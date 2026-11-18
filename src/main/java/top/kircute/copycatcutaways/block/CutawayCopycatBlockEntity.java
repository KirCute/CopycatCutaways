package top.kircute.copycatcutaways.block;
import top.kircute.copycatcutaways.geometry.CutOcclusionShape;
import top.kircute.copycatcutaways.geometry.CutOutline;
import top.kircute.copycatcutaways.geometry.CutCollisionMask;
import top.kircute.copycatcutaways.ModRegistry;
import top.kircute.copycatcutaways.geometry.CutPlane;

import com.copycatsplus.copycats.foundation.copycat.CCCopycatBlockEntity;
import com.copycatsplus.copycats.utility.BlockEntityUtils;
import com.copycatsplus.copycats.utility.neoforge.ModelDataUtils;
import com.copycatsplus.copycats.utility.shape.OutlinedVoxelShape;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

public class CutawayCopycatBlockEntity extends CCCopycatBlockEntity {

    public static final int COLLISION_SEGMENTS = CutCollisionMask.SEGMENTS;

    private static final String COLLISION_MASK_VERSION = "CollisionMaskVersion";

    private static final String COLLISION_MASK = "CollisionMask";

    @Nullable
    private CutPlane cutPlane;

    @Nullable
    private CutPlane collisionPlane;

    @Nullable
    private CutCollisionMask collisionMask;

    private VoxelShape collisionShape = Shapes.block();

    private VoxelShape outlineShape = Shapes.block();

    private VoxelShape occlusionShape = Shapes.empty();

    public CutawayCopycatBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.CUTAWAY_BLOCK_ENTITY.get(), pos, state);
    }

    public @Nullable CutPlane getCutPlane() {
        return cutPlane;
    }

    public VoxelShape getCollisionShape() {
        return collisionShape;
    }

    public VoxelShape getOutlineShape() {
        return outlineShape;
    }

    public VoxelShape getOcclusionShape() {
        return occlusionShape;
    }

    @Nullable
    public CutawaySnapshot createSnapshot() {
        BlockState material = getMaterial();
        if (cutPlane == null || collisionMask == null || material == null) {
            return null;
        }
        return new CutawaySnapshot(
                cutPlane,
                collisionMask,
                material,
                collisionShape,
                outlineShape,
                occlusionShape
        );
    }

    @Override
    public boolean isCTEnabled() {
        return true;
    }

    public void setCutPlane(@Nullable CutPlane plane, @Nullable CutPlane collision) {
        setCutPlane(plane, collision, null);
    }

    public void setCutPlane(@Nullable CutPlane plane, @Nullable CutPlane collision,
                            @Nullable CutCollisionMask mask) {
        cutPlane = plane;

        collisionPlane = collision == null ? plane : collision;

        rebuildShape(mask);

        setChanged();

        if (level != null) {
            BlockEntityUtils.redraw(this);
        }
    }

    @Override
    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {

        super.read(tag, registries, clientPacket);

        cutPlane = CutPlane.fromNbt(tag);
        collisionPlane = CutPlane.fromNbt(tag, "CollisionPlane");

        if (collisionPlane == null) {
            collisionPlane = cutPlane;
        }

        rebuildShape(readCollisionMask(tag));

        if (level != null && level.isClientSide()) {
            requestModelDataUpdate();
        }
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {

        super.write(tag, registries, clientPacket);

        if (cutPlane != null) {
            cutPlane.writeNbt(tag);
        }

        if (collisionPlane != null && !collisionPlane.equals(cutPlane)) {
            collisionPlane.writeNbt(tag, "CollisionPlane");
        }

        writeCollisionMask(tag);
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {

        super.writeSafe(tag, registries);

        if (cutPlane != null) {
            cutPlane.writeNbt(tag);
        }

        if (collisionPlane != null && !collisionPlane.equals(cutPlane)) {
            collisionPlane.writeNbt(tag, "CollisionPlane");
        }

        writeCollisionMask(tag);
    }

    @Override
    public ModelData getModelData() {

        ModelData.Builder builder = ModelData.builder();
        ModelData base = super.getModelData();
        ModelDataUtils.copyModelData(base, builder);

        CutawaySnapshot snapshot = createSnapshot();
        if (snapshot != null) {
            builder.with(CutawayModelData.SNAPSHOT, snapshot);
        }

        return builder.build();
    }

    @Override
    public void transform(BlockEntity blockEntity, StructureTransform transform) {

        setMaterialInternal(transform.apply(getMaterial()));

        if (cutPlane != null) {
            cutPlane = cutPlane.transform(transform);
        }
        if (collisionPlane != null) {
            collisionPlane = collisionPlane.transform(transform);
        }

        rebuildShape(null);

        notifyUpdate();

        if (level != null && level.isClientSide()) {
            requestModelDataUpdate();
        }
    }

    private void rebuildShape(@Nullable CutCollisionMask preferredMask) {

        CutPlane effectiveCollisionPlane = collisionPlane == null ? cutPlane : collisionPlane;

        if (effectiveCollisionPlane == null) {
            collisionMask = null;
            collisionShape = Shapes.block();
        } else {
            collisionMask = preferredMask != null
                    ? preferredMask
                    : CutCollisionMask.fromPlane(effectiveCollisionPlane);
            collisionShape = collisionMask.toVoxelShape();
        }

        outlineShape = cutPlane == null
                ? collisionShape
                : new OutlinedVoxelShape(collisionShape, CutOutline.create(cutPlane));

        occlusionShape = cutPlane == null ? Shapes.empty() : CutOcclusionShape.create(cutPlane);
    }

    @Nullable
    private static CutCollisionMask readCollisionMask(CompoundTag tag) {
        if (tag.getInt(COLLISION_MASK_VERSION) != CutCollisionMask.VERSION) {
            return null;
        }
        return CutCollisionMask.fromPacked(tag.getLongArray(COLLISION_MASK));
    }

    private void writeCollisionMask(CompoundTag tag) {
        if (collisionMask == null) {
            return;
        }
        tag.putInt(COLLISION_MASK_VERSION, CutCollisionMask.VERSION);
        tag.putLongArray(COLLISION_MASK, collisionMask.copyWords());
    }

}
