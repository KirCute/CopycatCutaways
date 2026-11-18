package top.kircute.copycatcutaways.compat.sable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import top.kircute.copycatcutaways.compat.IdentityPlacementSpace;
import top.kircute.copycatcutaways.compat.PlacementSpace;

import java.util.UUID;

public final class SablePlacementSpace implements PlacementSpace {

    private final SubLevel subLevel;

    private final boolean clientSide;

    private SablePlacementSpace(SubLevel subLevel, boolean clientSide) {
        this.subLevel = subLevel;
        this.clientSide = clientSide;
    }

    public static PlacementSpace forTarget(Level level, BlockPos target) {

        SubLevel containing = Sable.HELPER.getContaining(level, target);

        if (containing == null || containing.getLevel() != level) {
            return IdentityPlacementSpace.INSTANCE;
        }

        return new SablePlacementSpace(containing, level.isClientSide);
    }

    @Override
    public UUID subLevelId() {
        return this.subLevel.getUniqueId();
    }

    private Pose3dc pose() {
        if (this.clientSide && this.subLevel instanceof ClientSubLevel clientSubLevel) {
            return clientSubLevel.renderPose();
        }
        return this.subLevel.logicalPose();
    }

    @Override
    public Vec3 toLocalPosition(Vec3 worldPosition) {
        return this.pose().transformPositionInverse(worldPosition);
    }

    @Override
    public Vec3 toLocalDirection(Vec3 worldDirection) {
        return this.pose().transformNormalInverse(worldDirection).normalize();
    }

    @Override
    public Vec3 toWorldPosition(Vec3 localPosition) {
        return this.pose().transformPosition(localPosition);
    }

    public static boolean isCurrent(PlacementSpace space, Level level, BlockPos target) {

        if (!(space instanceof SablePlacementSpace sableSpace)) {
            return true;
        }

        SubLevel containing = Sable.HELPER.getContaining(level, target);
        return containing != null
                && containing.getLevel() == level
                && containing.getUniqueId().equals(sableSpace.subLevel.getUniqueId());
    }
}
