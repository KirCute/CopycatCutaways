package top.kircute.copycatcutaways.ct;
import top.kircute.copycatcutaways.CopycatCutaways;
import top.kircute.copycatcutaways.block.CutawayCopycatBlock;

import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour.CTContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class CutCTContextFixer {

    private CutCTContextFixer() {
    }

    public static CTContext fix(BlockAndTintGetter level, BlockPos pos,
                                BlockState state, Direction face, CTContext original) {
        try {
            Direction horizontal = CutCTEvaluator.horizontalDirection(face);
            Direction vertical = CutCTEvaluator.verticalDirection(face);
            boolean currentCut = isCut(level, pos);

            BlockPos upPos = pos.relative(vertical, 1);
            BlockPos downPos = pos.relative(vertical, -1);
            BlockPos leftPos = pos.relative(horizontal, -1);
            BlockPos rightPos = pos.relative(horizontal, 1);

            boolean up = resolveDirect(level, pos, upPos, face, original.up, currentCut);
            boolean down = resolveDirect(level, pos, downPos, face, original.down, currentCut);
            boolean left = resolveDirect(level, pos, leftPos, face, original.left, currentCut);
            boolean right = resolveDirect(level, pos, rightPos, face, original.right, currentCut);

            boolean topLeft = resolveCorner(
                    level, pos, upPos, leftPos, upPos.relative(horizontal, -1),
                    face, original.topLeft, up, left, currentCut
            );
            boolean topRight = resolveCorner(
                    level, pos, upPos, rightPos, upPos.relative(horizontal, 1),
                    face, original.topRight, up, right, currentCut
            );
            boolean bottomLeft = resolveCorner(
                    level, pos, downPos, leftPos, downPos.relative(horizontal, -1),
                    face, original.bottomLeft, down, left, currentCut
            );
            boolean bottomRight = resolveCorner(
                    level, pos, downPos, rightPos, downPos.relative(horizontal, 1),
                    face, original.bottomRight, down, right, currentCut
            );

            CTContext result = new CTContext();
            result.up = up;
            result.down = down;
            result.left = left;
            result.right = right;
            result.topLeft = topLeft;
            result.topRight = topRight;
            result.bottomLeft = bottomLeft;
            result.bottomRight = bottomRight;
            return result;
        } catch (Throwable throwable) {
            CopycatCutaways.LOGGER.error(
                    "Failed to correct copycat CT context at {} for {}",
                    pos,
                    face,
                    throwable
            );

            return original;
        }
    }

    private static boolean resolveDirect(BlockAndTintGetter level,
                                         BlockPos current,
                                         BlockPos target,
                                         Direction face,
                                         boolean original,
                                         boolean currentCut) {
        BlockState currentState = level.getBlockState(current);
        BlockState targetState = level.getBlockState(target);
        boolean currentRegistered = CopycatsPlusCTCompatibility.isRegistered(currentState.getBlock());
        boolean targetRegistered = CopycatsPlusCTCompatibility.isRegistered(targetState.getBlock());
        if (!currentCut && !isCut(level, target)
                && !currentRegistered && !targetRegistered) {
            return original;
        }
        if (currentCut) {
            CutCTEvaluator.DirectMask mask = CutCTEvaluator.directMask(
                    level,
                    current,
                    level.getBlockState(current),
                    face
            );
            return switch (relativeSide(
                    CutCTEvaluator.verticalDirection(face),
                    CutCTEvaluator.horizontalDirection(face),
                    target.subtract(current)
            )) {
                case UP -> mask.up();
                case DOWN -> mask.down();
                case LEFT -> mask.left();
                case RIGHT -> mask.right();
                case NONE -> false;
            };
        }
        if (currentRegistered || targetRegistered) {
            return CopycatsPlusCTCompatibility.directConnection(
                    level,
                    current,
                    target,
                    face
            );
        }
        return CutCTEvaluator.directConnection(level, current, target, face);
    }

    private static boolean resolveCorner(BlockAndTintGetter level,
                                         BlockPos current,
                                         BlockPos vertical,
                                         BlockPos horizontal,
                                         BlockPos diagonal,
                                         Direction face,
                                         boolean original,
                                         boolean verticalBit,
                                         boolean horizontalBit,
                                         boolean currentCut) {
        boolean relevant = currentCut
                || isCut(level, vertical)
                || isCut(level, horizontal)
                || isCut(level, diagonal);
        if (!relevant) {
            return original;
        }

        if (!hasSupportedSurface(level, current, face)
                || !hasSupportedSurface(level, vertical, face)
                || !hasSupportedSurface(level, horizontal, face)
                || !hasSupportedSurface(level, diagonal, face)) {
            return original;
        }
        return verticalBit
                && horizontalBit
                && CutCTEvaluator.cornerCycle(
                        level,
                        current,
                        vertical,
                        horizontal,
                        diagonal,
                        face
                );
    }

    private static boolean hasSupportedSurface(BlockAndTintGetter level,
                                               BlockPos pos,
                                               Direction face) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CutawayCopycatBlock) {
            return true;
        }
        if (state.getBlock() instanceof ICopycatBlock) {
            return CopycatsPlusCTCompatibility.isRegistered(state.getBlock());
        }
        return Block.isFaceFull(state.getFaceOcclusionShape(level, pos, face), face);
    }

    private static Side relativeSide(Direction vertical, Direction horizontal,
                                     Vec3i offset) {
        int verticalAmount = offset.get(vertical.getAxis())
                * vertical.getAxisDirection().getStep();
        int horizontalAmount = offset.get(horizontal.getAxis())
                * horizontal.getAxisDirection().getStep();
        if (verticalAmount == 0 && horizontalAmount == 0) {
            return Side.NONE;
        }
        if (Math.abs(verticalAmount) >= Math.abs(horizontalAmount)) {
            return verticalAmount > 0 ? Side.UP : Side.DOWN;
        }
        return horizontalAmount > 0 ? Side.RIGHT : Side.LEFT;
    }

    private static boolean isCut(BlockAndTintGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof CutawayCopycatBlock;
    }

    private enum Side {
        UP,
        DOWN,
        LEFT,
        RIGHT,
        NONE
    }
}
