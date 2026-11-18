package top.kircute.copycatcutaways;
import top.kircute.copycatcutaways.block.CutawayCopycatBlock;
import top.kircute.copycatcutaways.block.CutawayCopycatBlockEntity;
import top.kircute.copycatcutaways.block.CutawayCopycatItem;
import top.kircute.copycatcutaways.block.GhostCutawayCopycatBlock;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModRegistry {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(CopycatCutaways.MODID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(CopycatCutaways.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CopycatCutaways.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CopycatCutaways.MODID);

    public static final DeferredBlock<CutawayCopycatBlock> CUTAWAY_BLOCK =
            BLOCKS.register("copycat_cutaway", () -> new CutawayCopycatBlock(
                    BlockBehaviour.Properties.of()

                            .strength(1.0F)

                            .sound(SoundType.STONE)

                            .noOcclusion()

                            .dynamicShape()

                            .mapColor(MapColor.METAL)
            ));

    public static final DeferredBlock<GhostCutawayCopycatBlock> GHOST_CUTAWAY_BLOCK =
            BLOCKS.register("copycat_cutaway_ghost", () -> new GhostCutawayCopycatBlock(
                    BlockBehaviour.Properties.of()
                            .strength(1.0F)
                            .sound(SoundType.STONE)
                            .noOcclusion()
                            .noCollission()
                            .dynamicShape()
                            .mapColor(MapColor.METAL)
                            .isValidSpawn((state, level, pos, entity) -> false)
            ));

    public static final DeferredItem<CutawayCopycatItem> CUTAWAY_ITEM =
            ITEMS.register("copycat_cutaway", () -> new CutawayCopycatItem(

                    CUTAWAY_BLOCK.get(),

                    new Item.Properties()
            ));

    public static final DeferredItem<CutawayCopycatItem> GHOST_CUTAWAY_ITEM =
            ITEMS.register("copycat_cutaway_ghost", () -> new CutawayCopycatItem(
                    GHOST_CUTAWAY_BLOCK.get(),
                    new Item.Properties()
            ));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CutawayCopycatBlockEntity>>
            CUTAWAY_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("copycat_cutaway",

            () -> BlockEntityType.Builder.of(
                    CutawayCopycatBlockEntity::new,
                    CUTAWAY_BLOCK.get(),
                    GHOST_CUTAWAY_BLOCK.get()
            ).build(null));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()

                    .title(Component.translatable("itemGroup.copycatcutaways"))

                    .icon(() -> CUTAWAY_ITEM.get().getDefaultInstance())

                    .displayItems((parameters, output) -> {
                        output.accept(CUTAWAY_ITEM.get());
                        output.accept(GHOST_CUTAWAY_ITEM.get());
                    })

                    .build());

    private ModRegistry() {
    }

    public static boolean isCutawayItem(ItemStack stack) {
        return stack.is(CUTAWAY_ITEM.get()) || stack.is(GHOST_CUTAWAY_ITEM.get());
    }

    public static boolean isCutawayItemForMode(ItemStack stack, boolean noCollision) {
        return noCollision
                ? stack.is(GHOST_CUTAWAY_ITEM.get())
                : stack.is(CUTAWAY_ITEM.get());
    }

    public static void register(IEventBus modBus) {

        BLOCKS.register(modBus);

        ITEMS.register(modBus);

        BLOCK_ENTITY_TYPES.register(modBus);

        CREATIVE_MODE_TABS.register(modBus);
    }
}
