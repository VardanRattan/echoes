package com.vardanrattan.echoes.network;

import com.vardanrattan.echoes.data.EchoEventType;
import com.vardanrattan.echoes.data.EchoTier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server → client payload sent when a player uses an Echo Crystal.
 * Contains a list of nearby echoes to be highlighted locally.
 */
public record EchoSensePayload(List<SenseEntry> nearbyEchoes) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EchoSensePayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("echoes", "sense"));

    public static final StreamCodec<FriendlyByteBuf, EchoSensePayload> CODEC =
    StreamCodec.of(EchoSensePayload::write, EchoSensePayload::read);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    private static void write(FriendlyByteBuf buf, EchoSensePayload payload) {
        buf.writeVarInt(payload.nearbyEchoes.size());
        for (SenseEntry entry : payload.nearbyEchoes) {
            buf.writeUUID(entry.echoId());
            buf.writeBlockPos(entry.pos());
            buf.writeUtf(entry.tier().name());
            buf.writeUtf(entry.eventType().name());
            buf.writeUtf(entry.playerName());
            buf.writeLong(entry.timestamp());
        }
    }

    private static EchoSensePayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<SenseEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new SenseEntry(
                    buf.readUUID(),
                    buf.readBlockPos(),
                    EchoTier.valueOf(buf.readUtf()),
                    EchoEventType.valueOf(buf.readUtf()),
                    buf.readUtf(),
                    buf.readLong()
            ));
        }
        return new EchoSensePayload(entries);
    }

    public record SenseEntry(UUID echoId, BlockPos pos, EchoTier tier, EchoEventType eventType, String playerName, long timestamp) {}
}
