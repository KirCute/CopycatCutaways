package top.kircute.copycatcutaways.client.model;

import com.copycatsplus.copycats.foundation.copycat.model.assembly.neoforge.CopycatRenderContextNeoForge.CopycatBakedQuad;
import com.copycatsplus.copycats.foundation.copycat.multistate.MultiStateTextureAtlasSprite;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import top.kircute.copycatcutaways.geometry.CutPlane;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

public final class CutQuadClipper {

    private static final double EPSILON = 1.0E-7;

    private static final ThreadLocal<GeometryCache> GEOMETRY_CACHE =
            ThreadLocal.withInitial(GeometryCache::new);

    private CutQuadClipper() {
    }

    static void clipAndEmit(CopycatBakedQuad source, CutPlane plane,
                            List<CopycatBakedQuad> destination, String property) {

        List<Vertex> polygon = new ArrayList<>(8);

        boolean entirelyInside = true;

        for (int i = 0; i < 4; i++) {

            Vertex vertex = Vertex.from(source, i);
            polygon.add(vertex);

            if (plane.signedDistance(vertex.position) < -EPSILON) {
                entirelyInside = false;
            }
        }

        if (entirelyInside) {
            destination.add(source);
            return;
        }

        polygon = clip(polygon, plane);

        if (polygon.size() < 3) {
            return;
        }

        if (polygon.size() == 4) {
            destination.add(createQuad(
                    polygon,
                    source,
                    source.getDirection(),
                    null,
                    source.hasAmbientOcclusion(),
                    property
            ));
            return;
        }

        for (int i = 1; i + 1 < polygon.size(); i++) {
            destination.add(createQuad(
                    List.of(polygon.get(0), polygon.get(i), polygon.get(i + 1)),
                    source,

                    source.getDirection(),

                    null,
                    source.hasAmbientOcclusion(),
                    property
            ));
        }
    }

    static void emitCap(CutPlane plane, List<CopycatBakedQuad> sources,
                        List<CopycatBakedQuad> destination, String property) {

        Geometry geometry = geometry(plane);

        if (geometry.cap().size() < 3) {
            return;
        }

        Direction surfaceFace = geometry.surfaceFace();

        List<CopycatBakedQuad> layers = capLayers(sources, surfaceFace);

        if (layers.isEmpty()) {
            return;
        }

        Vec3 capNormal = plane.normal().scale(-1.0);

        Bounds2D bounds = bounds(geometry.cap(), surfaceFace);

        if (bounds.side() < EPSILON) {
            return;
        }

        for (CopycatBakedQuad textureSource : layers) {

            SourceUvTransform sourceUv = SourceUvTransform.from(textureSource, surfaceFace);

            List<Vertex> cap = new ArrayList<>(geometry.cap().size());
            for (Vec3 point : geometry.cap()) {

                FaceUv faceUv = faceUv(point, surfaceFace);

                Vertex template = Vertex.from(textureSource, 0);

                float[] uv = bounds.textureUv(faceUv);

                float[] sourceUvValue = sourceUv.apply(uv[0], uv[1]);
                cap.add(new Vertex(

                        point,

                        sourceUvValue[0],
                        sourceUvValue[1],

                        template.color,
                        template.light,
                        template.normal
                ));
            }

            double sourceWinding = sourceWindingSign(textureSource, capNormal);

            cap = orientPolygon(cap, capNormal, sourceWinding);

            emitPolygon(cap, textureSource, surfaceFace, capNormal, false, property, destination);
        }
    }

    public static List<Vec3> capPolygon(CutPlane plane) {
        return geometry(plane).cap();
    }

    private static Geometry geometry(CutPlane plane) {

        GeometryCache cache = GEOMETRY_CACHE.get();

        if (!plane.equals(cache.plane)) {

            cache.plane = plane;
            cache.geometry = new Geometry(plane.visibleMaterialFace(), calculateCapPolygon(plane));
        }

        return cache.geometry;
    }

    private static List<CopycatBakedQuad> capLayers(List<CopycatBakedQuad> sources,
                                                    Direction surfaceFace) {

        LinkedHashMap<CapLayer, CopycatBakedQuad> layers = new LinkedHashMap<>();

        for (CopycatBakedQuad source : sources) {

            if (source.getDirection() != surfaceFace) {
                continue;
            }

            CapLayer key = new CapLayer(source.getSprite(), source.getTintIndex(), source.isShade());
            layers.putIfAbsent(key, source);
        }

        return List.copyOf(layers.values());
    }

    private static Bounds2D bounds(List<Vec3> cap, Direction face) {

        double minU = Double.POSITIVE_INFINITY;
        double minV = Double.POSITIVE_INFINITY;
        double maxU = Double.NEGATIVE_INFINITY;
        double maxV = Double.NEGATIVE_INFINITY;

        for (Vec3 point : cap) {
            FaceUv uv = faceUv(point, face);

            minU = Math.min(minU, uv.u());
            minV = Math.min(minV, uv.v());
            maxU = Math.max(maxU, uv.u());
            maxV = Math.max(maxV, uv.v());
        }

        return new Bounds2D(minU, minV, maxU, maxV);
    }

    private static FaceUv faceUv(Vec3 point, Direction face) {
        return switch (face) {

            case UP -> new FaceUv(point.x, 1.0 - point.z);

            case DOWN -> new FaceUv(point.x, point.z);

            case NORTH -> new FaceUv(1.0 - point.x, 1.0 - point.y);

            case SOUTH -> new FaceUv(point.x, 1.0 - point.y);

            case EAST -> new FaceUv(point.z, 1.0 - point.y);

            case WEST -> new FaceUv(1.0 - point.z, 1.0 - point.y);
        };
    }

    private static double sourceWindingSign(CopycatBakedQuad source, Vec3 capNormal) {

        Vec3 expectedNormal = source.getDirection() == null
                ? capNormal
                : Vec3.atLowerCornerOf(source.getDirection().getNormal());

        Vec3 first = BakedQuadHelper.getXYZ(source.getVertices(), 1)
                .subtract(BakedQuadHelper.getXYZ(source.getVertices(), 0));
        Vec3 second = BakedQuadHelper.getXYZ(source.getVertices(), 2)
                .subtract(BakedQuadHelper.getXYZ(source.getVertices(), 0));

        double signed = first.cross(second).dot(expectedNormal);

        return signed < 0.0 ? -1.0 : 1.0;
    }

    private static List<Vertex> orientPolygon(List<Vertex> polygon, Vec3 normal,
                                              double expectedSign) {

        if (polygon.size() < 3) {
            return polygon;
        }

        Vec3 first = polygon.get(1).position.subtract(polygon.get(0).position);
        Vec3 second = polygon.get(2).position.subtract(polygon.get(0).position);
        double signed = first.cross(second).dot(normal);

        if (Math.abs(signed) > EPSILON && Math.signum(signed) != expectedSign) {
            List<Vertex> reversed = new ArrayList<>(polygon);
            java.util.Collections.reverse(reversed);
            return reversed;
        }
        return polygon;
    }

    private static List<Vertex> clip(List<Vertex> input, CutPlane plane) {

        List<Vertex> output = new ArrayList<>();

        for (int i = 0; i < input.size(); i++) {
            Vertex current = input.get(i);
            Vertex next = input.get((i + 1) % input.size());

            double currentDistance = plane.signedDistance(current.position);
            double nextDistance = plane.signedDistance(next.position);
            boolean currentInside = currentDistance >= -EPSILON;
            boolean nextInside = nextDistance >= -EPSILON;

            if (currentInside) {
                output.add(current);
            }

            if (currentInside != nextInside) {
                output.add(Vertex.lerp(current, next,
                        currentDistance / (currentDistance - nextDistance)));
            }
        }

        return removeAdjacentDuplicates(output);
    }

    private static List<Vec3> calculateCapPolygon(CutPlane plane) {

        Vec3[][] edges = cubeEdges();

        List<Vec3> points = new ArrayList<>(6);

        for (Vec3[] edge : edges) {

            double first = plane.signedDistance(edge[0]);
            double second = plane.signedDistance(edge[1]);

            if (Math.abs(first) < EPSILON) {
                addUnique(points, edge[0]);
            }
            if (Math.abs(second) < EPSILON) {
                addUnique(points, edge[1]);
            }

            if ((first < -EPSILON && second > EPSILON)
                    || (first > EPSILON && second < -EPSILON)) {
                addUnique(points, edge[0].lerp(edge[1], first / (first - second)));
            }
        }

        if (points.size() < 3) {
            return List.of();
        }

        Vec3 center = points.stream().reduce(Vec3.ZERO, Vec3::add).scale(1.0 / points.size());

        Vec3 normal = plane.normal();

        Vec3 helper = Math.abs(normal.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = normal.cross(helper).normalize();
        Vec3 v = normal.cross(u);

        points.sort(Comparator.comparingDouble(point -> {
            Vec3 offset = point.subtract(center);
            return Math.atan2(offset.dot(v), offset.dot(u));
        }));

        Vec3 firstEdge = points.get(1).subtract(points.get(0));
        Vec3 secondEdge = points.get(2).subtract(points.get(0));
        if (firstEdge.cross(secondEdge).dot(normal) > 0.0) {

            List<Vec3> reversed = new ArrayList<>(points);
            java.util.Collections.reverse(reversed);
            points = reversed;
        }

        return List.copyOf(points);
    }

    private static Vec3[][] cubeEdges() {
        return new Vec3[][]{

                {new Vec3(0, 0, 0), new Vec3(1, 0, 0)},
                {new Vec3(0, 0, 1), new Vec3(1, 0, 1)},
                {new Vec3(0, 1, 0), new Vec3(1, 1, 0)},
                {new Vec3(0, 1, 1), new Vec3(1, 1, 1)},

                {new Vec3(0, 0, 0), new Vec3(0, 1, 0)},
                {new Vec3(1, 0, 0), new Vec3(1, 1, 0)},
                {new Vec3(0, 0, 1), new Vec3(0, 1, 1)},
                {new Vec3(1, 0, 1), new Vec3(1, 1, 1)},

                {new Vec3(0, 0, 0), new Vec3(0, 0, 1)},
                {new Vec3(1, 0, 0), new Vec3(1, 0, 1)},
                {new Vec3(0, 1, 0), new Vec3(0, 1, 1)},
                {new Vec3(1, 1, 0), new Vec3(1, 1, 1)}
        };
    }

    private static void emitPolygon(List<Vertex> polygon, CopycatBakedQuad source,
                                    Direction lightFace, Vec3 normal, boolean ambientOcclusion,
                                    String property,
                                    List<CopycatBakedQuad> destination) {

        if (polygon.size() == 4) {
            destination.add(createQuad(
                    polygon,
                    source,
                    lightFace,
                    normal,
                    ambientOcclusion,
                    property
            ));
            return;
        }

        for (int i = 1; i + 1 < polygon.size(); i++) {
            destination.add(createQuad(
                    List.of(polygon.get(0), polygon.get(i), polygon.get(i + 1)),
                    source,
                    lightFace,
                    normal,
                    ambientOcclusion,
                    property
            ));
        }
    }

    private static CopycatBakedQuad createQuad(List<Vertex> polygon,
                                               CopycatBakedQuad source,
                                               Direction lightFace,
                                               Vec3 normal,
                                               boolean ambientOcclusion,
                                               String property) {

        int[] data = new int[4 * BakedQuadHelper.VERTEX_STRIDE];
        Arrays.fill(data, -1);

        for (int i = 0; i < 4; i++) {

            Vertex vertex = polygon.get(Math.min(i, polygon.size() - 1));

            BakedQuadHelper.setXYZ(data, i, vertex.position);
            BakedQuadHelper.setU(data, i, vertex.u);
            BakedQuadHelper.setV(data, i, vertex.v);

            data[i * BakedQuadHelper.VERTEX_STRIDE + BakedQuadHelper.COLOR_OFFSET] = vertex.color;
            data[i * BakedQuadHelper.VERTEX_STRIDE + BakedQuadHelper.LIGHT_OFFSET] = vertex.light;

            if (normal == null) {
                data[i * BakedQuadHelper.VERTEX_STRIDE + BakedQuadHelper.NORMAL_OFFSET] = vertex.normal;
            } else {
                BakedQuadHelper.setNormalXYZ(data, i, normal);
            }
        }

        return new CutCopycatBakedQuad(
                data,
                source.getTintIndex(),
                lightFace,
                source.getSprite(),
                source.isShade(),
                null,
                property,
                ambientOcclusion
        );
    }

    private static final class CutCopycatBakedQuad extends CopycatBakedQuad {

        private final boolean ambientOcclusion;

        private CutCopycatBakedQuad(int[] vertices, int tintIndex, Direction direction,
                                    TextureAtlasSprite sprite, boolean shade,
                                    @Nullable Direction cullFace, String property,
                                    boolean ambientOcclusion) {
            super(vertices, tintIndex, direction, sprite, shade, cullFace, property);
            this.ambientOcclusion = ambientOcclusion;
        }

        @Override
        public BakedQuad toBakedQuad() {
            return new BakedQuad(
                    getVertices(),
                    getTintIndex(),
                    getDirection(),
                    new MultiStateTextureAtlasSprite(property, getSprite()),
                    isShade(),
                    ambientOcclusion
            );
        }
    }

    private static void addUnique(List<Vec3> points, Vec3 point) {
        if (points.stream().noneMatch(existing ->
                existing.distanceToSqr(point) < EPSILON * EPSILON)) {
            points.add(point);
        }
    }

    private static List<Vertex> removeAdjacentDuplicates(List<Vertex> vertices) {

        List<Vertex> result = new ArrayList<>(vertices.size());

        for (Vertex vertex : vertices) {
            if (result.isEmpty()
                    || result.get(result.size() - 1).position.distanceToSqr(vertex.position)
                    > EPSILON * EPSILON) {
                result.add(vertex);
            }
        }

        if (result.size() > 1
                && result.get(0).position.distanceToSqr(result.get(result.size() - 1).position)
                < EPSILON * EPSILON) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    record Geometry(Direction surfaceFace, List<Vec3> cap) {
    }

    private static final class GeometryCache {

        private CutPlane plane;

        private Geometry geometry;
    }

    private record CapLayer(TextureAtlasSprite sprite, int tintIndex, boolean shade) {
    }

    private record SourceUvTransform(double uu, double uv, double uOffset,
                                     double vu, double vv, double vOffset) {

        private static SourceUvTransform from(CopycatBakedQuad source, Direction face) {

            Vec3[] positions = new Vec3[4];
            float[] sourceU = new float[4];
            float[] sourceV = new float[4];
            for (int index = 0; index < 4; index++) {
                positions[index] = BakedQuadHelper.getXYZ(source.getVertices(), index);
                sourceU[index] = BakedQuadHelper.getU(source.getVertices(), index);
                sourceV[index] = BakedQuadHelper.getV(source.getVertices(), index);
            }

            for (int a = 0; a < 4; a++) {
                for (int b = a + 1; b < 4; b++) {
                    for (int c = b + 1; c < 4; c++) {
                        FaceUv first = faceUv(positions[a], face);
                        FaceUv second = faceUv(positions[b], face);
                        FaceUv third = faceUv(positions[c], face);
                        double du1 = second.u() - first.u();
                        double dv1 = second.v() - first.v();
                        double du2 = third.u() - first.u();
                        double dv2 = third.v() - first.v();
                        double determinant = du1 * dv2 - du2 * dv1;
                        if (Math.abs(determinant) <= EPSILON) {
                            continue;
                        }

                        double u1 = sourceU[b] - sourceU[a];
                        double u2 = sourceU[c] - sourceU[a];
                        double v1 = sourceV[b] - sourceV[a];
                        double v2 = sourceV[c] - sourceV[a];
                        double uu = (u1 * dv2 - u2 * dv1) / determinant;
                        double uv = (du1 * u2 - du2 * u1) / determinant;
                        double vu = (v1 * dv2 - v2 * dv1) / determinant;
                        double vv = (du1 * v2 - du2 * v1) / determinant;
                        return new SourceUvTransform(
                                uu,
                                uv,
                                sourceU[a] - uu * first.u() - uv * first.v(),
                                vu,
                                vv,
                                sourceV[a] - vu * first.u() - vv * first.v()
                        );
                    }
                }
            }

            TextureAtlasSprite sprite = source.getSprite();
            float minU = sprite.getU(0.0F);
            float maxU = sprite.getU(1.0F);
            float minV = sprite.getV(0.0F);
            float maxV = sprite.getV(1.0F);
            return new SourceUvTransform(
                    maxU - minU,
                    0,
                    minU,
                    0,
                    maxV - minV,
                    minV
            );
        }

        private float[] apply(float localU, float localV) {
            return new float[]{
                    (float) (uu * localU + uv * localV + uOffset),
                    (float) (vu * localU + vv * localV + vOffset)
            };
        }
    }

    private record FaceUv(double u, double v) {
    }

    private record Bounds2D(double minU, double minV, double maxU, double maxV) {

        private double side() {
            return Math.max(maxU - minU, maxV - minV);
        }

        private float[] textureUv(FaceUv point) {

            double side = side();

            if (side >= 1.0) {
                return new float[]{
                        (float) ((point.u - minU) / side),
                        (float) ((point.v - minV) / side)
                };
            }

            double centerU = (minU + maxU) * 0.5;
            double centerV = (minV + maxV) * 0.5;
            return new float[]{
                    (float) (0.5 + point.u - centerU),
                    (float) (0.5 + point.v - centerV)
            };
        }
    }

    private record Vertex(Vec3 position, float u, float v, int color, int light, int normal) {

        private static Vertex from(CopycatBakedQuad quad, int index) {

            int[] data = quad.getVertices();
            int offset = index * BakedQuadHelper.VERTEX_STRIDE;
            return new Vertex(

                    BakedQuadHelper.getXYZ(data, index),
                    BakedQuadHelper.getU(data, index),
                    BakedQuadHelper.getV(data, index),

                    data[offset + BakedQuadHelper.COLOR_OFFSET],
                    data[offset + BakedQuadHelper.LIGHT_OFFSET],
                    data[offset + BakedQuadHelper.NORMAL_OFFSET]
            );
        }

        private static Vertex lerp(Vertex first, Vertex second, double t) {
            return new Vertex(

                    first.position.lerp(second.position, t),

                    (float) (first.u + (second.u - first.u) * t),
                    (float) (first.v + (second.v - first.v) * t),

                    first.color,
                    first.light,
                    first.normal
            );
        }
    }
}
