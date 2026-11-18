package top.kircute.copycatcutaways.client.render;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import top.kircute.copycatcutaways.client.selection.SelectionClient;
import top.kircute.copycatcutaways.compat.PlacementSpace;
import top.kircute.copycatcutaways.geometry.GridPoint;

public final class SelectionRenderer {

    private static final int WHITE = 0xF2F2F2;

    private static final int GREEN = 0x35D45B;

    private static final int YELLOW = 0xFFE24A;

    private static final double MARKER_RADIUS = 1.0 / 64.0;

    private SelectionRenderer() {
    }

    public static void render() {

        SelectionClient.Session session = SelectionClient.session();
        if (session == null) {
            removeOutline();
            return;
        }

        if (Minecraft.getInstance().player == null) {
            removeOutline();
            return;
        }

        GridPoint hovered = session.hovered();
        if (hovered != null) {
            SelectionClient.updateHover(Minecraft.getInstance().player, session);
            hovered = session.hovered();
        }

        showTargetCube("cutaway_outline", session.target(), session.placementSpace(), WHITE);

        for (int i = 0; i < session.selected().size(); i++) {
            showMarker(
                    "cutaway_selected_" + i,
                    session.target(),
                    session.placementSpace(),
                    session.selected().get(i),
                    YELLOW
            );
        }

        if (hovered != null && !session.selected().contains(hovered)) {
            showMarker("cutaway_hover", session.target(), session.placementSpace(), hovered, GREEN);
        } else {

            Outliner.getInstance().remove("cutaway_hover");
        }
    }

    private static void showTargetCube(String key, BlockPos target, PlacementSpace space,
                                       int color) {

        Vec3 min = new Vec3(target.getX(), target.getY(), target.getZ());
        Vec3 max = min.add(1.0, 1.0, 1.0);
        showCube(key, transformedCubeCorners(min, max, space), color, false);
    }

    private static void showMarker(String key, BlockPos target, PlacementSpace space,
                                   GridPoint point, int color) {

        Vec3 center = point.center().add(target.getX(), target.getY(), target.getZ());

        Vec3 min = center.subtract(MARKER_RADIUS, MARKER_RADIUS, MARKER_RADIUS);
        Vec3 max = center.add(MARKER_RADIUS, MARKER_RADIUS, MARKER_RADIUS);
        showCube(key, transformedCubeCorners(min, max, space), color, true);
    }

    private static Vec3[] transformedCubeCorners(Vec3 min, Vec3 max, PlacementSpace space) {
        return new Vec3[]{
                space.toWorldPosition(new Vec3(min.x, min.y, min.z)),
                space.toWorldPosition(new Vec3(max.x, min.y, min.z)),
                space.toWorldPosition(new Vec3(max.x, min.y, max.z)),
                space.toWorldPosition(new Vec3(min.x, min.y, max.z)),
                space.toWorldPosition(new Vec3(min.x, max.y, min.z)),
                space.toWorldPosition(new Vec3(max.x, max.y, min.z)),
                space.toWorldPosition(new Vec3(max.x, max.y, max.z)),
                space.toWorldPosition(new Vec3(min.x, max.y, max.z))
        };
    }

    private static void showCube(String key, Vec3[] corners, int color, boolean thin) {

        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };

        for (int i = 0; i < edges.length; i++) {
            Outliner.getInstance()
                    .showLine(key + "_" + i, corners[edges[i][0]], corners[edges[i][1]])

                    .colored(color)

                    .disableLineNormals()

                    .lineWidth(thin ? 1.0F / 64.0F : 1.0F / 96.0F);
        }
    }

    private static void removeOutline() {

        for (int i = 0; i < 12; i++) {
            Outliner.getInstance().remove("cutaway_outline_" + i);
            Outliner.getInstance().remove("cutaway_hover_" + i);

            for (int selected = 0; selected < 3; selected++) {
                Outliner.getInstance().remove("cutaway_selected_" + selected + "_" + i);
            }
        }
    }
}
