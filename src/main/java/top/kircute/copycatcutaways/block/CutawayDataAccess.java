package top.kircute.copycatcutaways.block;

import com.copycatsplus.copycats.foundation.copycat.ICopycatBlock;
import com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity;
import com.copycatsplus.copycats.foundation.copycat.model.FilteredBlockAndTintGetter;
import com.copycatsplus.copycats.foundation.copycat.model.ScaledBlockAndTintGetter;
import com.copycatsplus.copycats.foundation.copycat.model.CopycatModelCore;
import com.copycatsplus.copycats.foundation.copycat.model.neoforge.CopycatModelNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.extensions.IBlockGetterExtension;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves cutaway data without reading mutable BlockEntities on client mesh threads.
 *
 * <p>Server code uses the authoritative BlockEntity. Client rendering data is read
 * from the model data snapshot carried by the real RenderChunkRegion after Copycats+
 * filtered/scaled views have been unwrapped.</p>
 */
public final class CutawayDataAccess {

    private static final int MAX_UNWRAP_DEPTH = 8;

    private CutawayDataAccess() {
    }

    public static BlockState realState(BlockGetter level, BlockPos pos) {
        ResolvedLocation resolved = unwrap(level, pos);
        return resolved.level().getBlockState(resolved.pos());
    }

    @Nullable
    public static CutawaySnapshot snapshot(BlockGetter level, BlockPos pos) {
        ResolvedLocation resolved = unwrap(level, pos);
        if (isServer(resolved.level())) {
            return resolved.level().getBlockEntity(resolved.pos())
                    instanceof CutawayCopycatBlockEntity blockEntity
                    ? blockEntity.createSnapshot()
                    : null;
        }
        return modelData(resolved.level(), resolved.pos())
                .get(CutawayModelData.SNAPSHOT);
    }

    @Nullable
    public static BlockState material(BlockGetter level, BlockPos pos) {
        ResolvedLocation resolved = unwrap(level, pos);
        BlockState actual = resolved.level().getBlockState(resolved.pos());

        if (!(actual.getBlock() instanceof ICopycatBlock)) {
            return actual;
        }

        if (isServer(resolved.level())) {
            return resolved.level().getBlockEntity(resolved.pos())
                    instanceof ICopycatBlockEntity copycat
                    ? copycat.getMaterial()
                    : null;
        }

        ModelData data = modelData(resolved.level(), resolved.pos());
        CutawaySnapshot snapshot = data.get(CutawayModelData.SNAPSHOT);
        if (snapshot != null) {
            return snapshot.material();
        }

        BlockState singleMaterial = data.get(CopycatModelNeoForge.MATERIAL_PROPERTY);
        if (singleMaterial != null) {
            return singleMaterial;
        }
        return CopycatModelNeoForge.getMaterials(data)
                .get(CopycatModelCore.MATERIAL_KEY);
    }

    private static ModelData modelData(BlockGetter level, BlockPos pos) {
        return level instanceof IBlockGetterExtension extension
                ? extension.getModelData(pos)
                : ModelData.EMPTY;
    }

    private static boolean isServer(BlockGetter level) {
        return level instanceof Level serverAwareLevel && !serverAwareLevel.isClientSide;
    }

    private static ResolvedLocation unwrap(BlockGetter level, BlockPos pos) {
        BlockGetter currentLevel = level;
        BlockPos currentPos = pos;

        for (int depth = 0; depth < MAX_UNWRAP_DEPTH; depth++) {
            if (currentLevel instanceof FilteredBlockAndTintGetter filtered) {
                currentLevel = filtered.wrapped;
                continue;
            }
            if (currentLevel instanceof ScaledBlockAndTintGetter scaled) {
                currentPos = scaled.getTruePos(currentPos);
                currentLevel = scaled.getWrapped();
                continue;
            }
            break;
        }
        return new ResolvedLocation(currentLevel, currentPos);
    }

    private record ResolvedLocation(BlockGetter level, BlockPos pos) {
    }
}
