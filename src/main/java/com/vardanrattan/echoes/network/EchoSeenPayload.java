package com.vardanrattan.echoes.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Client → server payload: reports that a client has finished seeing an echo.
 *
 * Playback logic will send this after ghost completion so the server can update
 * seen-state and avoid re-triggering for that player.
 */
public record EchoSeenPayload(UUID echoId) implements CustomPayload {

    public static final CustomPayload.Id<EchoSeenPayload> ID =
            new CustomPayload.Id<>(Identifier.of("echoes", "seen"));

    public static final PacketCodec<RegistryByteBuf, EchoSeenPayload> CODEC =
            PacketCodec.ofStatic(EchoSeenPayload::write, EchoSeenPayload::read);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buf, EchoSeenPayload payload) {
        buf.writeUuid(payload.echoId);
    }

    private static EchoSeenPayload read(RegistryByteBuf buf) {
        return new EchoSeenPayload(buf.readUuid());
    }
}

