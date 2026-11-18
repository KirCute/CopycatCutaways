package top.kircute.copycatcutaways.geometry;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

public record GridPoint(int x, int y, int z) {

    public static final int MAX = 16;

    public static final StreamCodec<ByteBuf, GridPoint> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GridPoint::x,
            ByteBufCodecs.VAR_INT, GridPoint::y,
            ByteBufCodecs.VAR_INT, GridPoint::z,
            GridPoint::new
    );

    public boolean isOnCubeEdge() {

        if (x < 0 || y < 0 || z < 0 || x > MAX || y > MAX || z > MAX) {
            return false;
        }

        int boundaries = (x == 0 || x == MAX ? 1 : 0)
                + (y == 0 || y == MAX ? 1 : 0)
                + (z == 0 || z == MAX ? 1 : 0);

        return boundaries >= 2;
    }

    public Vec3 center() {
        return new Vec3(x / (double) MAX, y / (double) MAX, z / (double) MAX);
    }

    public double distanceSqr(Vec3 point) {

        double dx = point.x - x / (double) MAX;
        double dy = point.y - y / (double) MAX;
        double dz = point.z - z / (double) MAX;

        return dx * dx + dy * dy + dz * dz;
    }

    public GridPoint snapForCollision() {
        return new GridPoint(

                x + (x & 1),

                y + (y & 1),

                z + (z & 1)
        );
    }
}
