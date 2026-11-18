package top.kircute.copycatcutaways.block;
import top.kircute.copycatcutaways.CopycatCutaways;
import top.kircute.copycatcutaways.ModRegistry;
import top.kircute.copycatcutaways.ct.CutCTEvaluator;

import com.copycatsplus.copycats.foundation.copycat.CCCopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.copycatsplus.copycats.utility.BlockFaceUtils;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.kircute.copycatcutaways.api.CopycatCTCompatibility;

import java.util.Optional;

public class CutawayCopycatBlock extends CCCopycatBlock {

    public CutawayCopycatBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new CutawayCopycatBlockEntity(pos, state);
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                        @NotNull BlockPos pos, @NotNull CollisionContext context) {

        CutawaySnapshot snapshot = CutawayDataAccess.snapshot(level, pos);
        return snapshot == null ? Shapes.block() : snapshot.outlineShape();
    }

    @Override
    public @NotNull VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                                 @NotNull BlockPos pos, @NotNull CollisionContext context) {

        CutawaySnapshot snapshot = CutawayDataAccess.snapshot(level, pos);
        return snapshot == null ? Shapes.block() : snapshot.collisionShape();
    }

    @Override
    public @NotNull VoxelShape getOcclusionShape(@NotNull BlockState state,
                                                 @NotNull BlockGetter level,
                                                 @NotNull BlockPos pos) {

        CutawaySnapshot snapshot = CutawayDataAccess.snapshot(level, pos);
        return snapshot == null ? Shapes.empty() : snapshot.occlusionShape();
    }

    @Override
    public Optional<Boolean> shapeCanOccludeNeighbor(BlockGetter level,
                                                     BlockPos pos,
                                                     BlockState state,
                                                     BlockPos neighborPos,
                                                     Direction dir) {
        try {
            CutawaySnapshot snapshot = CutawayDataAccess.snapshot(level, pos);
            if (snapshot == null) {
                return Optional.of(false);
            }

            BlockState neighborState = CutawayDataAccess.realState(level, neighborPos);
            boolean covers = BlockFaceUtils.canOcclude(
                    level,
                    neighborState,
                    neighborPos,
                    state,
                    pos,
                    dir.getOpposite()
            );
            return Optional.of(covers);
        } catch (Throwable throwable) {
            CopycatCutaways.LOGGER.error(
                    "Failed to evaluate copycat neighbor occlusion at {} toward {}",
                    pos,
                    neighborPos,
                    throwable
            );

            return Optional.of(false);
        }
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        CutawaySnapshot snapshot = CutawayDataAccess.snapshot(level, pos);
        return snapshot == null ? 0 : snapshot.material().getLightEmission(level, pos);
    }

    @Override
    public boolean isPathfindable(@NotNull BlockState state, @NotNull PathComputationType type) {
        return false;
    }

    @Override
    public boolean supportsExternalFaceHiding(BlockState state) {
        return true;
    }

    @Override
    public boolean canToggleCT(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        return false;
    }

    @Override
    public boolean hidesNeighborFace(BlockGetter level, BlockPos pos, BlockState state,
                                     BlockState neighborState, Direction dir) {
        CutawaySnapshot snapshot = CutawayDataAccess.snapshot(level, pos);
        if (snapshot == null) {
            return false;
        }

        BlockPos neighborPos = pos.relative(dir);
        BlockState neighborMaterial = CutawayDataAccess.material(level, neighborPos);
        if (neighborMaterial == null || !BlockFaceUtils.canOcclude(
                level,
                neighborState,
                neighborPos,
                state,
                pos,
                dir.getOpposite()
        )) {
            return false;
        }
        return snapshot.material().skipRendering(neighborMaterial, dir.getOpposite());
    }

    @Override
    public BlockState getAppearance(BlockState state, BlockAndTintGetter level,
                                    BlockPos pos, Direction side,
                                    @Nullable BlockState ignoredQueryState,
                                    @Nullable BlockPos queryPos) {
        try {

            if (queryPos == null) {
                return state;
            }

            CutawaySnapshot snapshot = CutawayDataAccess.snapshot(level, pos);
            if (snapshot == null) {
                return state;
            }

            BlockState otherState = CopycatCTCompatibility.realBlockState(level, queryPos);
            CopycatCTCompatibility.ConnectionContext context =
                    new CopycatCTCompatibility.ConnectionContext(
                            level,
                            state,
                            pos,
                            snapshot.plane(),
                            snapshot.material(),
                            otherState,
                            queryPos,
                            side
                    );
            BlockState resolvedAppearance = CopycatCTCompatibility.REGISTRY
                    .resolveAppearance(context);
            if (resolvedAppearance != null) {
                return resolvedAppearance;
            }

            boolean connects;
            if (otherState.getBlock() instanceof CutawayCopycatBlock) {
                connects = CutCTEvaluator.canAppearanceConnect(
                        level,
                        queryPos,
                        pos,
                        snapshot,
                        side
                );
            } else if (otherState.getBlock() instanceof ICopycatBlock) {

                connects = false;
            } else {
                connects = CutCTEvaluator.canAppearanceConnect(
                        level,
                        queryPos,
                        pos,
                        snapshot,
                        side
                );
            }

            return connects ? snapshot.material() : state;
        } catch (Throwable throwable) {
            CopycatCutaways.LOGGER.error(
                    "Failed to resolve copycat CT appearance at {} for query {}",
                    pos,
                    queryPos,
                    throwable
            );

            return state;
        }
    }

    @Override
    public boolean isIgnoredConnectivitySide(BlockAndTintGetter level, BlockState state, Direction face,
                                             BlockPos fromPos, @Nullable BlockPos toPos,
                                             @Nullable BlockState ignoredToState) {
        try {

            if (toPos == null) {
                return true;
            }

            CutawaySnapshot snapshot = CutawayDataAccess.snapshot(level, fromPos);
            if (snapshot == null) {
                return true;
            }

            BlockState otherState = CopycatCTCompatibility.realBlockState(level, toPos);
            CopycatCTCompatibility.ConnectionContext context =
                    new CopycatCTCompatibility.ConnectionContext(
                            level,
                            state,
                            fromPos,
                            snapshot.plane(),
                            snapshot.material(),
                            otherState,
                            toPos,
                            face
                    );
            BlockState resolvedAppearance = CopycatCTCompatibility.REGISTRY
                    .resolveAppearance(context);
            boolean connects;
            if (otherState.getBlock() instanceof CutawayCopycatBlock) {
                connects = CutCTEvaluator.canAppearanceConnect(
                        level,
                        toPos,
                        fromPos,
                        snapshot,
                        face
                );
            } else if (resolvedAppearance != null) {
                connects = true;
            } else if (otherState.getBlock() instanceof ICopycatBlock) {
                connects = false;
            } else {
                connects = CutCTEvaluator.canAppearanceConnect(
                        level,
                        toPos,
                        fromPos,
                        snapshot,
                        face
                );
            }
            return !connects;
        } catch (Throwable throwable) {
            CopycatCutaways.LOGGER.error(
                    "Failed to evaluate ignored copycat CT side at {} toward {}",
                    fromPos,
                    toPos,
                    throwable
            );
            return true;
        }
    }

    @Override
    public boolean canConnectTexturesToward(BlockAndTintGetter level, BlockPos fromPos,
                                            BlockPos toPos, BlockState state) {
        try {
            BlockState otherState = CopycatCTCompatibility.realBlockState(level, toPos);

            if (otherState.getBlock() instanceof CutawayCopycatBlock) {
                return CutCTEvaluator.canConnect(level, fromPos, toPos, null);
            }

            if (otherState.getBlock() instanceof ICopycatBlock) {
                CutawaySnapshot snapshot = CutawayDataAccess.snapshot(level, fromPos);
                if (snapshot == null) {
                    return false;
                }
                CopycatCTCompatibility.ConnectionContext context =
                        new CopycatCTCompatibility.ConnectionContext(
                                level,
                                state,
                                fromPos,
                                snapshot.plane(),
                                snapshot.material(),
                                otherState,
                                toPos,
                                null
                        );
                if (CopycatCTCompatibility.REGISTRY.resolveAppearance(context) != null) {
                    return true;
                }
                return false;
            }

            return CutCTEvaluator.canConnect(level, fromPos, toPos, null);
        } catch (Throwable throwable) {
            CopycatCutaways.LOGGER.error(
                    "Failed to evaluate copycat CT connection from {} toward {}",
                    fromPos,
                    toPos,
                    throwable
            );
            return false;
        }
    }

    @Override
    public BlockState transform(BlockState state, StructureTransform transform) {
        return state;
    }

    @Override
    public BlockEntityType<? extends CutawayCopycatBlockEntity> getBlockEntityType() {
        return ModRegistry.CUTAWAY_BLOCK_ENTITY.get();
    }
}
