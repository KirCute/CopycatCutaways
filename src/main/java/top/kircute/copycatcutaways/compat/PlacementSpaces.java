package top.kircute.copycatcutaways.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import top.kircute.copycatcutaways.compat.sable.SablePlacementSpace;

public final class PlacementSpaces {

    private static final String SABLE_MOD_ID = "sable";

    private PlacementSpaces() {
    }

    public static PlacementSpace forTarget(Level level, BlockPos target) {

        if (!ModList.get().isLoaded(SABLE_MOD_ID)) {
            return IdentityPlacementSpace.INSTANCE;
        }
        return SablePlacementSpace.forTarget(level, target);
    }

    public static boolean isCurrent(PlacementSpace space, Level level, BlockPos target) {

        if (space.subLevelId() == null) {
            return true;
        }

        return ModList.get().isLoaded(SABLE_MOD_ID)
                && SablePlacementSpace.isCurrent(space, level, target);
    }
}
