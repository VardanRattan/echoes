package com.vardanrattan.echoes.network;

import com.vardanrattan.echoes.data.EchoEventType;
import com.vardanrattan.echoes.data.EchoFrame;
import com.vardanrattan.echoes.data.EchoTier;
import com.vardanrattan.echoes.data.EquipmentSnapshot;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server → client payload: instructs the client to play back a specific echo.
 *
 * Uses the 1.21.11 CustomPayload + PacketCodec API.
 */
public record EchoPlaybackPayload(
        UUID echoId,
        RegistryKey<World> dimension,
        BlockPos anchorPos,
        EchoTier tier,
        EchoEventType eventType,
        UUID playerUuid,
        String playerName,
        long realTimestamp,
        EquipmentSnapshot equipment,
        List<EchoFrame> frames
) implements CustomPayload {

    public static final CustomPayload.Id<EchoPlaybackPayload> ID =
            new CustomPayload.Id<>(Identifier.of("echoes", "playback"));

    public static final PacketCodec<RegistryByteBuf, EchoPlaybackPayload> CODEC =
            PacketCodec.ofStatic(EchoPlaybackPayload::write, EchoPlaybackPayload::read);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buf, EchoPlaybackPayload payload) {
        buf.writeUuid(payload.echoId);

        buf.writeIdentifier(payload.dimension.getValue());
        buf.writeBlockPos(payload.anchorPos);

        buf.writeString(payload.tier.name());
        buf.writeString(payload.eventType.name());

        buf.writeUuid(payload.playerUuid);
        buf.writeString(payload.playerName);
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
            buf.writeString(frame.getAnimationState().name());
            buf.writeVarInt(frame.getTickOffset());
        }
    }

    private static EchoPlaybackPayload read(RegistryByteBuf buf) {
        UUID echoId = buf.readUuid();

        Identifier dimId = buf.readIdentifier();
        RegistryKey<World> dimensionKey = RegistryKey.of(RegistryKeys.WORLD, dimId);
        BlockPos anchorPos = buf.readBlockPos();

        EchoTier tier = EchoTier.valueOf(buf.readString());
        EchoEventType eventType = EchoEventType.valueOf(buf.readString());

        UUID playerUuid = buf.readUuid();
        String playerName = buf.readString();
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
            var animState = com.vardanrattan.echoes.data.EchoAnimState.valueOf(buf.readString());
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

