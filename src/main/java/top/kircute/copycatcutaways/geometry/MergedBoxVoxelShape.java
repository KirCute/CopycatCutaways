package top.kircute.copycatcutaways.geometry;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

final class MergedBoxVoxelShape extends VoxelShape {

    private final DoubleList coords;

    private volatile List<AABB> boxes;

    MergedBoxVoxelShape(DiscreteVoxelShape discreteShape) {
        super(discreteShape);
        this.coords = createCoords(CutCollisionMask.SEGMENTS);
    }

    @Override
    public DoubleList getCoords(Direction.Axis axis) {
        return coords;
    }

    @Override
    public void forAllBoxes(Shapes.DoubleLineConsumer consumer) {
        for (AABB box : boxes()) {

            consumer.consume(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        }
    }

    private List<AABB> boxes() {
        // Collision uses the bit-backed discrete shape directly. Merged boxes are
        // only needed by outline, clipping and selection APIs, so build them once
        // on demand instead of during every load.
        List<AABB> current = boxes;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            current = boxes;
            if (current == null) {
                List<AABB> created = new ArrayList<>();
                shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> created.add(new AABB(
                        coords.getDouble(x1),
                        coords.getDouble(y1),
                        coords.getDouble(z1),
                        coords.getDouble(x2),
                        coords.getDouble(y2),
                        coords.getDouble(z2)
                )), true);
                current = List.copyOf(created);
                boxes = current;
            }
        }
        return current;
    }

    private static DoubleList createCoords(int segments) {
        double[] values = new double[segments + 1];
        for (int i = 0; i <= segments; i++) {
            values[i] = i / (double) segments;
        }
        return DoubleArrayList.wrap(values);
    }
}
