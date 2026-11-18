package top.kircute.copycatcutaways.network;
import top.kircute.copycatcutaways.geometry.GridPoint;
import top.kircute.copycatcutaways.CopycatCutaways;
import top.kircute.copycatcutaways.placement.ServerCutPlacement;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.NotNull;

public record CommitCutPayload(BlockPos target, GridPoint first, GridPoint second,
                               GridPoint third, boolean offhand,
                               boolean noCollision) implements CustomPacketPayload {

    public static final Type<CommitCutPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CopycatCutaways.MODID, "commit_cut"));

    public static final StreamCodec<ByteBuf, CommitCutPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CommitCutPayload::target,
            GridPoint.STREAM_CODEC, CommitCutPayload::first,
            GridPoint.STREAM_CODEC, CommitCutPayload::second,
            GridPoint.STREAM_CODEC, CommitCutPayload::third,
            ByteBufCodecs.BOOL, CommitCutPayload::offhand,
            ByteBufCodecs.BOOL, CommitCutPayload::noCollision,
            CommitCutPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {

        PayloadRegistrar registrar = event.registrar("2");

        registrar.playToServer(TYPE, STREAM_CODEC, CommitCutPayload::handle);
    }

    private static void handle(CommitCutPayload payload, IPayloadContext context) {

        context.enqueueWork(() -> {

            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {

                ServerCutPlacement.handle(player, payload);
            }
        });
    }
}
