package top.kircute.copycatcutaways.ct;
import top.kircute.copycatcutaways.CopycatCutaways;
import top.kircute.copycatcutaways.block.CutawayDataAccess;
import top.kircute.copycatcutaways.block.CutawaySnapshot;
import top.kircute.copycatcutaways.geometry.CutOutline;
import top.kircute.copycatcutaways.geometry.CutPlane;

import com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CutCTEvaluator {

    private static final double EPSILON = 1.0E-6;

    private static final ThreadLocal<GeometryCache> CACHE =
            ThreadLocal.withInitial(GeometryCache::new);

    private static final Vec3[][] FACE_CORNERS = {
            {new Vec3(0, 0, 0), new Vec3(1, 0, 0), new Vec3(1, 0, 1), new Vec3(0, 0, 1)},
            {new Vec3(0, 1, 0), new Vec3(0, 1, 1), new Vec3(1, 1, 1), new Vec3(1, 1, 0)},
            {new Vec3(0, 0, 0), new Vec3(0, 1, 0), new Vec3(1, 1, 0), new Vec3(1, 0, 0)},
            {new Vec3(0, 0, 1), new Vec3(1, 0, 1), new Vec3(1, 1, 1), new Vec3(0, 1, 1)},
            {new Vec3(0, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 1), new Vec3(0, 1, 0)},
            {new Vec3(1, 0, 0), new Vec3(1, 1, 0), new Vec3(1, 1, 1), new Vec3(1, 0, 1)}
    };

    private CutCTEvaluator() {
    }

    public record DirectMask(boolean up, boolean down, boolean left, boolean right) {

        public static final DirectMask EMPTY = new DirectMask(false, false, false, false);
    }

    private enum CtSide {
        UP,
        DOWN,
        LEFT,
        RIGHT,
        NONE
    }

    public static boolean canConnect(BlockAndTintGetter level, BlockPos fromPos,
                                     BlockPos toPos, @Nullable Direction side) {
        try {
            GeometryCache cache = CACHE.get();
            CutawaySnapshot fromSnapshot = CutawayDataAccess.snapshot(level, fromPos);
            if (fromSnapshot == null) {
                return false;
            }

            BlockState toState = CutawayDataAccess.realState(level, toPos);
            if (!materialsCompatible(
                    fromSnapshot.material(),
                    CutawayDataAccess.material(level, toPos)
            )) {
                return false;
            }
            List<Surface> targetSurfaces = surfacesAt(level, toPos, toState);
            if (targetSurfaces.isEmpty()) {
                return false;
            }

            return connectSurfaces(
                    surfaces(fromSnapshot.plane(), cache),
                    targetSurfaces,
                    toPos.subtract(fromPos),
                    side
            );
        } catch (Throwable throwable) {
            CopycatCutaways.LOGGER.error(
                    "Failed to evaluate copycat CT connection from {} to {}",
                    fromPos,
                    toPos,
                    throwable
            );
            return false;
        }
    }

    public static boolean canAppearanceConnect(BlockAndTintGetter level,
                                               BlockPos queryPos,
                                               BlockPos cutawayPos,
                                               CutawaySnapshot cutaway,
                                               Direction side) {
        try {
            BlockState cutawayState = CutawayDataAccess.realState(level, cutawayPos);
            if (!materialsCompatible(
                    cutaway.material(),
                    CutawayDataAccess.material(level, queryPos)
            )) {
                return false;
            }
            Vec3i offset = queryPos.subtract(cutawayPos);
            int distance = Math.abs(offset.getX())
                    + Math.abs(offset.getY())
                    + Math.abs(offset.getZ());

            if (distance == 1) {
                DirectMask mask = directMask(level, cutawayPos, cutawayState, side);
                return switch (offsetSide(offset, side)) {
                    case UP -> mask.up();
                    case DOWN -> mask.down();
                    case LEFT -> mask.left();
                    case RIGHT -> mask.right();
                    case NONE -> false;
                };
            }

            if (distance == 2
                    && Math.abs(offset.getX()) <= 1
                    && Math.abs(offset.getY()) <= 1
                    && Math.abs(offset.getZ()) <= 1) {
                Direction horizontal = horizontalDirection(side);
                Direction vertical = verticalDirection(side);
                int horizontalStep = offset.get(horizontal.getAxis())
                        * horizontal.getAxisDirection().getStep();
                int verticalStep = offset.get(vertical.getAxis())
                        * vertical.getAxisDirection().getStep();
                if (Math.abs(horizontalStep) != 1 || Math.abs(verticalStep) != 1) {
                    return false;
                }
                BlockPos verticalPos = cutawayPos.relative(vertical, verticalStep);
                BlockPos horizontalPos = cutawayPos.relative(horizontal, horizontalStep);
                return cornerCycle(
                        level,
                        cutawayPos,
                        verticalPos,
                        horizontalPos,
                        queryPos,
                        side
                );
            }
            return false;
        } catch (Throwable throwable) {
            CopycatCutaways.LOGGER.error(
                    "Failed to evaluate copycat CT appearance at {}",
                    queryPos,
                    throwable
            );
            return false;
        }
    }

    private static CtSide offsetSide(Vec3i offset, Direction textureFace) {
        Direction horizontal = horizontalDirection(textureFace);
        Direction vertical = verticalDirection(textureFace);
        int horizontalAmount = offset.get(horizontal.getAxis())
                * horizontal.getAxisDirection().getStep();
        int verticalAmount = offset.get(vertical.getAxis())
                * vertical.getAxisDirection().getStep();
        if (verticalAmount == 0 && horizontalAmount == 0) {
            return CtSide.NONE;
        }
        if (Math.abs(verticalAmount) >= Math.abs(horizontalAmount)) {
            return verticalAmount > 0 ? CtSide.UP : CtSide.DOWN;
        }
        return horizontalAmount > 0 ? CtSide.RIGHT : CtSide.LEFT;
    }

    private static boolean connectSurfaces(List<Surface> source,
                                           List<Surface> target,
                                           Vec3i offset,
                                           @Nullable Direction side) {
        int distance = Math.abs(offset.getX()) + Math.abs(offset.getY()) + Math.abs(offset.getZ());
        if (distance != 1) {
            return false;
        }

        Vec3 translation = new Vec3(offset.getX(), offset.getY(), offset.getZ());
        for (Surface first : source) {
            Direction firstIdentity = first.ctIdentity();
            if (firstIdentity == null || side != null && firstIdentity != side) {
                continue;
            }
            for (Surface second : target) {
                if (second.ctIdentity() != firstIdentity) {
                    continue;
                }
                if (first.normal().dot(second.normal()) <= EPSILON) {
                    continue;
                }

                List<Vec3> translated = second.vertices().stream()
                        .map(point -> point.add(translation))
                        .toList();
                if (sharesWholeEdge(first.vertices(), translated, translation)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean connectSurfacesOnFace(List<Surface> source,
                                                 List<Surface> target,
                                                 Vec3i offset,
                                                 Direction textureFace) {
        return connectSurfaces(source, target, offset, textureFace);
    }

    private static boolean sharesWholeEdge(List<Vec3> first, List<Vec3> second,
                                           Vec3 blockOffset) {
        for (int i = 0; i < first.size(); i++) {
            Vec3 a = first.get(i);
            Vec3 b = first.get((i + 1) % first.size());
            if (a.distanceToSqr(b) <= EPSILON * EPSILON) {
                continue;
            }

            if (Math.abs(b.subtract(a).dot(blockOffset)) > EPSILON) {
                continue;
            }
            for (int j = 0; j < second.size(); j++) {
                Vec3 c = second.get(j);
                Vec3 d = second.get((j + 1) % second.size());
                if (samePoint(a, c) && samePoint(b, d)
                        || samePoint(a, d) && samePoint(b, c)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean samePoint(Vec3 first, Vec3 second) {
        return first.distanceToSqr(second) <= EPSILON * EPSILON;
    }

    private static List<Surface> surfacesAt(BlockAndTintGetter level,
                                            BlockPos pos,
                                            BlockState state) {
        CutawaySnapshot snapshot = CutawayDataAccess.snapshot(level, pos);
        if (snapshot != null) {
            return surfaces(snapshot.plane(), CACHE.get());
        }

        if (state.getBlock() instanceof IMultiStateCopycatBlock) {
            return List.of();
        }

        List<Surface> result = new ArrayList<>(6);
        for (int index = 0; index < FACE_CORNERS.length; index++) {
            Direction face = Direction.from3DDataValue(index);
            if (!Block.isFaceFull(state.getFaceOcclusionShape(level, pos, face), face)) {
                continue;
            }
            result.add(new Surface(
                    face,
                    Vec3.atLowerCornerOf(face.getNormal()),
                    List.of(FACE_CORNERS[index])
            ));
        }
        return result;
    }

    private static boolean materialsCompatible(BlockState firstMaterial,
                                               @Nullable BlockState secondMaterial) {
        if (firstMaterial == null || secondMaterial == null) {
            return false;
        }
        return firstMaterial.getBlock() == secondMaterial.getBlock();
    }

    public static boolean directConnection(BlockAndTintGetter level,
                                           BlockPos firstPos,
                                           BlockPos secondPos,
                                           Direction textureFace) {
        try {
            BlockState firstState = CutawayDataAccess.realState(level, firstPos);
            BlockState secondState = CutawayDataAccess.realState(level, secondPos);
            if (CopycatsPlusCTCompatibility.isRegistered(firstState.getBlock())
                    || CopycatsPlusCTCompatibility.isRegistered(secondState.getBlock())) {
                return CopycatsPlusCTCompatibility.directConnection(
                        level,
                        firstPos,
                        secondPos,
                        textureFace
                );
            }
            if (!materialsCompatible(
                    CutawayDataAccess.material(level, firstPos),
                    CutawayDataAccess.material(level, secondPos)
            )) {
                return false;
            }
            List<Surface> first = surfacesAt(level, firstPos, firstState);
            List<Surface> second = surfacesAt(level, secondPos, secondState);
            return connectSurfacesOnFace(
                    first,
                    second,
                    secondPos.subtract(firstPos),
                    textureFace
            );
        } catch (Throwable throwable) {
            CopycatCutaways.LOGGER.error(
                    "Failed to evaluate direct copycat CT edge from {} to {}",
                    firstPos,
                    secondPos,
                    throwable
            );
            return false;
        }
    }

    public static boolean cornerCycle(BlockAndTintGetter level,
                                      BlockPos current,
                                      BlockPos vertical,
                                      BlockPos horizontal,
                                      BlockPos diagonal,
                                      Direction textureFace) {
        try {
            return directConnection(level, current, vertical, textureFace)
                    && directConnection(level, current, horizontal, textureFace)
                    && directConnection(level, diagonal, vertical, textureFace)
                    && directConnection(level, diagonal, horizontal, textureFace);
        } catch (Throwable throwable) {
            CopycatCutaways.LOGGER.error(
                    "Failed to evaluate four-quadrant copycat CT corner at {}",
                    current,
                    throwable
            );
            return false;
        }
    }

    public static DirectMask directMask(BlockAndTintGetter level,
                                        BlockPos pos,
                                        BlockState state,
                                        Direction textureFace) {
        try {
            Direction horizontal = horizontalDirection(textureFace);
            Direction vertical = verticalDirection(textureFace);
            return new DirectMask(
                    directConnection(level, pos, pos.relative(vertical, 1), textureFace),
                    directConnection(level, pos, pos.relative(vertical, -1), textureFace),
                    directConnection(level, pos, pos.relative(horizontal, -1), textureFace),
                    directConnection(level, pos, pos.relative(horizontal, 1), textureFace)
            );
        } catch (Throwable throwable) {
            CopycatCutaways.LOGGER.error(
                    "Failed to build copycat CT direct mask at {} for {}",
                    pos,
                    textureFace,
                    throwable
            );
            return DirectMask.EMPTY;
        }
    }

    public static Direction horizontalDirection(Direction face) {
        Direction horizontal = face.getAxis() == Direction.Axis.X
                ? Direction.SOUTH
                : Direction.WEST;
        if (face.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            horizontal = horizontal.getOpposite();
        }
        if (face == Direction.DOWN) {
            horizontal = horizontal.getOpposite();
        }
        return horizontal;
    }

    public static Direction verticalDirection(Direction face) {
        Direction vertical = face.getAxis().isHorizontal()
                ? Direction.UP
                : Direction.NORTH;
        if (face == Direction.DOWN) {
            vertical = vertical.getOpposite();
        }
        return vertical;
    }

    private static List<Surface> surfaces(CutPlane plane, GeometryCache cache) {
        if (!plane.equals(cache.plane)) {
            List<Surface> result = new ArrayList<>(7);
            for (int index = 0; index < FACE_CORNERS.length; index++) {
                Direction face = Direction.from3DDataValue(index);
                List<Vec3> polygon = clip(
                        List.of(FACE_CORNERS[index]),
                        plane
                );
                if (polygon.size() >= 3) {
                    result.add(new Surface(
                            face,
                            Vec3.atLowerCornerOf(face.getNormal()),
                            polygon
                    ));
                }
            }

            List<Vec3> cap = CutOutline.capPolygon(plane);
            if (cap.size() >= 3) {
                result.add(new Surface(
                        plane.capCTFace(),
                        plane.visibleNormal(),
                        cap
                ));
            }
            cache.plane = plane;
            cache.surfaces = List.copyOf(result);
        }
        return cache.surfaces;
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
        List<Vec3> result = new ArrayList<>(input.size());
        for (Vec3 point : input) {
            if (result.isEmpty() || !samePoint(result.get(result.size() - 1), point)) {
                result.add(point);
            }
        }
        if (result.size() > 1 && samePoint(result.get(0), result.get(result.size() - 1))) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    private record Surface(@Nullable Direction ctIdentity,
                           Vec3 normal,
                           List<Vec3> vertices) {
    }

    private static final class GeometryCache {

        private CutPlane plane;

        private List<Surface> surfaces = Collections.emptyList();
    }
}
