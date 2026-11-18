package top.kircute.copycatcutaways.geometry;

import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public record CutPlane(double nx, double ny, double nz, double offset) {

    private static final double EPSILON = 1.0E-7;

    public enum Result {

        VALID,

        INVALID_GEOMETRY,

        AMBIGUOUS_SIDE
    }

    public record Evaluation(CutPlane plane, Result result) {

        public boolean isValid() {
            return result == Result.VALID;
        }
    }

    public static Evaluation evaluate(List<GridPoint> selected, Vec3 fourthPoint) {

        if (selected.size() != 3) {
            return new Evaluation(null, Result.INVALID_GEOMETRY);
        }

        Vec3 a = selected.get(0).center();
        Vec3 b = selected.get(1).center();
        Vec3 c = selected.get(2).center();

        Vec3 normal = b.subtract(a).cross(c.subtract(a));

        double length = normal.length();
        if (length < EPSILON) {
            return new Evaluation(null, Result.INVALID_GEOMETRY);
        }

        normal = normal.scale(1.0 / length);
        double offset = normal.dot(a);

        double side = normal.dot(fourthPoint) - offset;

        if (Math.abs(side) < EPSILON) {
            return new Evaluation(null, Result.AMBIGUOUS_SIDE);
        }

        if (side < 0.0) {
            normal = normal.scale(-1.0);
            offset = -offset;
        }

        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    double signed = signedDistance(new Vec3(x, y, z), normal, offset);
                    minimum = Math.min(minimum, signed);
                    maximum = Math.max(maximum, signed);
                }
            }
        }

        if (minimum >= -EPSILON || maximum <= EPSILON) {
            return new Evaluation(null, Result.INVALID_GEOMETRY);
        }

        return new Evaluation(new CutPlane(normal.x, normal.y, normal.z, offset), Result.VALID);
    }

    public static CutPlane forCollision(List<GridPoint> selected, CutPlane materialPlane) {

        if (selected.size() != 3 || materialPlane == null) {
            return null;
        }

        List<GridPoint> snapped = selected.stream().map(GridPoint::snapForCollision).toList();

        Vec3 a = snapped.get(0).center();
        Vec3 b = snapped.get(1).center();
        Vec3 c = snapped.get(2).center();
        Vec3 normal = b.subtract(a).cross(c.subtract(a));
        double length = normal.length();

        if (length < EPSILON) {
            return null;
        }

        normal = normal.scale(1.0 / length);
        if (normal.dot(materialPlane.normal()) < 0.0) {
            normal = normal.scale(-1.0);
        }

        double offset = normal.dot(a);
        CutPlane collisionPlane = new CutPlane(normal.x, normal.y, normal.z, offset);
        return collisionPlane.splitsCube() ? collisionPlane : null;
    }

    public static CutPlane fromNbt(CompoundTag tag) {
        return fromNbt(tag, "CutPlane");
    }

    public static CutPlane fromNbt(CompoundTag tag, String key) {

        if (!tag.contains(key)) {
            return null;
        }

        CompoundTag planeTag = tag.getCompound(key);
        CutPlane plane = new CutPlane(
                planeTag.getDouble("NX"),
                planeTag.getDouble("NY"),
                planeTag.getDouble("NZ"),
                planeTag.getDouble("Offset")
        );

        return plane.isFiniteAndNormalized() ? plane : null;
    }

    public void writeNbt(CompoundTag tag) {
        writeNbt(tag, "CutPlane");
    }

    public void writeNbt(CompoundTag tag, String key) {

        CompoundTag planeTag = new CompoundTag();

        planeTag.putDouble("NX", nx);
        planeTag.putDouble("NY", ny);
        planeTag.putDouble("NZ", nz);

        planeTag.putDouble("Offset", offset);

        tag.put(key, planeTag);
    }

    public Vec3 normal() {
        return new Vec3(nx, ny, nz);
    }

    public Vec3 visibleNormal() {
        return new Vec3(-nx, -ny, -nz);
    }

    public Direction visibleMaterialFace() {
        Direction replacement = capCTFace();
        if (replacement != null) {
            return replacement;
        }

        Vec3 visible = visibleNormal();

        double horizontalX = Math.abs(visible.x);
        double horizontalZ = Math.abs(visible.z);

        double diagonal = Math.sqrt(0.5);

        if (visible.y >= diagonal - EPSILON) {
            return Direction.UP;
        }

        if (visible.y <= -diagonal + EPSILON) {
            return Direction.DOWN;
        }

        if (horizontalX >= horizontalZ) {
            return visible.x >= 0.0 ? Direction.EAST : Direction.WEST;
        }

        return visible.z >= 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    public Direction capCTFace() {
        Direction best = null;
        double bestDot = Double.NEGATIVE_INFINITY;

        Direction[] priority = {
                Direction.UP, Direction.DOWN,
                Direction.EAST, Direction.WEST,
                Direction.NORTH, Direction.SOUTH
        };
        Vec3 visible = visibleNormal();
        for (Direction candidate : priority) {
            if (hasRetainedArea(candidate)) {
                continue;
            }
            double dot = visible.dot(Vec3.atLowerCornerOf(candidate.getNormal()));
            if (dot > bestDot + EPSILON) {
                bestDot = dot;
                best = candidate;
            }
        }
        return best;
    }

    public double signedDistance(Vec3 point) {
        return nx * point.x + ny * point.y + nz * point.z - offset;
    }

    public CutPlane transform(StructureTransform transform) {

        Vec3 origin = normal().scale(offset);

        Vec3 transformedOrigin = transform.applyWithoutOffset(origin);

        Vec3 transformedNormal = transform.applyWithoutOffset(origin.add(normal()))
                .subtract(transformedOrigin)
                .normalize();

        return new CutPlane(
                transformedNormal.x,
                transformedNormal.y,
                transformedNormal.z,
                transformedNormal.dot(transformedOrigin)
        );
    }

    private static double signedDistance(Vec3 point, Vec3 normal, double offset) {
        return normal.dot(point) - offset;
    }

    private boolean isFiniteAndNormalized() {

        if (!Double.isFinite(nx) || !Double.isFinite(ny) || !Double.isFinite(nz) || !Double.isFinite(offset)) {
            return false;
        }

        double lengthSqr = nx * nx + ny * ny + nz * nz;
        return Math.abs(lengthSqr - 1.0) < 1.0E-5;
    }

    private boolean splitsCube() {

        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;

        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {

                    double signed = signedDistance(new Vec3(x, y, z));
                    minimum = Math.min(minimum, signed);
                    maximum = Math.max(maximum, signed);
                }
            }
        }

        return minimum < -EPSILON && maximum > EPSILON;
    }

    private boolean hasRetainedArea(Direction face) {
        for (Vec3 corner : faceCorners(face)) {
            if (signedDistance(corner) > EPSILON) {
                return true;
            }
        }
        return false;
    }

    private static Vec3[] faceCorners(Direction face) {
        return switch (face) {
            case UP -> new Vec3[]{
                    new Vec3(0, 1, 0), new Vec3(1, 1, 0),
                    new Vec3(1, 1, 1), new Vec3(0, 1, 1)
            };
            case DOWN -> new Vec3[]{
                    new Vec3(0, 0, 0), new Vec3(0, 0, 1),
                    new Vec3(1, 0, 1), new Vec3(1, 0, 0)
            };
            case NORTH -> new Vec3[]{
                    new Vec3(0, 0, 0), new Vec3(1, 0, 0),
                    new Vec3(1, 1, 0), new Vec3(0, 1, 0)
            };
            case SOUTH -> new Vec3[]{
                    new Vec3(0, 0, 1), new Vec3(0, 1, 1),
                    new Vec3(1, 1, 1), new Vec3(1, 0, 1)
            };
            case WEST -> new Vec3[]{
                    new Vec3(0, 0, 0), new Vec3(0, 1, 0),
                    new Vec3(0, 1, 1), new Vec3(0, 0, 1)
            };
            case EAST -> new Vec3[]{
                    new Vec3(1, 0, 0), new Vec3(1, 0, 1),
                    new Vec3(1, 1, 1), new Vec3(1, 1, 0)
            };
        };
    }

}
