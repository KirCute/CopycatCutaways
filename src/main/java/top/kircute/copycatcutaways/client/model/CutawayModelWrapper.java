package top.kircute.copycatcutaways.client.model;

import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.model.FilteredBlockAndTintGetter;
import com.copycatsplus.copycats.foundation.copycat.model.neoforge.CopycatModelNeoForge;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import top.kircute.copycatcutaways.api.CopycatCTCompatibility;
import top.kircute.copycatcutaways.block.CutawaySnapshot;
import top.kircute.copycatcutaways.block.CutawayModelData;

import java.util.Map;

public class CutawayModelWrapper extends CopycatModelNeoForge {

    private final CutawayModelCore cutCore;

    public CutawayModelWrapper(BakedModel original, CutawayModelCore core) {
        super(original, core, false);
        this.cutCore = core;
    }

    @Override
    public ModelData.Builder gatherModelData(ModelData.Builder builder, BlockAndTintGetter level,
                                             BlockPos pos, BlockState state,
                                             ModelData blockEntityData) {

        CutawaySnapshot snapshot = blockEntityData.get(CutawayModelData.SNAPSHOT);
        if (snapshot == null) {

            super.gatherModelData(builder, level, pos, state, blockEntityData);
            return builder;
        }

        ModelData realData = super.gatherModelData(
                ModelData.builder(), level, pos, state, blockEntityData
        ).build();
        ModelData appearanceData = super.gatherModelData(
                ModelData.builder(),
                new AppearanceWorld(level, state, pos, snapshot),
                pos,
                state,
                blockEntityData
        ).build();

        copyNonOcclusionProperties(builder, appearanceData);
        copyOcclusionProperty(builder, realData);

        builder.with(CutawayModelData.SNAPSHOT, snapshot);

        return builder;
    }

    private static void copyNonOcclusionProperties(ModelData.Builder builder,
                                                   ModelData data) {
        for (ModelProperty<?> property : data.getProperties()) {
            Object value = copyableValue(data, property);
            if (!isOcclusionValue(value)) {
                copyProperty(builder, property, value);
            }
        }
    }

    private static void copyOcclusionProperty(ModelData.Builder builder,
                                              ModelData data) {
        for (ModelProperty<?> property : data.getProperties()) {
            Object value = copyableValue(data, property);
            if (isOcclusionValue(value)) {
                copyProperty(builder, property, value);
            }
        }
    }

    private static boolean isOcclusionValue(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            return false;
        }
        return map.values().stream()
                .allMatch(entry -> entry instanceof CopycatModelNeoForge.OcclusionData);
    }

    private static Object copyableValue(ModelData data, ModelProperty<?> property) {
        return data.get(property);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void copyProperty(ModelData.Builder builder,
                                     ModelProperty<?> property,
                                     Object value) {
        builder.with((ModelProperty) property, value);
    }

    private static final class AppearanceWorld
            extends FilteredBlockAndTintGetter
            implements CopycatCTCompatibility.RealBlockStateView {
        private final BlockAndTintGetter realLevel;
        private final BlockState cutawayState;
        private final BlockPos cutawayPos;
        private final CutawaySnapshot snapshot;

        private AppearanceWorld(BlockAndTintGetter realLevel,
                                BlockState cutawayState,
                                BlockPos cutawayPos,
                                CutawaySnapshot snapshot) {
            super(realLevel, ignored -> true);
            this.realLevel = realLevel;
            this.cutawayState = cutawayState;
            this.cutawayPos = cutawayPos;
            this.snapshot = snapshot;
        }

        @Override
        public BlockState getRealBlockState(BlockPos pos) {
            return realLevel.getBlockState(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState actual = realLevel.getBlockState(pos);
            if (pos.equals(cutawayPos) || !(actual.getBlock() instanceof ICopycatBlock)) {
                return actual;
            }
            BlockState appearance = CopycatCTCompatibility.REGISTRY.resolveAppearance(
                    new CopycatCTCompatibility.ConnectionContext(
                            realLevel,
                            cutawayState,
                            cutawayPos,
                            snapshot.plane(),
                            snapshot.material(),
                            actual,
                            pos,
                            null
                    )
            );
            return appearance == null ? actual : appearance;
        }
    }

    @Override
    protected void prepareModelCore(@NotNull BlockState state, @NotNull RandomSource random,
                                    @NotNull ModelData data) {

        super.prepareModelCore(state, random, data);

        CutawaySnapshot snapshot = data.get(CutawayModelData.SNAPSHOT);
        cutCore.setData(snapshot == null ? null : snapshot.plane());
    }
}
