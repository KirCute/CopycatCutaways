package top.kircute.copycatcutaways.geometry;

import net.minecraft.world.phys.shapes.VoxelShape;

public final class CutVoxelShape {

    private CutVoxelShape() {
    }

    public static VoxelShape create(CutPlane plane, int segments) {
        return createMask(plane, segments).toVoxelShape();
    }

    public static CutCollisionMask createMask(CutPlane plane, int segments) {
        if (segments != CutCollisionMask.SEGMENTS) {
            throw new IllegalArgumentException(
                    "segments must be " + CutCollisionMask.SEGMENTS
            );
        }
        return CutCollisionMask.fromPlane(plane);
    }
}
