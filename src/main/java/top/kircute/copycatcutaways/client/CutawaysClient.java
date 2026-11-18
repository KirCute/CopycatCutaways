package top.kircute.copycatcutaways.client;

import top.kircute.copycatcutaways.client.model.CutawayModelCore;
import top.kircute.copycatcutaways.client.model.CutawayModelWrapper;
import top.kircute.copycatcutaways.client.render.PreviewRenderer;
import top.kircute.copycatcutaways.client.render.SelectionRenderer;
import top.kircute.copycatcutaways.client.selection.SelectionClient;

import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import top.kircute.copycatcutaways.CopycatCutaways;
import top.kircute.copycatcutaways.ModRegistry;
import top.kircute.copycatcutaways.config.ClientConfig;
import top.kircute.copycatcutaways.ct.CopycatsPlusCTCompatibility;

import java.util.List;
import java.util.Map;

@Mod(value = CopycatCutaways.MODID, dist = Dist.CLIENT)
public class CutawaysClient {

    public CutawaysClient(IEventBus modBus) {

        modBus.addListener(this::onClientSetup);

        ModLoadingContext.get().getActiveContainer().registerConfig(
                ModConfig.Type.CLIENT,
                ClientConfig.SPEC
        );
        modBus.addListener(ClientConfig::onConfigEvent);

        modBus.addListener(this::onModifyBakingResult);
        modBus.addListener(this::onRegisterBlockColors);

        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onRender);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(CopycatsPlusCTCompatibility::register);
    }

    private void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(com.copycatsplus.copycats.foundation.copycat.ICopycatBlock.wrappedColor(),
                ModRegistry.CUTAWAY_BLOCK.get(),
                ModRegistry.GHOST_CUTAWAY_BLOCK.get());
    }

    private void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {

        Map<ModelResourceLocation, BakedModel> models = event.getModels();

        for (Block block : List.of(
                ModRegistry.CUTAWAY_BLOCK.get(),
                ModRegistry.GHOST_CUTAWAY_BLOCK.get()
        )) {
            for (var state : block.getStateDefinition().getPossibleStates()) {

                ModelResourceLocation location = BlockModelShaper.stateToModelLocation(
                        BuiltInRegistries.BLOCK.getKey(block), state);

                BakedModel original = models.get(location);
                if (original != null) {

                    models.put(location, new CutawayModelWrapper(original, new CutawayModelCore()));
                }
            }
        }
    }

    private void onClientTick(ClientTickEvent.Post event) {
        SelectionClient.clientTick();
    }

    private void onRender(RenderLevelStageEvent event) {

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            SelectionRenderer.render();
        }

        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            PreviewRenderer.render(event.getPoseStack());
        }
    }
}
