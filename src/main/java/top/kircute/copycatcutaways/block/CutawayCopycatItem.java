package top.kircute.copycatcutaways.block;

import top.kircute.copycatcutaways.placement.CutPlacement;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import top.kircute.copycatcutaways.client.selection.SelectionClient;

import java.util.List;

public class CutawayCopycatItem extends BlockItem {

    public CutawayCopycatItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {

        if (context.getLevel().isClientSide) {
            return SelectionClient.onUseOn(context);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, @NotNull Player player, @NotNull InteractionHand hand) {

        if (level.isClientSide) {
            SelectionClient.onUseAir(level, player, hand);
        }

        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    protected boolean canPlace(@NotNull BlockPlaceContext context, @NotNull BlockState state) {
        if (context instanceof CutPlacement.PlacementContext placementContext
                && placementContext.collisionMask() != null) {
            if (mustSurvive() && !state.canSurvive(context.getLevel(), context.getClickedPos())) {
                return false;
            }
            if (placementContext.noCollision()) {
                return true;
            }
            return CutPlacement.isUnobstructed(
                    context.getLevel(),
                    context.getClickedPos(),
                    placementContext.collisionMask()
            );
        }
        return super.canPlace(context, state);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, List<Component> tooltip,
                                @NotNull TooltipFlag flag) {

        tooltip.add(Component.translatable("tooltip.copycatcutaways.select")
                .withStyle(ChatFormatting.GRAY));

        tooltip.add(Component.translatable("tooltip.copycatcutaways.commit")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
