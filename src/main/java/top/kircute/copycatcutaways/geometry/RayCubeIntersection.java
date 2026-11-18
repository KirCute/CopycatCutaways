package top.kircute.copycatcutaways.geometry;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class RayCubeIntersection {

    private static final double EPSILON = 1.0E-9;

    private RayCubeIntersection() {
    }

    public static Optional<Vec3> farHit(Vec3 eye, Vec3 direction, double reach, BlockPos target) {

        Vec3 localOrigin = eye.subtract(target.getX(), target.getY(), target.getZ());

        double tNear = 0.0;
        double tFar = reach;

        double[] origins = {localOrigin.x, localOrigin.y, localOrigin.z};
        double[] directions = {direction.x, direction.y, direction.z};

        for (int axis = 0; axis < 3; axis++) {

            double origin = origins[axis];
            double delta = directions[axis];

            if (Math.abs(delta) < EPSILON) {
                if (origin < -EPSILON || origin > 1.0 + EPSILON) {
                    return Optional.empty();
                }
                continue;
            }

            double first = -origin / delta;
            double second = (1.0 - origin) / delta;

            if (first > second) {
                double swap = first;
                first = second;
                second = swap;
            }

            tNear = Math.max(tNear, first);
            tFar = Math.min(tFar, second);

            if (tNear > tFar + EPSILON) {
                return Optional.empty();
            }
        }

        if (tFar < -EPSILON) {
            return Optional.empty();
        }

        double clampedFar = Math.max(0.0, tFar);

        return Optional.of(localOrigin.add(direction.scale(clampedFar))
                .add(target.getX(), target.getY(), target.getZ()));
    }
}
