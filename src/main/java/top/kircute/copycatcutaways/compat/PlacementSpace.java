package top.kircute.copycatcutaways.compat;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface PlacementSpace {

    @Nullable
    UUID subLevelId();

    Vec3 toLocalPosition(Vec3 worldPosition);

    Vec3 toLocalDirection(Vec3 worldDirection);

    Vec3 toWorldPosition(Vec3 localPosition);
}
