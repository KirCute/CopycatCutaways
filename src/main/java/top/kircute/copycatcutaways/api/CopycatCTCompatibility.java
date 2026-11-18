package top.kircute.copycatcutaways.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import top.kircute.copycatcutaways.CopycatCutaways;
import top.kircute.copycatcutaways.block.CutawayDataAccess;
import top.kircute.copycatcutaways.geometry.CutPlane;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class CopycatCTCompatibility {

    public static final Registrar REGISTRY = new Registrar();

    private CopycatCTCompatibility() {
    }

    /**
     * all geometry and ownership fields must describe the actual world state.
     * Caller-supplied CT appearance/query states must not be stored here.
     */
    public record ConnectionContext(BlockAndTintGetter level,
                                    BlockState cutawayState,
                                    BlockPos cutawayPos,
                                    CutPlane plane,
                                    BlockState cutawayMaterial,
                                    BlockState otherState,
                                    BlockPos otherPos,
                                    @Nullable Direction textureFace) {
    }

    @FunctionalInterface
    public interface AppearanceResolver {
        @Nullable
        BlockState resolveAppearance(ConnectionContext context);
    }

    public interface RealBlockStateView {
        BlockState getRealBlockState(BlockPos pos);
    }

    public static final class Registrar {

        private final Map<Block, AppearanceResolver> appearanceResolvers =
                new ConcurrentHashMap<>();

        public void registerAppearance(Block block, AppearanceResolver resolver) {
            Objects.requireNonNull(block, "block");
            Objects.requireNonNull(resolver, "resolver");
            AppearanceResolver previous = appearanceResolvers.putIfAbsent(block, resolver);
            if (previous != null) {
                CopycatCutaways.LOGGER.warn(
                        "Duplicate copycat CT appearance registration for {}; keeping the first resolver",
                        block
                );
            }
        }

        public boolean hasAppearanceResolver(Block block) {
            return appearanceResolvers.containsKey(block);
        }

        @Nullable
        public BlockState resolveAppearance(ConnectionContext context) {
            AppearanceResolver resolver = appearanceResolvers.get(context.otherState().getBlock());
            if (resolver == null) {
                return null;
            }
            try {
                return resolver.resolveAppearance(context);
            } catch (Throwable throwable) {
                CopycatCutaways.LOGGER.error(
                        "Copycat CT appearance resolver failed for {} at {}",
                        context.otherState(),
                        context.otherPos(),
                        throwable
                );
                return null;
            }
        }
    }

    public static BlockState realBlockState(BlockAndTintGetter level, BlockPos pos) {
        if (level instanceof RealBlockStateView view) {
            return view.getRealBlockState(pos);
        }
        return CutawayDataAccess.realState(level, pos);
    }
}
