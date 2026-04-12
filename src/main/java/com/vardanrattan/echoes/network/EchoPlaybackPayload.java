package com.vardanrattan.echoes.network;

import com.vardanrattan.echoes.data.EchoEventType;
import com.vardanrattan.echoes.data.EchoFrame;
import com.vardanrattan.echoes.data.EchoTier;
import com.vardanrattan.echoes.data.EquipmentSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server → client payload: instructs the client to play back a specific echo.
 *
 * Uses the 1.21.11 CustomPacketPayload + PacketCodec API.
 */
public record EchoPlaybackPayload(
        UUID echoId,
        ResourceKey<Level> dimension,
        BlockPos anchorPos,
        EchoTier tier,
        EchoEventType eventType,
        UUID playerUuid,
        String playerName,
        long realTimestamp,
        EquipmentSnapshot equipment,
        List<EchoFrame> frames
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EchoPlaybackPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("echoes", "playback"));

    public static final StreamCodec<FriendlyByteBuf, EchoPlaybackPayload> CODEC =
        StreamCodec.of(EchoPlaybackPayload::write, EchoPlaybackPayload::read);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    private static void write(FriendlyByteBuf buf, EchoPlaybackPayload payload) {
        buf.writeUUID(payload.echoId);

        buf.writeIdentifier(payload.dimension.identifier());
        buf.writeBlockPos(payload.anchorPos);

        buf.writeUtf(payload.tier.name());
        buf.writeUtf(payload.eventType.name());

        buf.writeUUID(payload.playerUuid);
        buf.writeUtf(payload.playerName);
        buf.writeLong(payload.realTimestamp);

        // Equipment snapshot (may be null for very old echoes).
        if (payload.equipment != null) {
            buf.writeBoolean(true);
            buf.writeNbt(payload.equipment.toNbt());
        } else {
            buf.writeBoolean(false);
        }

        // Frames
        List<EchoFrame> frames = payload.frames;
        buf.writeVarInt(frames.size());
        for (EchoFrame frame : frames) {
            buf.writeFloat(frame.getRelX());
            buf.writeFloat(frame.getRelY());
            buf.writeFloat(frame.getRelZ());
            buf.writeFloat(frame.getYaw());
            buf.writeFloat(frame.getPitch());
            buf.writeFloat(frame.getLimbSwing());
            buf.writeUtf(frame.getAnimationState().name());
            buf.writeVarInt(frame.getTickOffset());
        }
    }

    private static EchoPlaybackPayload read(FriendlyByteBuf buf) {
        UUID echoId = buf.readUUID();

        Identifier dimId = buf.readIdentifier();
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimId);
        BlockPos anchorPos = buf.readBlockPos();

        EchoTier tier = EchoTier.valueOf(buf.readUtf());
        EchoEventType eventType = EchoEventType.valueOf(buf.readUtf());

        UUID playerUuid = buf.readUUID();
        String playerName = buf.readUtf();
        long realTimestamp = buf.readLong();

        EquipmentSnapshot equipment = null;
        if (buf.readBoolean()) {
            var nbt = buf.readNbt();
            equipment = EquipmentSnapshot.fromNbt(nbt);
        }

        int frameCount = buf.readVarInt();
        List<EchoFrame> frames = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            float relX = buf.readFloat();
            float relY = buf.readFloat();
            float relZ = buf.readFloat();
            float yaw = buf.readFloat();
            float pitch = buf.readFloat();
            float limbSwing = buf.readFloat();
            var animState = com.vardanrattan.echoes.data.EchoAnimState.valueOf(buf.readUtf());
            int tickOffset = buf.readVarInt();
            frames.add(new EchoFrame(relX, relY, relZ, yaw, pitch, limbSwing, animState, tickOffset));
        }

        return new EchoPlaybackPayload(
                echoId,
                dimensionKey,
                anchorPos,
                tier,
                eventType,
                playerUuid,
                playerName,
                realTimestamp,
                equipment,
                frames
        );
    }
}

