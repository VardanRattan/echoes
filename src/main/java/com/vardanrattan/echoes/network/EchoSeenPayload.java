package com.vardanrattan.echoes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * Client → server payload: reports that a client has finished seeing an echo.
 *
 * Playback logic will send this after ghost completion so the server can update
 * seen-state and avoid re-triggering for that player.
 */
public record EchoSeenPayload(UUID echoId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EchoSeenPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("echoes", "seen"));

    public static final StreamCodec<FriendlyByteBuf, EchoSeenPayload> CODEC =
            StreamCodec.of(EchoSeenPayload::write, EchoSeenPayload::read);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    private static void write(FriendlyByteBuf buf, EchoSeenPayload payload) {
        buf.writeUUID(payload.echoId);
    }

    private static EchoSeenPayload read(FriendlyByteBuf buf) {
        return new EchoSeenPayload(buf.readUUID());
    }
}

