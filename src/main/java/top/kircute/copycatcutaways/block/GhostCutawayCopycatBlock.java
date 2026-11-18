package top.kircute.copycatcutaways.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * A cutaway copycat with identical visual/selection/CT behavior and no entity
 * collision or ordinary occlusion.
 */
public final class GhostCutawayCopycatBlock extends CutawayCopycatBlock {

    public GhostCutawayCopycatBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull VoxelShape getCollisionShape(@NotNull BlockState state,
                                                 @NotNull BlockGetter level,
                                                 @NotNull BlockPos pos,
                                                 @NotNull CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public boolean canOcclude(BlockGetter level, BlockState state, BlockPos pos) {
        return false;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return false;
    }

    @Override
    public Optional<Boolean> shapeCanOccludeNeighbor(BlockGetter level,
                                                     BlockPos pos,
                                                     BlockState state,
                                                     BlockPos neighborPos,
                                                     Direction dir) {
        return Optional.of(false);
    }
}
