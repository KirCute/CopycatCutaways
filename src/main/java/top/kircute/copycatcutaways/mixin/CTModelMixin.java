package top.kircute.copycatcutaways.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.copycatsplus.copycats.foundation.copycat.model.FilteredBlockAndTintGetter;
import com.copycatsplus.copycats.foundation.copycat.model.ScaledBlockAndTintGetter;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour.CTContext;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour.ContextRequirement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.kircute.copycatcutaways.ct.CutCTContextFixer;
import top.kircute.copycatcutaways.ct.CopycatsPlusCTCompatibility;
import top.kircute.copycatcutaways.block.CutawayCopycatBlock;

@Mixin(value = CTModel.class, priority = 1100)
public class CTModelMixin {

    @WrapOperation(
            method = "createCTData",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/block/connected/ConnectedTextureBehaviour;buildContext(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lcom/simibubi/create/foundation/block/connected/ConnectedTextureBehaviour$ContextRequirement;)Lcom/simibubi/create/foundation/block/connected/ConnectedTextureBehaviour$CTContext;"
            )
    )
    private CTContext copycatCutaways$fixCornerContext(
            ConnectedTextureBehaviour behaviour,
            BlockAndTintGetter world,
            BlockPos pos,
            BlockState state,
            Direction face,
            ContextRequirement requirement,
            Operation<CTContext> original
    ) {
        CTContext context = original.call(behaviour, world, pos, state, face, requirement);

        if (!(behaviour instanceof EncasedCTBehaviour)) {
            return context;
        }

        BlockAndTintGetter actualWorld = unwrapWorld(world);
        BlockState actualState = actualWorld.getBlockState(pos);

        if (actualState.getBlock() instanceof ICopycatBlock
                && !(actualState.getBlock() instanceof CutawayCopycatBlock)
                && !CopycatsPlusCTCompatibility.isRegistered(actualState.getBlock())) {
            return context;
        }

        if (!(actualState.getBlock() instanceof CutawayCopycatBlock)
                && !Block.isFaceFull(actualState.getFaceOcclusionShape(actualWorld, pos, face), face)) {
            return context;
        }
        return CutCTContextFixer.fix(actualWorld, pos, state, face, context);
    }

    private static BlockAndTintGetter unwrapWorld(BlockAndTintGetter world) {
        BlockAndTintGetter current = world;
        for (int depth = 0; depth < 8; depth++) {
            if (current instanceof FilteredBlockAndTintGetter filtered) {
                current = filtered.wrapped;
                continue;
            }
            if (current instanceof ScaledBlockAndTintGetter scaled) {
                current = scaled.getWrapped();
                continue;
            }
            break;
        }
        return current;
    }
}
