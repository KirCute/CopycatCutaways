package top.kircute.copycatcutaways.placement;
import top.kircute.copycatcutaways.geometry.GridPoint;
import top.kircute.copycatcutaways.network.CommitCutPayload;
import top.kircute.copycatcutaways.compat.PlacementSpace;
import top.kircute.copycatcutaways.compat.PlacementSpaces;
import top.kircute.copycatcutaways.geometry.RayCubeIntersection;
import top.kircute.copycatcutaways.ModRegistry;
import top.kircute.copycatcutaways.geometry.CutPlane;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class ServerCutPlacement {

    private ServerCutPlacement() {
    }

    public static void handle(ServerPlayer player, CommitCutPayload payload) {

        BlockPos target = payload.target();

        if (!player.mayBuild()) {
            rejectPrediction(player, target);
            return;
        }

        InteractionHand hand = payload.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;

        ItemStack held = player.getItemInHand(hand);

        if (!ModRegistry.isCutawayItemForMode(held, payload.noCollision())) {
            rejectPrediction(player, target);
            return;
        }

        List<GridPoint> points = List.of(payload.first(), payload.second(), payload.third());

        if (points.stream().anyMatch(point -> !point.isOnCubeEdge())
                || points.stream().distinct().count() != 3) {
            rejectPrediction(player, target);
            return;
        }

        ServerLevel level = player.serverLevel();

        if (!level.isLoaded(target)) {
            return;
        }

        BlockState existing = level.getBlockState(target);
        if (!existing.canBeReplaced()) {
            rejectPrediction(player, target);
            return;
        }

        double reach = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE);

        PlacementSpace placementSpace = PlacementSpaces.forTarget(level, target);

        Vec3 eye = placementSpace.toLocalPosition(player.getEyePosition());
        Vec3 direction = placementSpace.toLocalDirection(player.getLookAngle());

        Vec3 hit = RayCubeIntersection.farHit(eye, direction, reach, target).orElse(null);

        if (hit == null || eye.distanceToSqr(Vec3.atCenterOf(target)) > (reach + 1.0) * (reach + 1.0)) {
            rejectPrediction(player, target);
            return;
        }

        Vec3 localHit = hit.subtract(target.getX(), target.getY(), target.getZ());

        if (points.stream().anyMatch(point -> point.distanceSqr(localHit) > 2.0)) {
            rejectPrediction(player, target);
            return;
        }

        CutPlane.Evaluation evaluation = CutPlane.evaluate(points, localHit);

        if (evaluation.result() == CutPlane.Result.AMBIGUOUS_SIDE) {
            rejectPrediction(player, target);
            return;
        }

        if (!CutPlacement.place(
                level,
                player,
                hand,
                held,
                target,
                hit,
                evaluation,
                points,
                payload.noCollision()
        )) {
            rejectPrediction(player, target);
        }
    }

    private static void rejectPrediction(ServerPlayer player, BlockPos target) {
        ServerLevel level = player.serverLevel();

        if (!level.isLoaded(target)) {
            return;
        }

        player.connection.send(new ClientboundBlockUpdatePacket(level, target));
    }
}
