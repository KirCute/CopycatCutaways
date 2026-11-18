package top.kircute.copycatcutaways.client.render;

import top.kircute.copycatcutaways.client.model.CutQuadClipper;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.render.DefaultSuperRenderTypeBuffer;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import top.kircute.copycatcutaways.client.selection.SelectionClient;
import top.kircute.copycatcutaways.compat.PlacementSpace;
import top.kircute.copycatcutaways.geometry.CutPlane;

import java.util.ArrayList;
import java.util.List;

public final class PreviewRenderer {

    private static final double EPSILON = 1.0E-7;

    private static final Vec3[][] CUBE_FACES = {
            {new Vec3(0, 0, 0), new Vec3(0, 1, 0), new Vec3(1, 1, 0), new Vec3(1, 0, 0)},
            {new Vec3(0, 0, 1), new Vec3(1, 0, 1), new Vec3(1, 1, 1), new Vec3(0, 1, 1)},
            {new Vec3(0, 1, 0), new Vec3(0, 1, 1), new Vec3(1, 1, 1), new Vec3(1, 1, 0)},
            {new Vec3(0, 0, 0), new Vec3(1, 0, 0), new Vec3(1, 0, 1), new Vec3(0, 0, 1)},
            {new Vec3(0, 0, 0), new Vec3(0, 0, 1), new Vec3(0, 1, 1), new Vec3(0, 1, 0)},
            {new Vec3(1, 0, 0), new Vec3(1, 1, 0), new Vec3(1, 1, 1), new Vec3(1, 0, 1)}
    };

    private PreviewRenderer() {
    }

    public static void render(PoseStack poseStack) {

        SelectionClient.FacePreview preview = SelectionClient.facePreview();
        if (preview == null || preview.evaluation().result() == CutPlane.Result.AMBIGUOUS_SIDE) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();

        SuperRenderTypeBuffer buffers = DefaultSuperRenderTypeBuffer.getInstance();

        VertexConsumer consumer = buffers.getBuffer(RenderType.debugQuads());

        poseStack.pushPose();

        Matrix4f matrix = poseStack.last().pose();

        float alpha = 0.22F + (float) ((Math.sin(System.currentTimeMillis() / 260.0) + 1.0) * 0.06);

        if (preview.evaluation().isValid()) {

            CutPlane plane = preview.evaluation().plane();
            for (Vec3[] face : CUBE_FACES) {

                emitPolygon(
                        consumer,
                        matrix,
                        preview.target(),
                        preview.placementSpace(),
                        camera,
                        clip(List.of(face), plane),
                        alpha
                );
            }

            emitPolygon(
                    consumer,
                    matrix,
                    preview.target(),
                    preview.placementSpace(),
                    camera,
                    CutQuadClipper.capPolygon(plane),
                    alpha
            );

        } else {
            for (Vec3[] face : CUBE_FACES) {

                emitPolygon(
                        consumer,
                        matrix,
                        preview.target(),
                        preview.placementSpace(),
                        camera,
                        List.of(face),
                        alpha
                );
            }
        }

        poseStack.popPose();
        buffers.draw();

        RenderSystem.enableCull();
    }

    private static List<Vec3> clip(List<Vec3> input, CutPlane plane) {

        List<Vec3> output = new ArrayList<>(6);

        for (int i = 0; i < input.size(); i++) {
            Vec3 current = input.get(i);
            Vec3 next = input.get((i + 1) % input.size());

            double currentDistance = plane.signedDistance(current);
            double nextDistance = plane.signedDistance(next);

            boolean currentInside = currentDistance >= -EPSILON;
            boolean nextInside = nextDistance >= -EPSILON;

            if (currentInside) {
                output.add(current);
            }

            if (currentInside != nextInside) {
                output.add(current.lerp(next, currentDistance / (currentDistance - nextDistance)));
            }
        }
        return output;
    }

    private static void emitPolygon(VertexConsumer consumer, Matrix4f matrix,
                                    net.minecraft.core.BlockPos target, PlacementSpace space,
                                    Vec3 camera, List<Vec3> polygon, float alpha) {

        if (polygon.size() < 3) {
            return;
        }

        for (int i = 1; i + 1 < polygon.size(); i++) {

            emitVertex(consumer, matrix, target, space, camera, polygon.get(0), alpha);
            emitVertex(consumer, matrix, target, space, camera, polygon.get(i), alpha);
            emitVertex(consumer, matrix, target, space, camera, polygon.get(i + 1), alpha);

            emitVertex(consumer, matrix, target, space, camera, polygon.get(i + 1), alpha);
            emitVertex(consumer, matrix, target, space, camera, polygon.get(i), alpha);

            emitVertex(consumer, matrix, target, space, camera, polygon.get(0), alpha);
        }
    }

    private static void emitVertex(VertexConsumer consumer, Matrix4f matrix,
                                   net.minecraft.core.BlockPos target, PlacementSpace space,
                                   Vec3 camera, Vec3 point, float alpha) {

        Vec3 local = point.add(target.getX(), target.getY(), target.getZ());

        Vec3 world = space.toWorldPosition(local);

        Vec3 relative = world.subtract(camera);
        consumer.addVertex(matrix, (float) relative.x, (float) relative.y, (float) relative.z)

                .setColor(1.0F, 0.88F, 0.12F, alpha);
    }
}
