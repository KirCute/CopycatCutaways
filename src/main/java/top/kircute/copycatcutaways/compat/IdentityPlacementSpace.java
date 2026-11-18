package top.kircute.copycatcutaways.compat;

import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class IdentityPlacementSpace implements PlacementSpace {

    public static final IdentityPlacementSpace INSTANCE = new IdentityPlacementSpace();

    private IdentityPlacementSpace() {
    }

    @Override
    public UUID subLevelId() {
        return null;
    }

    @Override
    public Vec3 toLocalPosition(Vec3 worldPosition) {
        return worldPosition;
    }

    @Override
    public Vec3 toLocalDirection(Vec3 worldDirection) {
        return worldDirection;
    }

    @Override
    public Vec3 toWorldPosition(Vec3 localPosition) {
        return localPosition;
    }
}
