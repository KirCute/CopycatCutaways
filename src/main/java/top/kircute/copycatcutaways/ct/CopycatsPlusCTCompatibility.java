package top.kircute.copycatcutaways.ct;

import com.copycatsplus.copycats.CCBlocks;
import com.copycatsplus.copycats.content.copycat.slope.CopycatSlopeBlock;
import com.copycatsplus.copycats.content.copycat.slope_layer.CopycatSlopeLayerBlock;
import com.copycatsplus.copycats.content.copycat.vertical_slope.CopycatVerticalSlopeBlock;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlock;
import com.simibubi.create.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import top.kircute.copycatcutaways.api.CopycatCTCompatibility;
import top.kircute.copycatcutaways.block.CutawayCopycatBlock;
import top.kircute.copycatcutaways.block.CutawayDataAccess;
import top.kircute.copycatcutaways.block.CutawaySnapshot;
import top.kircute.copycatcutaways.geometry.CutOutline;
import top.kircute.copycatcutaways.geometry.CutPlane;

import java.util.ArrayList;
import java.util.List;

public final class CopycatsPlusCTCompatibility {

    private static final double EPSILON = 1.0E-6;

    private static final Vec3[][] CUBE_FACES = {
            {new Vec3(0, 0, 0), new Vec3(1, 0, 0), new Vec3(1, 0, 1), new Vec3(0, 0, 1)},
            {new Vec3(0, 1, 0), new Vec3(0, 1, 1), new Vec3(1, 1, 1), new Vec3(1, 1, 0)},
            {new Vec3(0, 0, 0), new Vec3(0, 1, 0), new Vec3(1, 1, 0), new Vec3(1, 0, 0)},
            {new Vec3(0, 0, 1), new Vec3(1, 0, 1), new Vec3(1, 1, 1), new Vec3(0, 1, 1)},
            {new Vec3(0, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 1), new Vec3(0, 1, 0)},
            {new Vec3(1, 0, 0), new Vec3(1, 1, 0), new Vec3(1, 1, 1), new Vec3(1, 0, 1)}
    };

    private static final Vec3[] CUBE_NORMALS = {
            new Vec3(0, -1, 0), new Vec3(0, 1, 0),
            new Vec3(0, 0, -1), new Vec3(0, 0, 1),
            new Vec3(-1, 0, 0), new Vec3(1, 0, 0)
    };

    private static boolean registered;

    private CopycatsPlusCTCompatibility() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        register(CCBlocks.COPYCAT_BLOCK.get());
        register(CCBlocks.COPYCAT_GHOST_BLOCK.get());
        register(CCBlocks.COPYCAT_SLOPE.get());
        register(CCBlocks.COPYCAT_VERTICAL_SLOPE.get());
        register(CCBlocks.COPYCAT_SLOPE_LAYER.get());
    }

    private static void register(Block block) {
        CopycatCTCompatibility.REGISTRY.registerAppearance(
                block,
                CopycatsPlusCTCompatibility::resolveAppearance
        );
    }

    public static boolean isRegistered(Block block) {
        return CopycatCTCompatibility.REGISTRY.hasAppearanceResolver(block);
    }

    public static boolean directConnection(BlockAndTintGetter level,
                                           BlockPos firstPos,
                                           BlockPos secondPos,
                                           Direction textureFace) {
        BlockState firstState = CutawayDataAccess.realState(level, firstPos);
        BlockState secondState = CutawayDataAccess.realState(level, secondPos);
        boolean firstCut = firstState.getBlock() instanceof CutawayCopycatBlock;
        boolean secondCut = secondState.getBlock() instanceof CutawayCopycatBlock;

        if (firstCut && isRegistered(secondState.getBlock())) {
            CutawaySnapshot snapshot = CutawayDataAccess.snapshot(level, firstPos);
            if (snapshot == null) {
                return false;
            }
            return resolveAppearance(new CopycatCTCompatibility.ConnectionContext(
                    level,
                    firstState,
                    firstPos,
                    snapshot.plane(),
                    snapshot.material(),
                    secondState,
                    secondPos,
                    textureFace
            )) != null;
        }
        if (secondCut && isRegistered(firstState.getBlock())) {
            CutawaySnapshot snapshot = CutawayDataAccess.snapshot(level, secondPos);
            if (snapshot == null) {
                return false;
            }
            return resolveAppearance(new CopycatCTCompatibility.ConnectionContext(
                    level,
                    secondState,
                    secondPos,
                    snapshot.plane(),
                    snapshot.material(),
                    firstState,
                    firstPos,
                    textureFace
            )) != null;
        }

        if (!isRegistered(firstState.getBlock()) && !isRegistered(secondState.getBlock())) {
            return false;
        }
        if (!materialsCompatible(level, firstPos, secondPos)) {
            return false;
        }

        Vec3 offset = new Vec3(
                secondPos.getX() - firstPos.getX(),
                secondPos.getY() - firstPos.getY(),
                secondPos.getZ() - firstPos.getZ()
        );
        List<Surface> firstSurfaces = surfacesForState(level, firstPos, firstState);
        List<Surface> secondSurfaces = surfacesForState(level, secondPos, secondState);
        if (firstSurfaces == null || secondSurfaces == null) {
            return false;
        }
        for (Surface first : firstSurfaces) {
            for (Surface second : secondSurfaces) {
                if (first.normal().dot(second.normal()) > EPSILON
                        && sharesWholeEdge(first.vertices(), second.vertices(), offset)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    private static BlockState resolveAppearance(
            CopycatCTCompatibility.ConnectionContext context) {

        if (!(context.otherState().getBlock() instanceof ICopycatBlock copycat)) {
            return null;
        }

        Vec3 offset = new Vec3(
                context.otherPos().getX() - context.cutawayPos().getX(),
                context.otherPos().getY() - context.cutawayPos().getY(),
                context.otherPos().getZ() - context.cutawayPos().getZ()
        );
        int distance = Math.abs((int) offset.x)
                + Math.abs((int) offset.y)
                + Math.abs((int) offset.z);
        if (distance != 1) {
            return null;
        }

        BlockState currentMaterial = context.cutawayMaterial();
        BlockState otherMaterial = CutawayDataAccess.material(
                context.level(),
                context.otherPos()
        );
        if (otherMaterial == null
                || currentMaterial.getBlock() == AllBlocks.COPYCAT_BASE.get()
                || otherMaterial.getBlock() == AllBlocks.COPYCAT_BASE.get()
                || currentMaterial.getBlock() != otherMaterial.getBlock()) {
            return null;
        }

        if (!copycat.isCTEnabled(
                context.otherState(),
                context.level(),
                context.otherPos()
        )) {
            return null;
        }

        List<Surface> ownSurfaces = cutSurfaces(
                context.plane(),
                context.textureFace()
        );
        List<Surface> otherSurfaces = targetSurfaces(context.otherState());

        for (Surface own : ownSurfaces) {
            for (Surface other : otherSurfaces) {
                if (own.normal().dot(other.normal()) > EPSILON
                        && sharesWholeEdge(own.vertices(), other.vertices(), offset)) {
                    return currentMaterial;
                }
            }
        }
        return null;
    }

    private static List<Surface> cutSurfaces(CutPlane plane,
                                             @Nullable Direction textureFace) {
        List<Surface> result = new ArrayList<>(7);

        for (int index = 0; index < CUBE_FACES.length; index++) {
            Direction face = Direction.from3DDataValue(index);
            if (textureFace != null && face != textureFace) {
                continue;
            }
            List<Vec3> polygon = clip(List.of(CUBE_FACES[index]), plane);
            if (polygon.size() >= 3) {
                result.add(new Surface(CUBE_NORMALS[index], polygon));
            }
        }

        if (plane.capCTFace() != null) {
            List<Vec3> cap = CutOutline.capPolygon(plane);
            if (cap.size() >= 3) {
                result.add(new Surface(plane.visibleNormal(), cap));
            }
        }
        return result;
    }

    private static List<Surface> targetSurfaces(BlockState state) {
        Block block = state.getBlock();

        if (block == CCBlocks.COPYCAT_SLOPE.get()) {
            return transformWedge(
                    wedgeSurfaces(0.0, 1.0),
                    state.getValue(CopycatSlopeBlock.FACING),
                    state.getValue(CopycatSlopeBlock.HALF),
                    false
            );
        }
        if (block == CCBlocks.COPYCAT_VERTICAL_SLOPE.get()) {
            return transformWedge(
                    wedgeSurfaces(0.0, 1.0),
                    state.getValue(CopycatVerticalSlopeBlock.FACING),
                    Half.BOTTOM,
                    true
            );
        }
        if (block == CCBlocks.COPYCAT_SLOPE_LAYER.get()) {
            int layers = state.getValue(CopycatSlopeLayerBlock.LAYERS);
            double minHeight = layers <= 4 ? 0.0 : (layers - 4) / 4.0;
            double maxHeight = layers <= 4 ? layers / 4.0 : 1.0;
            return transformWedge(
                    wedgeSurfaces(minHeight, maxHeight),
                    state.getValue(CopycatSlopeLayerBlock.FACING),
                    state.getValue(CopycatSlopeLayerBlock.HALF),
                    false
            );
        }

        return cubeSurfaces();
    }

    @Nullable
    private static List<Surface> surfacesForState(BlockAndTintGetter level,
                                                  BlockPos pos,
                                                  BlockState state) {
        if (isRegistered(state.getBlock())) {
            return targetSurfaces(state);
        }

        // A multi-state block has no single safe "whole block" geometry without
        // the concrete part property. Defer to the owning mod instead of guessing.
        if (state.getBlock() instanceof IMultiStateCopycatBlock) {
            return null;
        }

        List<Surface> result = new ArrayList<>(6);
        for (int index = 0; index < CUBE_FACES.length; index++) {
            Direction face = Direction.from3DDataValue(index);
            if (!Block.isFaceFull(
                    state.getFaceOcclusionShape(level, pos, face),
                    face
            )) {
                continue;
            }
            result.add(new Surface(CUBE_NORMALS[index], List.of(CUBE_FACES[index])));
        }
        return List.copyOf(result);
    }

    private static boolean materialsCompatible(BlockAndTintGetter level,
                                               BlockPos firstPos,
                                               BlockPos secondPos) {
        BlockState firstMaterial = CutawayDataAccess.material(level, firstPos);
        BlockState secondMaterial = CutawayDataAccess.material(level, secondPos);
        return firstMaterial != null
                && secondMaterial != null
                && firstMaterial.getBlock() != AllBlocks.COPYCAT_BASE.get()
                && secondMaterial.getBlock() != AllBlocks.COPYCAT_BASE.get()
                && firstMaterial.getBlock() == secondMaterial.getBlock();
    }

    private static List<Surface> cubeSurfaces() {
        List<Surface> result = new ArrayList<>(6);
        for (int index = 0; index < CUBE_FACES.length; index++) {
            result.add(new Surface(CUBE_NORMALS[index], List.of(CUBE_FACES[index])));
        }
        return result;
    }

    private static List<Surface> wedgeSurfaces(double minHeight, double maxHeight) {
        List<Surface> result = new ArrayList<>(6);

        result.add(new Surface(new Vec3(0, -1, 0), List.of(
                point(0, 0, 0), point(1, 0, 0),
                point(1, 0, 1), point(0, 0, 1)
        )));

        result.add(new Surface(new Vec3(0, 0, 1), List.of(
                point(0, 0, 1), point(1, 0, 1),
                point(1, maxHeight, 1), point(0, maxHeight, 1)
        )));

        if (minHeight <= EPSILON) {

            result.add(new Surface(
                    new Vec3(0, 1, -maxHeight).normalize(),
                    List.of(
                    point(0, 0, 0), point(1, 0, 0),
                    point(1, maxHeight, 1), point(0, maxHeight, 1)
                    )
            ));

            result.add(new Surface(new Vec3(-1, 0, 0), List.of(
                    point(0, 0, 0), point(0, maxHeight, 1), point(0, 0, 1)
            )));
            result.add(new Surface(new Vec3(1, 0, 0), List.of(
                    point(1, 0, 0), point(1, 0, 1), point(1, maxHeight, 1)
            )));
            return result;
        }

        result.add(new Surface(new Vec3(0, 0, -1), List.of(
                point(0, 0, 0), point(1, 0, 0),
                point(1, minHeight, 0), point(0, minHeight, 0)
        )));

        result.add(new Surface(
                new Vec3(0, 1, minHeight - maxHeight).normalize(),
                List.of(
                point(0, minHeight, 0), point(1, minHeight, 0),
                point(1, maxHeight, 1), point(0, maxHeight, 1)
                )
        ));

        result.add(new Surface(new Vec3(-1, 0, 0), List.of(
                point(0, 0, 0), point(0, 0, 1),
                point(0, maxHeight, 1), point(0, minHeight, 0)
        )));
        result.add(new Surface(new Vec3(1, 0, 0), List.of(
                point(1, 0, 0), point(1, minHeight, 0),
                point(1, maxHeight, 1), point(1, 0, 1)
        )));
        return result;
    }

    private static List<Surface> transformWedge(List<Surface> surfaces,
                                                Direction facing,
                                                Half half,
                                                boolean vertical) {
        int yRotation = (int) facing.toYRot();
        boolean flipY = half == Half.TOP;
        return surfaces.stream()
                .map(surface -> {
                    Vec3 transformedNormal = vertical
                            ? rotateZNormal(surface.normal(), -90)
                            : surface.normal();
                    if (flipY) {
                        transformedNormal = point(
                                transformedNormal.x,
                                -transformedNormal.y,
                                transformedNormal.z
                        );
                    }
                    transformedNormal = rotateYNormal(transformedNormal, yRotation);

                    List<Vec3> vertices = surface.vertices().stream()
                            .map(vertex -> {
                                Vec3 transformed = vertical ? rotateZ(vertex, -90) : vertex;
                                if (flipY) {
                                    transformed = point(
                                            transformed.x,
                                            1.0 - transformed.y,
                                            transformed.z
                                    );
                                }
                                return rotateY(transformed, yRotation);
                            })
                            .toList();
                    return new Surface(transformedNormal, vertices);
                })
                .toList();
    }

    private static Vec3 rotateY(Vec3 point, int angle) {
        int normalized = ((angle % 360) + 360) % 360;
        return switch (normalized) {
            case 90 -> point(1.0 - point.z, point.y, point.x);
            case 180 -> point(1.0 - point.x, point.y, 1.0 - point.z);
            case 270 -> point(point.z, point.y, 1.0 - point.x);
            default -> point;
        };
    }

    private static Vec3 rotateZ(Vec3 point, int angle) {
        int normalized = ((angle % 360) + 360) % 360;
        return switch (normalized) {
            case 90 -> point(point.y, 1.0 - point.x, point.z);
            case 180 -> point(1.0 - point.x, 1.0 - point.y, point.z);
            case 270 -> point(1.0 - point.y, point.x, point.z);
            default -> point;
        };
    }

    private static Vec3 rotateYNormal(Vec3 normal, int angle) {
        int normalized = ((angle % 360) + 360) % 360;
        return switch (normalized) {
            case 90 -> point(-normal.z, normal.y, normal.x);
            case 180 -> point(-normal.x, normal.y, -normal.z);
            case 270 -> point(normal.z, normal.y, -normal.x);
            default -> normal;
        };
    }

    private static Vec3 rotateZNormal(Vec3 normal, int angle) {
        int normalized = ((angle % 360) + 360) % 360;
        return switch (normalized) {
            case 90 -> point(normal.y, -normal.x, normal.z);
            case 180 -> point(-normal.x, -normal.y, normal.z);
            case 270 -> point(-normal.y, normal.x, normal.z);
            default -> normal;
        };
    }

    private static List<Vec3> clip(List<Vec3> input, CutPlane plane) {
        List<Vec3> output = new ArrayList<>(6);
        for (int i = 0; i < input.size(); i++) {
            Vec3 current = input.get(i);
            Vec3 next = input.get((i + 1) % input.size());
            double currentDistance = plane.signedDistance(current);
            double nextDistance = plane.signedDistance(next);
            boolean currentInside = currentDistance >= -EPSILON;
            boolean nextInside = nextDistance >= -EPSILON;
            if (currentInside) {
                output.add(current);
            }
            if (currentInside != nextInside) {
                output.add(current.lerp(
                        next,
                        currentDistance / (currentDistance - nextDistance)
                ));
            }
        }
        return removeAdjacentDuplicates(output);
    }

    private static List<Vec3> removeAdjacentDuplicates(List<Vec3> input) {
        List<Vec3> output = new ArrayList<>(input.size());
        for (Vec3 point : input) {
            if (output.isEmpty() || !samePoint(output.get(output.size() - 1), point)) {
                output.add(point);
            }
        }
        if (output.size() > 1 && samePoint(output.get(0), output.get(output.size() - 1))) {
            output.remove(output.size() - 1);
        }
        return output;
    }

    private static boolean sharesWholeEdge(List<Vec3> first,
                                           List<Vec3> second,
                                           Vec3 offset) {
        List<Vec3> translated = second.stream().map(point -> point.add(offset)).toList();
        for (int i = 0; i < first.size(); i++) {
            Vec3 a = first.get(i);
            Vec3 b = first.get((i + 1) % first.size());
            if (samePoint(a, b) || Math.abs(b.subtract(a).dot(offset)) > EPSILON) {
                continue;
            }
            for (int j = 0; j < translated.size(); j++) {
                Vec3 c = translated.get(j);
                Vec3 d = translated.get((j + 1) % translated.size());
                if (samePoint(a, c) && samePoint(b, d)
                        || samePoint(a, d) && samePoint(b, c)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Vec3 point(double x, double y, double z) {
        return new Vec3(x, y, z);
    }

    private static boolean samePoint(Vec3 first, Vec3 second) {
        return first.distanceToSqr(second) <= EPSILON * EPSILON;
    }

    private record Surface(Vec3 normal, List<Vec3> vertices) {
        private Surface {
            vertices = List.copyOf(vertices);
        }
    }
}
