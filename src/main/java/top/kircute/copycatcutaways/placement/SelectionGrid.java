package top.kircute.copycatcutaways.placement;
import top.kircute.copycatcutaways.geometry.GridPoint;

import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class SelectionGrid {

    private static final Map<Integer, List<GridPoint>> CACHE = new HashMap<>();

    private SelectionGrid() {
    }

    public static GridPoint nearest(Vec3 localPoint, Collection<GridPoint> excluded, int segments) {
        return breakpoints(segments).stream()

                .filter(point -> !excluded.contains(point))

                .min(Comparator.<GridPoint>comparingDouble(point -> point.distanceSqr(localPoint))
                        .thenComparingInt(GridPoint::x)
                        .thenComparingInt(GridPoint::y)
                        .thenComparingInt(GridPoint::z))

                .orElse(null);
    }

    private static List<GridPoint> breakpoints(int segments) {
        if (segments != 2 && segments != 4 && segments != 8 && segments != 16) {
            throw new IllegalArgumentException("segments must be one of 2, 4, 8, 16");
        }
        return CACHE.computeIfAbsent(segments, SelectionGrid::buildBreakpoints);
    }

    private static List<GridPoint> buildBreakpoints(int segments) {
        int step = GridPoint.MAX / segments;
        List<GridPoint> points = new ArrayList<>(expectedBreakpointCount(segments));
        for (int x = 0; x <= GridPoint.MAX; x += step) {
            for (int y = 0; y <= GridPoint.MAX; y += step) {
                for (int z = 0; z <= GridPoint.MAX; z += step) {
                    GridPoint point = new GridPoint(x, y, z);
                    if (point.isOnCubeEdge()) {
                        points.add(point);
                    }
                }
            }
        }
        return List.copyOf(points);
    }

    private static int expectedBreakpointCount(int segments) {
        return 12 * (segments - 1) + 8;
    }
}
