package com.vardanrattan.echoes.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Stored echo recording metadata and frame data.
 */
public final class EchoRecord {

    private final UUID uuid;
    private final UUID playerUuid;
    private final String playerName;
    private final EchoEventType eventType;
    private final EchoTier tier;
    private final ResourceKey<Level> dimension;
    private final BlockPos anchorPos;
    private final long worldTimestamp;
    private final long realTimestamp;

    private final EquipmentSnapshot equipment;

    private final List<EchoFrame> frames;
    private final Set<UUID> seenBy;

    public EchoRecord(
            UUID uuid,
            UUID playerUuid,
            String playerName,
            EchoEventType eventType,
            EchoTier tier,
            ResourceKey<Level> dimension,
            BlockPos anchorPos,
            long worldTimestamp,
            long realTimestamp,
            EquipmentSnapshot equipment,
            List<EchoFrame> frames,
            Set<UUID> seenBy) {
        this.uuid = uuid == null ? UUID.randomUUID() : uuid;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.eventType = eventType;
        this.tier = tier;
        this.dimension = dimension;
        this.anchorPos = anchorPos.immutable();
        this.worldTimestamp = worldTimestamp;
        this.realTimestamp = realTimestamp;
        this.equipment = equipment;
        this.frames = frames == null ? new ArrayList<>() : new ArrayList<>(frames);
        this.seenBy = seenBy == null ? new HashSet<>() : new HashSet<>(seenBy);
    }

    public UUID getUUID() {
        return uuid;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public EchoEventType getEventType() {
        return eventType;
    }

    public EchoTier getTier() {
        return tier;
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public BlockPos getAnchorPos() {
        return anchorPos;
    }

    public long getWorldTimestamp() {
        return worldTimestamp;
    }

    public long getRealTimestamp() {
        return realTimestamp;
    }

    public EquipmentSnapshot getEquipment() {
        return equipment;
    }

    @Override
    public String toString() {
        return String.format("EchoRecord[uuid=%s, player=%s, event=%s, tier=%s, dim=%s, pos=%s, frames=%d]",
                uuid, playerName, eventType, tier, dimension.identifier(), anchorPos.toShortString(), frames.size());
    }

    public boolean isExpired(long nowMs, int decayDays) {
        if (decayDays < 0) return false;
        long ageMs = nowMs - realTimestamp;
        long ageDays = ageMs / (1000L * 60L * 60L * 24L);
        return ageDays > decayDays;
    }

    public List<EchoFrame> getFrames() {
        return Collections.unmodifiableList(frames);
    }

    public int getFrameCount() {
        return frames.size();
    }

    public void addFrame(EchoFrame frame, int maxFrames) {
        if (frame == null) {
            return;
        }
        if (frames.size() >= maxFrames) {
            return;
        }
        frames.add(frame);
    }

    public boolean hasBeenSeenBy(UUID player) {
        return player != null && seenBy.contains(player);
    }

    public void markSeen(UUID player) {
        if (player != null) {
            seenBy.add(player);
        }
    }

    public Set<UUID> getSeenBy() {
        return Collections.unmodifiableSet(seenBy);
    }
}
