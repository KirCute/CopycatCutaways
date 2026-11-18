package top.kircute.copycatcutaways.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import top.kircute.copycatcutaways.geometry.CutCollisionMask;
import top.kircute.copycatcutaways.geometry.CutPlane;

import java.util.Objects;

public record CutawaySnapshot(CutPlane plane,
                              CutCollisionMask collisionMask,
                              BlockState material,
                              VoxelShape collisionShape,
                              VoxelShape outlineShape,
                              VoxelShape occlusionShape) {

    public CutawaySnapshot {
        Objects.requireNonNull(plane, "plane");
        Objects.requireNonNull(collisionMask, "collisionMask");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(collisionShape, "collisionShape");
        Objects.requireNonNull(outlineShape, "outlineShape");
        Objects.requireNonNull(occlusionShape, "occlusionShape");
    }
}
