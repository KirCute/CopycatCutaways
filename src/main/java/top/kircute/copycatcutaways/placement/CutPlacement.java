package top.kircute.copycatcutaways.placement;
import top.kircute.copycatcutaways.geometry.GridPoint;
import top.kircute.copycatcutaways.block.CutawayCopycatBlockEntity;
import top.kircute.copycatcutaways.block.CutawayCopycatItem;
import top.kircute.copycatcutaways.block.GhostCutawayCopycatBlock;
import top.kircute.copycatcutaways.geometry.CutCollisionMask;
import top.kircute.copycatcutaways.geometry.CutVoxelShape;
import top.kircute.copycatcutaways.geometry.CutPlane;

import com.copycatsplus.copycats.CCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class CutPlacement {

    private static final double EPSILON = 1.0E-7;

    public static final class PlacementContext extends BlockPlaceContext {

        @Nullable
        private final CutCollisionMask collisionMask;

        private final boolean noCollision;

        private PlacementContext(Level level, Player player, InteractionHand hand,
                                 ItemStack stack, BlockHitResult hitResult,
                                 @Nullable CutCollisionMask collisionMask,
                                 boolean noCollision) {

            super(level, player, hand, stack, hitResult);
            this.collisionMask = collisionMask;
            this.noCollision = noCollision;
        }

        @Nullable
        public CutCollisionMask collisionMask() {
            return collisionMask;
        }

        public boolean noCollision() {
            return noCollision;
        }
    }

    private CutPlacement() {
    }

    public static boolean place(Level level, Player player, InteractionHand hand,
                                ItemStack held, BlockPos target, Vec3 targetSpaceHit,
                                CutPlane.Evaluation evaluation, List<GridPoint> points,
                                boolean noCollision) {

        if (evaluation.result() == CutPlane.Result.AMBIGUOUS_SIDE) {
            return false;
        }

        ItemStack placementStack;
        BlockItem placementItem;
        CutPlane collisionPlane = null;
        CutCollisionMask collisionMask = null;
        boolean fallbackPlacement = !evaluation.isValid();

        if (evaluation.isValid()) {

            if (!(held.getItem() instanceof BlockItem heldBlockItem)) {
                return false;
            }
            if ((heldBlockItem.getBlock() instanceof GhostCutawayCopycatBlock) != noCollision) {
                return false;
            }

            placementStack = held;
            placementItem = heldBlockItem;

            collisionPlane = CutPlane.forCollision(points, evaluation.plane());
            CutPlane effectiveCollisionPlane = collisionPlane == null
                    ? evaluation.plane()
                    : collisionPlane;
            collisionMask = CutVoxelShape.createMask(
                    effectiveCollisionPlane,
                    CutawayCopycatBlockEntity.COLLISION_SEGMENTS
            );
        } else {

            Item baseItem = noCollision
                    ? CCBlocks.COPYCAT_GHOST_BLOCK.asItem()
                    : CCBlocks.COPYCAT_BLOCK.asItem();
            if (!(baseItem instanceof BlockItem baseBlockItem)) {
                return false;
            }

            placementStack = new ItemStack(baseItem);
            placementItem = baseBlockItem;
        }

        BlockPlaceContext context = createContext(
                level, player, hand, placementStack, target, targetSpaceHit,
                collisionMask,
                noCollision
        );

        if (!context.getClickedPos().equals(target)) {
            return false;
        }

        InteractionResult result = placementItem.place(context);
        if (!result.consumesAction()) {
            return false;
        }

        if (evaluation.isValid()) {

            if (!(level.getBlockEntity(target) instanceof CutawayCopycatBlockEntity blockEntity)) {

                level.removeBlock(target, false);
                return false;
            }

            blockEntity.setCutPlane(evaluation.plane(), collisionPlane, collisionMask);
        } else if (fallbackPlacement && !level.isClientSide && !player.isCreative()) {

            held.shrink(1);
        }

        return true;
    }

    private static BlockPlaceContext createContext(Level level, Player player,
                                                   InteractionHand hand, ItemStack stack,
                                                   BlockPos target, Vec3 targetSpaceHit,
                                                   @Nullable CutCollisionMask collisionMask,
                                                   boolean noCollision) {

        Vec3 localHit = targetSpaceHit.subtract(target.getX(), target.getY(), target.getZ());

        Direction face = faceAt(localHit);

        BlockHitResult hitResult = new BlockHitResult(targetSpaceHit, face, target, false);
        return new PlacementContext(
                level,
                player,
                hand,
                stack,
                hitResult,
                collisionMask,
                noCollision
        );
    }

    private static Direction faceAt(Vec3 localHit) {

        double distanceX = Math.min(localHit.x, 1.0 - localHit.x);
        double distanceY = Math.min(localHit.y, 1.0 - localHit.y);
        double distanceZ = Math.min(localHit.z, 1.0 - localHit.z);

        if (distanceX <= distanceY + EPSILON && distanceX <= distanceZ + EPSILON) {
            return localHit.x < 0.5 ? Direction.WEST : Direction.EAST;
        }
        if (distanceY <= distanceZ + EPSILON) {
            return localHit.y < 0.5 ? Direction.DOWN : Direction.UP;
        }
        return localHit.z < 0.5 ? Direction.NORTH : Direction.SOUTH;
    }

    public static boolean isUnobstructed(Level level, BlockPos target,
                                         CutCollisionMask collisionMask) {

        VoxelShape localShape = collisionMask.toVoxelShape();

        if (localShape.isEmpty()) {
            return true;
        }

        VoxelShape worldShape = localShape.move(target.getX(), target.getY(), target.getZ());

        for (Entity entity : level.getEntities(
                (Entity) null,
                worldShape.bounds().inflate(EPSILON),
                EntitySelector.CAN_BE_COLLIDED_WITH
        )) {

            if (Shapes.joinIsNotEmpty(
                    worldShape,
                    Shapes.create(entity.getBoundingBox()),
                    BooleanOp.AND
            )) {
                return false;
            }
        }
        return true;
    }
}
