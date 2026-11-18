package top.kircute.copycatcutaways.client.selection;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import top.kircute.copycatcutaways.ModRegistry;
import top.kircute.copycatcutaways.config.ClientConfig;
import top.kircute.copycatcutaways.compat.PlacementSpace;
import top.kircute.copycatcutaways.compat.PlacementSpaces;
import top.kircute.copycatcutaways.geometry.CutPlane;
import top.kircute.copycatcutaways.geometry.GridPoint;
import top.kircute.copycatcutaways.geometry.RayCubeIntersection;
import top.kircute.copycatcutaways.network.CommitCutPayload;
import top.kircute.copycatcutaways.placement.CutPlacement;
import top.kircute.copycatcutaways.placement.SelectionGrid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class SelectionClient {

    public enum Phase {

        EDGE_POINTS,

        FACE_POINT
    }

    public static final class Session {

        private final BlockPos target;

        private final PlacementSpace placementSpace;

        private final boolean noCollision;

        private final List<GridPoint> selected = new ArrayList<>(3);

        @Nullable
        private GridPoint hovered;

        private Phase phase = Phase.EDGE_POINTS;

        private Session(BlockPos target, PlacementSpace placementSpace, boolean noCollision) {
            this.target = target;
            this.placementSpace = placementSpace;
            this.noCollision = noCollision;
        }

        public BlockPos target() {
            return target;
        }

        public PlacementSpace placementSpace() {
            return placementSpace;
        }

        public boolean noCollision() {
            return noCollision;
        }

        public List<GridPoint> selected() {
            return Collections.unmodifiableList(selected);
        }

        @Nullable
        public GridPoint hovered() {
            return hovered;
        }

    }

    public record FacePreview(BlockPos target, PlacementSpace placementSpace,
                              CutPlane.Evaluation evaluation) {
    }

    @Nullable
    private static Session session;

    @Nullable
    private static ClientLevel sessionLevel;

    private SelectionClient() {
    }

    public static InteractionResult onUseOn(UseOnContext context) {

        LocalPlayer player = Minecraft.getInstance().player;
        Level level = context.getLevel();

        if (player == null || !(level instanceof ClientLevel clientLevel)) {
            return InteractionResult.PASS;
        }

        ItemStack usedStack = context.getItemInHand();
        if (!ModRegistry.isCutawayItem(usedStack)) {
            return InteractionResult.PASS;
        }

        boolean noCollision = usedStack.is(ModRegistry.GHOST_CUTAWAY_ITEM.get());

        if (session != null
                && sessionLevel == clientLevel
                && session.noCollision != noCollision) {
            clear();
        }

        if (session == null || sessionLevel != clientLevel) {

            BlockPlaceContext placementContext = new BlockPlaceContext(context);
            BlockPos target = placementContext.getClickedPos();

            if (!level.getBlockState(target).canBeReplaced(placementContext)) {
                return InteractionResult.PASS;
            }

            PlacementSpace placementSpace = PlacementSpaces.forTarget(clientLevel, target);

            session = new Session(target, placementSpace, noCollision);
            sessionLevel = clientLevel;

            updateHover(player, session);
            return InteractionResult.SUCCESS;
        }

        click(player, clientLevel, context.getHand());
        return InteractionResult.SUCCESS;
    }

    public static void onUseAir(Level level, Player player, InteractionHand hand) {

        if (!(level instanceof ClientLevel clientLevel)
                || !(player instanceof LocalPlayer localPlayer)
                || session == null
                || sessionLevel != clientLevel) {
            return;
        }

        click(localPlayer, clientLevel, hand);
    }

    public static void clientTick() {

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (session == null || player == null || minecraft.level == null) {
            return;
        }

        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();

        if (sessionLevel != minecraft.level
                || !PlacementSpaces.isCurrent(session.placementSpace, minecraft.level, session.target)
                || (!ModRegistry.isCutawayItemForMode(main, session.noCollision)
                && !ModRegistry.isCutawayItemForMode(off, session.noCollision))) {
            clear();
            return;
        }
        updateHover(player, session);
    }

    public static void updateHover(LocalPlayer player, Session activeSession) {

        if (activeSession.phase != Phase.EDGE_POINTS) {
            activeSession.hovered = null;
            return;
        }

        Vec3 hit = rayHit(player, activeSession).orElse(null);

        if (hit == null) {
            activeSession.hovered = null;
            return;
        }

        Vec3 local = hit.subtract(activeSession.target.getX(), activeSession.target.getY(), activeSession.target.getZ());

        activeSession.hovered = SelectionGrid.nearest(
                local,
                activeSession.selected,
                ClientConfig.placementSegments()
        );
    }

    private static void click(LocalPlayer player, ClientLevel level, InteractionHand hand) {

        if (session == null || sessionLevel != level) {
            return;
        }

        Vec3 hit = rayHit(player, session).orElse(null);

        if (hit == null) {
            clear();
            return;
        }

        Vec3 local = hit.subtract(session.target.getX(), session.target.getY(), session.target.getZ());

        if (session.phase == Phase.EDGE_POINTS) {

            GridPoint nearest = SelectionGrid.nearest(
                    local,
                    session.selected,
                    ClientConfig.placementSegments()
            );

            if (nearest == null) {
                clear();
                return;
            }

            session.selected.add(nearest);

            if (session.selected.size() == 3) {
                session.phase = Phase.FACE_POINT;
                session.hovered = null;
            } else {

                updateHover(player, session);
            }
            return;
        }

        CutPlane.Evaluation evaluation = CutPlane.evaluate(session.selected, local);

        if (evaluation.result() == CutPlane.Result.AMBIGUOUS_SIDE) {
            return;
        }

        ItemStack held = player.getItemInHand(hand);
        if (!ModRegistry.isCutawayItemForMode(held, session.noCollision)) {
            clear();
            return;
        }

        if (!CutPlacement.place(
                level,
                player,
                hand,
                held,
                session.target,
                hit,
                evaluation,
                session.selected,
                session.noCollision
        )) {

            clear();
            return;
        }

        PacketDistributor.sendToServer(new CommitCutPayload(
                session.target,
                session.selected.get(0),
                session.selected.get(1),
                session.selected.get(2),
                hand == InteractionHand.OFF_HAND,
                session.noCollision
        ));

        clear();
    }

    private static Optional<Vec3> rayHit(LocalPlayer player, Session activeSession) {

        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);

        Vec3 localEye = activeSession.placementSpace.toLocalPosition(player.getEyePosition());
        Vec3 localDirection = activeSession.placementSpace.toLocalDirection(player.getLookAngle());

        return RayCubeIntersection.farHit(localEye, localDirection, reach, activeSession.target);
    }

    private static void clear() {
        session = null;
        sessionLevel = null;
    }

    @Nullable
    public static FacePreview facePreview() {

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || session == null || session.phase != Phase.FACE_POINT) {
            return null;
        }

        Vec3 hit = rayHit(player, session).orElse(null);
        if (hit == null) {
            return null;
        }

        Vec3 local = hit.subtract(session.target.getX(), session.target.getY(), session.target.getZ());
        return new FacePreview(
                session.target,
                session.placementSpace,
                CutPlane.evaluate(session.selected, local)
        );
    }

    @Nullable
    public static Session session() {
        return session;
    }
}
