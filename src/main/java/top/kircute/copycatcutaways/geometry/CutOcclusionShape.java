package top.kircute.copycatcutaways.geometry;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CutOcclusionShape {

    private static final double EPSILON = 1.0E-7;

    private static final double FACE_THICKNESS = 1.0E-4;

    private CutOcclusionShape() {
    }

    public static VoxelShape create(CutPlane plane) {

        VoxelShape shape = Shapes.empty();

        for (Direction face : Direction.values()) {
            shape = Shapes.or(shape, createFace(plane, face));
        }
        return shape;
    }

    private static VoxelShape createFace(CutPlane plane, Direction face) {
        return faceFullyRetained(plane, face) ? faceSlab(face) : Shapes.empty();
    }

    private static boolean faceFullyRetained(CutPlane plane, Direction face) {
        for (Vec3 point : faceCorners(face)) {

            if (plane.signedDistance(point) < -EPSILON) {
                return false;
            }
        }

        return true;
    }

    private static Vec3[] faceCorners(Direction face) {
        return switch (face) {
            case UP -> new Vec3[]{
                    new Vec3(0, 1, 0), new Vec3(1, 1, 0),
                    new Vec3(1, 1, 1), new Vec3(0, 1, 1)
            };
            case DOWN -> new Vec3[]{
                    new Vec3(0, 0, 0), new Vec3(0, 0, 1),
                    new Vec3(1, 0, 1), new Vec3(1, 0, 0)
            };
            case NORTH -> new Vec3[]{
                    new Vec3(0, 0, 0), new Vec3(1, 0, 0),
                    new Vec3(1, 1, 0), new Vec3(0, 1, 0)
            };
            case SOUTH -> new Vec3[]{
                    new Vec3(0, 0, 1), new Vec3(0, 1, 1),
                    new Vec3(1, 1, 1), new Vec3(1, 0, 1)
            };
            case WEST -> new Vec3[]{
                    new Vec3(0, 0, 0), new Vec3(0, 1, 0),
                    new Vec3(0, 1, 1), new Vec3(0, 0, 1)
            };
            case EAST -> new Vec3[]{
                    new Vec3(1, 0, 0), new Vec3(1, 0, 1),
                    new Vec3(1, 1, 1), new Vec3(1, 1, 0)
            };
        };
    }

    private static VoxelShape faceSlab(Direction face) {
        return switch (face) {

            case UP -> Shapes.box(0, 1 - FACE_THICKNESS, 0, 1, 1, 1);

            case DOWN -> Shapes.box(0, 0, 0, 1, FACE_THICKNESS, 1);

            case NORTH -> Shapes.box(0, 0, 0, 1, 1, FACE_THICKNESS);

            case SOUTH -> Shapes.box(0, 0, 1 - FACE_THICKNESS, 1, 1, 1);

            case WEST -> Shapes.box(0, 0, 0, FACE_THICKNESS, 1, 1);

            case EAST -> Shapes.box(1 - FACE_THICKNESS, 0, 0, 1, 1, 1);
        };
    }
}
