package com.vardanrattan.echoes.network;

import com.vardanrattan.echoes.data.EchoEventType;
import com.vardanrattan.echoes.data.EchoTier;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server → client payload sent when a player uses an Echo Crystal.
 * Contains a list of nearby echoes to be highlighted locally.
 */
public record EchoSensePayload(List<SenseEntry> nearbyEchoes) implements CustomPayload {

    public static final CustomPayload.Id<EchoSensePayload> ID =
            new CustomPayload.Id<>(Identifier.of("echoes", "sense"));

    public static final PacketCodec<RegistryByteBuf, EchoSensePayload> CODEC =
            PacketCodec.ofStatic(EchoSensePayload::write, EchoSensePayload::read);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    private static void write(RegistryByteBuf buf, EchoSensePayload payload) {
        buf.writeVarInt(payload.nearbyEchoes.size());
        for (SenseEntry entry : payload.nearbyEchoes) {
            buf.writeUuid(entry.echoId());
            buf.writeBlockPos(entry.pos());
            buf.writeString(entry.tier().name());
            buf.writeString(entry.eventType().name());
            buf.writeString(entry.playerName());
            buf.writeLong(entry.timestamp());
        }
    }

    private static EchoSensePayload read(RegistryByteBuf buf) {
        int size = buf.readVarInt();
        List<SenseEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new SenseEntry(
                    buf.readUuid(),
                    buf.readBlockPos(),
                    EchoTier.valueOf(buf.readString()),
                    EchoEventType.valueOf(buf.readString()),
                    buf.readString(),
                    buf.readLong()
            ));
        }
        return new EchoSensePayload(entries);
    }

    public record SenseEntry(UUID echoId, BlockPos pos, EchoTier tier, EchoEventType eventType, String playerName, long timestamp) {}
}
