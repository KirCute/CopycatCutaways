package top.kircute.copycatcutaways.geometry;

import net.createmod.catnip.data.Pair;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CutOutline {

    private static final double EPSILON = 1.0E-7;

    private static final Vec3[][] CUBE_EDGES = {
            {new Vec3(0, 0, 0), new Vec3(1, 0, 0)}, {new Vec3(0, 0, 1), new Vec3(1, 0, 1)},
            {new Vec3(0, 1, 0), new Vec3(1, 1, 0)}, {new Vec3(0, 1, 1), new Vec3(1, 1, 1)},
            {new Vec3(0, 0, 0), new Vec3(0, 1, 0)}, {new Vec3(1, 0, 0), new Vec3(1, 1, 0)},
            {new Vec3(0, 0, 1), new Vec3(0, 1, 1)}, {new Vec3(1, 0, 1), new Vec3(1, 1, 1)},
            {new Vec3(0, 0, 0), new Vec3(0, 0, 1)}, {new Vec3(1, 0, 0), new Vec3(1, 0, 1)},
            {new Vec3(0, 1, 0), new Vec3(0, 1, 1)}, {new Vec3(1, 1, 0), new Vec3(1, 1, 1)}
    };

    private CutOutline() {
    }

    public static List<Pair<Vec3, Vec3>> create(CutPlane plane) {

        List<Pair<Vec3, Vec3>> edges = new ArrayList<>(18);

        for (Vec3[] edge : CUBE_EDGES) {

            double first = plane.signedDistance(edge[0]);
            double second = plane.signedDistance(edge[1]);

            if (first >= -EPSILON && second >= -EPSILON) {
                addEdge(edges, edge[0], edge[1]);

            } else if (first < -EPSILON && second < -EPSILON) {
                continue;

            } else {

                double t = first / (first - second);
                Vec3 intersection = edge[0].lerp(edge[1], t);

                if (first >= -EPSILON) {
                    addEdge(edges, edge[0], intersection);
                } else {
                    addEdge(edges, intersection, edge[1]);
                }
            }
        }

        List<Vec3> cap = capPolygon(plane);
        for (int i = 0; i < cap.size(); i++) {
            addEdge(edges, cap.get(i), cap.get((i + 1) % cap.size()));
        }

        return List.copyOf(edges);
    }

    public static List<Vec3> capPolygon(CutPlane plane) {

        List<Vec3> points = new ArrayList<>(6);

        for (Vec3[] edge : CUBE_EDGES) {

            double first = plane.signedDistance(edge[0]);
            double second = plane.signedDistance(edge[1]);

            if (Math.abs(first) < EPSILON) {
                addUnique(points, edge[0]);
            }
            if (Math.abs(second) < EPSILON) {
                addUnique(points, edge[1]);
            }

            if ((first < -EPSILON && second > EPSILON) || (first > EPSILON && second < -EPSILON)) {
                addUnique(points, edge[0].lerp(edge[1], first / (first - second)));
            }
        }

        if (points.size() < 3) {
            return List.of();
        }

        Vec3 center = points.stream().reduce(Vec3.ZERO, Vec3::add).scale(1.0 / points.size());

        Vec3 normal = plane.normal();
        Vec3 helper = Math.abs(normal.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = normal.cross(helper).normalize();
        Vec3 v = normal.cross(u);

        points.sort(Comparator.comparingDouble(point -> {
            Vec3 offset = point.subtract(center);
            return Math.atan2(offset.dot(v), offset.dot(u));
        }));
        return points;
    }

    private static void addEdge(List<Pair<Vec3, Vec3>> edges, Vec3 start, Vec3 end) {
        if (start.distanceToSqr(end) > EPSILON * EPSILON) {
            edges.add(Pair.of(start, end));
        }
    }

    private static void addUnique(List<Vec3> points, Vec3 point) {
        if (points.stream().noneMatch(existing -> existing.distanceToSqr(point) < EPSILON * EPSILON)) {
            points.add(point);
        }
    }
}
