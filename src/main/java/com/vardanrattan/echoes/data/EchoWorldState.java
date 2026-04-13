package com.vardanrattan.echoes.data;

import com.vardanrattan.echoes.Echoes;
import com.vardanrattan.echoes.config.EchoesConfig;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;
import java.util.function.Consumer;

/**
 * Per-world persistent state for all echoes and per-player echo data.
 *
 * This class is intentionally self-contained and only depends on core
 * Minecraft types plus the Echoes data classes, so upgrades to Fabric or
 * the rendering stack should not require touching this file.
 */
public final class EchoWorldState extends SavedData {

    public static final String NAME = Echoes.MOD_ID + "_world";

    public static final Codec<EchoWorldState> CODEC = CompoundTag.CODEC.xmap(
            EchoWorldState::fromTag,
            state -> state.save(new CompoundTag()));

    public static final SavedDataType<EchoWorldState> TYPE = new SavedDataType<>(
            Identifier.tryParse(Echoes.MOD_ID + ":echoes_world"),
            EchoWorldState::new,
            EchoWorldState.CODEC,
            DataFixTypes.LEVEL);

    private static final String TAG_CHUNK_ECHOS = "chunkEchoMap";
    private static final String TAG_PLAYER_DATA = "playerData";

    private static final String TAG_CHUNK_X = "chunkX";
    private static final String TAG_CHUNK_Z = "chunkZ";
    private static final String TAG_ECHOS = "echos";

    private static final String TAG_ECHO_UUID = "uuid";
    private static final String TAG_ECHO_PLAYER_UUID = "playerUUID";
    private static final String TAG_ECHO_PLAYER_NAME = "playerName";
    private static final String TAG_ECHO_EVENT_TYPE = "eventType";
    private static final String TAG_ECHO_TIER = "tier";
    private static final String TAG_ECHO_DIMENSION = "dimension";
    private static final String TAG_ECHO_ANCHOR_X = "anchorX";
    private static final String TAG_ECHO_ANCHOR_Y = "anchorY";
    private static final String TAG_ECHO_ANCHOR_Z = "anchorZ";
    private static final String TAG_ECHO_WORLD_TIME = "worldTime";
    private static final String TAG_ECHO_REAL_TIME = "realTime";
    private static final String TAG_ECHO_EQUIPMENT = "equipment";
    private static final String TAG_ECHO_FRAMES = "frames";
    private static final String TAG_ECHO_SEEN_BY = "seenBy";
    private static final String TAG_LAST_DECAY_CHECK_MS = "lastDecayCheckMs";

    private static final String TAG_FRAME_REL_X = "relX";
    private static final String TAG_FRAME_REL_Y = "relY";
    private static final String TAG_FRAME_REL_Z = "relZ";
    private static final String TAG_FRAME_YAW = "yaw";
    private static final String TAG_FRAME_PITCH = "pitch";
    private static final String TAG_FRAME_LIMB_SWING = "limbSwing";
    private static final String TAG_FRAME_ANIM_STATE = "animState";
    private static final String TAG_FRAME_TICK_OFFSET = "tickOffset";

    private static final String TAG_PLAYER_UUID = "playerUUID";
    private static final String TAG_PLAYER_BIOMES = "discoveredBiomes";
    private static final String TAG_PLAYER_STRUCTURES = "discoveredStructures";
    private static final String TAG_PLAYER_DIMENSIONS = "visitedDimensions";
    private static final String TAG_PLAYER_MILESTONES = "craftedMilestones";
    private static final String TAG_PLAYER_SEEN = "seenEchos";
    private static final String TAG_PLAYER_OPTED_OUT = "optedOut";
    private static final String TAG_PLAYER_DISPLAY_OPTED_OUT = "displayOptedOut";
    private static final String TAG_PLAYER_SESSION_ORIGIN = "sessionOrigin";
    private static final String TAG_PLAYER_SESSION_DISTANCE = "sessionDistanceTraveled";

    private static final String TAG_BIOME_ID = "biomeId";
    private static final String TAG_STRUCTURE_ID = "structureId";
    private static final String TAG_POS_X = "x";
    private static final String TAG_POS_Y = "y";
    private static final String TAG_POS_Z = "z";
    private static final String TAG_WORLD_FIRSTS = "worldFirstsClaimed";
    private static final String TAG_DATA_VERSION = "dataVersion";

    private static final int CURRENT_DATA_VERSION = 1;

    private final Map<ChunkPos, List<EchoRecord>> chunkEchoMap = new HashMap<>();
    private final Map<UUID, PlayerEchoData> playerData = new HashMap<>();
    private final Set<String> worldFirstsClaimed = new HashSet<>();
    private long lastDecayCheckMs;

    public EchoWorldState() {
    }

    public static EchoWorldState get(ServerLevel world) {
        return world.getDataStorage().computeIfAbsent(TYPE);
    }

    public static EchoWorldState fromTag(CompoundTag tag) {
        EchoWorldState state = new EchoWorldState();

        int version = tag.getInt(TAG_DATA_VERSION).orElse(0);

        Tag chunkEchoesTag = tag.get(TAG_CHUNK_ECHOS);
        state.readChunkEchoes(chunkEchoesTag instanceof ListTag ? (ListTag) chunkEchoesTag : new ListTag());

        Tag playerDataTag = tag.get(TAG_PLAYER_DATA);
        state.readPlayerData(playerDataTag instanceof ListTag ? (ListTag) playerDataTag : new ListTag());

        state.lastDecayCheckMs = tag.getLong(TAG_LAST_DECAY_CHECK_MS).orElse(0L);

        Tag firstsTag = tag.get(TAG_WORLD_FIRSTS);
        ListTag firsts = firstsTag instanceof ListTag ? (ListTag) firstsTag : new ListTag();

        for (int i = 0; i < firsts.size(); i++) {
            Tag element = firsts.get(i);
            if (element != null) {
                String first = element.asString().orElse("");
                if (!first.isEmpty()) {
                    state.worldFirstsClaimed.add(first);
                }
            }
        }

        return state;
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putInt(TAG_DATA_VERSION, CURRENT_DATA_VERSION);
        tag.put(TAG_CHUNK_ECHOS, writeChunkEchoes());
        tag.put(TAG_PLAYER_DATA, writePlayerData());
        tag.putLong(TAG_LAST_DECAY_CHECK_MS, lastDecayCheckMs);

        ListTag firsts = new ListTag();
        for (String first : worldFirstsClaimed) {
            firsts.add(StringTag.valueOf(first));
        }
        tag.put(TAG_WORLD_FIRSTS, firsts);

        return tag;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void addEcho(EchoRecord echo) {
        if (echo == null) {
            return;
        }
        if (echo.getAnchorPos() == null || echo.getDimension() == null) {
            return;
        }

        EchoesConfig cfg = EchoesConfig.get();
        ChunkPos chunkPos = new ChunkPos(echo.getAnchorPos().getX() >> 4, echo.getAnchorPos().getZ() >> 4);
        List<EchoRecord> chunkList = chunkEchoMap.computeIfAbsent(chunkPos, key -> new ArrayList<>());

        // Enforce per-chunk cap
        int maxPerChunk = cfg.getMaxEchoesPerChunk();
        if (maxPerChunk > 0 && chunkList.size() >= maxPerChunk) {
            EchoRecord victim = findEvictionCandidate(chunkList, echo.getTier());
            if (victim == null) {
                // No suitable victim (all higher tier); drop incoming echo.
                return;
            }
            chunkList.remove(victim);
        }

        // Enforce per-player cap
        int maxPerPlayer = cfg.getMaxEchoesPerPlayer();
        UUID playerId = echo.getPlayerUuid();
        if (maxPerPlayer > 0 && playerId != null) {
            int playerCount = countEchoesForPlayer(playerId);
            if (playerCount >= maxPerPlayer) {
                EchoRecord victim = findEvictionCandidateForPlayer(playerId, echo.getTier());
                if (victim == null) {
                    return;
                }
                removeEcho(victim.getUUID());
            }
        }

        // Enforce global cap
        int maxGlobal = cfg.getMaxEchoesGlobal();
        if (maxGlobal > 0) {
            int total = totalEchoCount();
            if (total >= maxGlobal) {
                EchoRecord victim = findGlobalEvictionCandidate(echo.getTier());
                if (victim == null) {
                    return;
                }
                removeEcho(victim.getUUID());
            }
        }

        chunkList.add(echo);
        setDirty();
    }

    public void removeEcho(UUID echoUuid) {
        if (echoUuid == null) {
            return;
        }
        boolean changed = false;
        for (List<EchoRecord> list : chunkEchoMap.values()) {
            changed |= list.removeIf(e -> echoUuid.equals(e.getUUID()));
        }
        if (changed) {
            setDirty();
        }
    }

    public void purgeEchoesByPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        boolean changed = false;
        for (List<EchoRecord> list : chunkEchoMap.values()) {
            changed |= list.removeIf(e -> playerUuid.equals(e.getPlayerUuid()));
        }
        if (changed) {
            setDirty();
        }
    }

    /**
     * Marks an echo as seen for a player (both per-echo and per-player tracking).
     * Returns true if the echo was found and updated.
     */
    public boolean markEchoSeen(UUID echoUuid, UUID playerUuid) {
        if (echoUuid == null || playerUuid == null) {
            return false;
        }

        EchoRecord record = getEchoById(echoUuid);
        if (record == null) {
            return false;
        }
        record.markSeen(playerUuid);
        getOrCreatePlayerData(playerUuid).markEchoSeen(echoUuid);
        setDirty();
        return true;
    }

    public boolean claimWorldFirst(String eventKey) {
        if (worldFirstsClaimed.contains(eventKey)) {
            return false;
        }
        worldFirstsClaimed.add(eventKey);
        setDirty();
        return true;
    }

    public EchoRecord getEchoById(UUID echoUuid) {
        if (echoUuid == null)
            return null;
        for (List<EchoRecord> list : chunkEchoMap.values()) {
            for (EchoRecord record : list) {
                if (echoUuid.equals(record.getUUID())) {
                    return record;
                }
            }
        }
        return null;
    }

    private int totalEchoCount() {
        int total = 0;
        for (List<EchoRecord> list : chunkEchoMap.values()) {
            total += list.size();
        }
        return total;
    }

    private int countEchoesForPlayer(UUID playerUuid) {
        if (playerUuid == null)
            return 0;
        int count = 0;
        for (List<EchoRecord> list : chunkEchoMap.values()) {
            for (EchoRecord record : list) {
                if (playerUuid.equals(record.getPlayerUuid())) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int tierRank(EchoTier tier) {
        return switch (tier) {
            case WHISPER -> 0;
            case MARK -> 1;
            case SCAR -> 2;
            case WORLD_FIRST -> 3;
        };
    }

    private EchoRecord findEvictionCandidate(List<EchoRecord> list, EchoTier incomingTier) {
        int incomingRank = tierRank(incomingTier);
        EchoRecord oldest = null;
        for (EchoRecord record : list) {
            if (tierRank(record.getTier()) > incomingRank) {
                continue;
            }
            if (oldest == null || record.getWorldTimestamp() < oldest.getWorldTimestamp()) {
                oldest = record;
            }
        }
        return oldest;
    }

    private EchoRecord findEvictionCandidateForPlayer(UUID playerUuid, EchoTier incomingTier) {
        int incomingRank = tierRank(incomingTier);
        EchoRecord oldest = null;
        for (List<EchoRecord> list : chunkEchoMap.values()) {
            for (EchoRecord record : list) {
                if (!playerUuid.equals(record.getPlayerUuid()))
                    continue;
                if (tierRank(record.getTier()) > incomingRank)
                    continue;
                if (oldest == null || record.getWorldTimestamp() < oldest.getWorldTimestamp()) {
                    oldest = record;
                }
            }
        }
        return oldest;
    }

    private EchoRecord findGlobalEvictionCandidate(EchoTier incomingTier) {
        int incomingRank = tierRank(incomingTier);
        EchoRecord oldest = null;
        for (List<EchoRecord> list : chunkEchoMap.values()) {
            for (EchoRecord record : list) {
                if (tierRank(record.getTier()) > incomingRank)
                    continue;
                if (oldest == null || record.getWorldTimestamp() < oldest.getWorldTimestamp()) {
                    oldest = record;
                }
            }
        }
        return oldest;
    }

    /**
     * Returns echoes near a given position in the same dimension within the given
     * radius.
     */
    public List<EchoRecord> getEchoesNear(BlockPos center, int radius, ResourceKey<Level> dimensionKey) {
        if (center == null || dimensionKey == null || radius <= 0) {
            return Collections.emptyList();
        }

        int radiusSq = radius * radius;
        int minChunkX = (center.getX() - radius) >> 4;
        int maxChunkX = (center.getX() + radius) >> 4;
        int minChunkZ = (center.getZ() - radius) >> 4;
        int maxChunkZ = (center.getZ() + radius) >> 4;

        List<EchoRecord> result = new ArrayList<>();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                List<EchoRecord> list = chunkEchoMap.get(new ChunkPos(chunkX, chunkZ));
                if (list == null || list.isEmpty()) {
                    continue;
                }
                for (EchoRecord echo : list) {
                    if (!dimensionKey.equals(echo.getDimension())) {
                        continue;
                    }
                    if (center.distSqr(echo.getAnchorPos()) <= radiusSq) {
                        result.add(echo);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Runs decay if enough real time has passed since the last check.
     */
    public void runDecayIfNeeded(long nowMs) {
        EchoesConfig cfg = EchoesConfig.get();
        if (!cfg.isDecayEnabled()) {
            return;
        }

        // Default: once per real hour; config days are applied per echo.
        final long intervalMs = 60L * 60L * 1000L;
        if (lastDecayCheckMs != 0L && (nowMs - lastDecayCheckMs) < intervalMs) {
            return;
        }
        lastDecayCheckMs = nowMs;

        boolean changed = false;
        int whispersDays = cfg.getWhisperDays();
        int markDays = cfg.getMarkDays();
        int scarDays = cfg.getScarDays();
        int worldFirstDays = cfg.getWorldFirstDays();

        long now = nowMs;

        Iterator<Map.Entry<ChunkPos, List<EchoRecord>>> it = chunkEchoMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ChunkPos, List<EchoRecord>> entry = it.next();
            List<EchoRecord> list = entry.getValue();
            Iterator<EchoRecord> echoIt = list.iterator();
            while (echoIt.hasNext()) {
                EchoRecord record = echoIt.next();
                int decayDays = switch (record.getTier()) {
                    case WHISPER -> whispersDays;
                    case MARK -> markDays;
                    case SCAR -> scarDays;
                    case WORLD_FIRST -> worldFirstDays;
                };
                if (decayDays < 0) {
                    continue;
                }
                long ageMs = now - record.getRealTimestamp();
                long ageDays = ageMs / (1000L * 60L * 60L * 24L);
                if (ageDays > decayDays) {
                    echoIt.remove();
                    changed = true;
                }
            }
            if (list.isEmpty()) {
                it.remove();
            }
        }

        if (changed) {
            setDirty();
            Echoes.LOGGER.debug("Echo decay run completed; remaining echoes={}", totalEchoCount());
        }
    }

    public PlayerEchoData getOrCreatePlayerData(UUID playerUuid) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid cannot be null");
        }
        return playerData.computeIfAbsent(playerUuid, ignore -> new PlayerEchoData());
    }

    public void forEachEcho(Consumer<EchoRecord> consumer) {
        if (consumer == null) {
            return;
        }
        for (List<EchoRecord> list : chunkEchoMap.values()) {
            for (EchoRecord record : list) {
                consumer.accept(record);
            }
        }
    }

    public void forEachPlayerData(Consumer<Map.Entry<UUID, PlayerEchoData>> consumer) {
        if (consumer == null) {
            return;
        }
        for (Map.Entry<UUID, PlayerEchoData> entry : playerData.entrySet()) {
            consumer.accept(entry);
        }
    }

    // -------------------------------------------------------------------------
    // NBT encode / decode
    // -------------------------------------------------------------------------

    private void readChunkEchoes(ListTag chunkList) {
        chunkEchoMap.clear();
        for (Tag element : chunkList) {
            if (!(element instanceof CompoundTag chunkNbt)) {
                continue;
            }
            int chunkX = chunkNbt.getInt(TAG_CHUNK_X).orElse(0);
            int chunkZ = chunkNbt.getInt(TAG_CHUNK_Z).orElse(0);
            ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
            ListTag echoList = chunkNbt.getList(TAG_ECHOS).orElse(new ListTag());
            List<EchoRecord> records = new ArrayList<>(echoList.size());
            for (Tag echoElement : echoList) {
                if (!(echoElement instanceof CompoundTag echoNbt)) {
                    continue;
                }
                EchoRecord record = decodeEcho(echoNbt);
                if (record != null) {
                    records.add(record);
                }
            }
            if (!records.isEmpty()) {
                chunkEchoMap.put(chunkPos, records);
            }
        }
    }

    private ListTag writeChunkEchoes() {
        ListTag chunkList = new ListTag();
        for (Map.Entry<ChunkPos, List<EchoRecord>> entry : chunkEchoMap.entrySet()) {
            ChunkPos chunkPos = entry.getKey();
            List<EchoRecord> records = entry.getValue();
            if (records == null || records.isEmpty()) {
                continue;
            }
            CompoundTag chunkNbt = new CompoundTag();
            chunkNbt.putInt(TAG_CHUNK_X, chunkPos.x());
            chunkNbt.putInt(TAG_CHUNK_Z, chunkPos.z());

            ListTag echoList = new ListTag();
            for (EchoRecord record : records) {
                echoList.add(encodeEcho(record));
            }
            chunkNbt.put(TAG_ECHOS, echoList);
            chunkList.add(chunkNbt);
        }
        return chunkList;
    }

    private EchoRecord decodeEcho(CompoundTag nbt) {
        try {
            UUID id = parseUuid(nbt.getString(TAG_ECHO_UUID).orElse(""));
            if (id == null)
                id = UUID.randomUUID();
            UUID playerId = parseUuid(nbt.getString(TAG_ECHO_PLAYER_UUID).orElse(""));
            String playerName = nbt.getString(TAG_ECHO_PLAYER_NAME).orElse("");

            EchoEventType eventType = EchoEventType.valueOf(nbt.getString(TAG_ECHO_EVENT_TYPE).orElse(""));
            EchoTier tier = EchoTier.valueOf(nbt.getString(TAG_ECHO_TIER).orElse(""));

            Identifier dimId = Identifier.tryParse(nbt.getString(TAG_ECHO_DIMENSION).orElse(""));
            if (dimId == null) {
                return null;
            }
            ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimId);

            BlockPos anchor = new BlockPos(
                    nbt.getInt(TAG_ECHO_ANCHOR_X).orElse(0),
                    nbt.getInt(TAG_ECHO_ANCHOR_Y).orElse(0),
                    nbt.getInt(TAG_ECHO_ANCHOR_Z).orElse(0));

            long worldTime = nbt.getLong(TAG_ECHO_WORLD_TIME).orElse(0L);
            long realTime = nbt.getLong(TAG_ECHO_REAL_TIME).orElse(0L);

            EquipmentSnapshot equipment = null;
            if (nbt.contains(TAG_ECHO_EQUIPMENT)) {
                equipment = EquipmentSnapshot.fromNbt(nbt.getCompound(TAG_ECHO_EQUIPMENT).orElse(new CompoundTag()));
            }

            List<EchoFrame> frames = new ArrayList<>();
            ListTag frameList = nbt.getList(TAG_ECHO_FRAMES).orElse(new ListTag());
            for (Tag element : frameList) {
                if (element instanceof CompoundTag frameNbt) {
                    EchoFrame f = decodeFrame(frameNbt);
                    if (f != null)
                        frames.add(f);
                }
            }

            Set<UUID> seenBy = new HashSet<>();
            ListTag seenList = nbt.getList(TAG_ECHO_SEEN_BY).orElse(new ListTag());
            for (Tag element : seenList) {
                try {
                    UUID seenId = UUID.fromString(element.asString().orElse(""));
                    seenBy.add(seenId);
                } catch (IllegalArgumentException ignored) {
                }
            }

            return new EchoRecord(
                    id,
                    playerId,
                    playerName,
                    eventType,
                    tier,
                    dimensionKey,
                    anchor,
                    worldTime,
                    realTime,
                    equipment,
                    frames,
                    seenBy);
        } catch (Exception e) {
            Echoes.LOGGER.warn("Failed to decode EchoRecord from NBT", e);
            return null;
        }
    }

    private CompoundTag encodeEcho(EchoRecord record) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString(TAG_ECHO_UUID, record.getUUID().toString());
        if (record.getPlayerUuid() != null) {
            nbt.putString(TAG_ECHO_PLAYER_UUID, record.getPlayerUuid().toString());
        }
        if (record.getPlayerName() != null) {
            nbt.putString(TAG_ECHO_PLAYER_NAME, record.getPlayerName());
        }
        nbt.putString(TAG_ECHO_EVENT_TYPE, record.getEventType().name());
        nbt.putString(TAG_ECHO_TIER, record.getTier().name());
        nbt.putString(TAG_ECHO_DIMENSION, record.getDimension().identifier().toString());

        BlockPos anchor = record.getAnchorPos();
        nbt.putInt(TAG_ECHO_ANCHOR_X, anchor.getX());
        nbt.putInt(TAG_ECHO_ANCHOR_Y, anchor.getY());
        nbt.putInt(TAG_ECHO_ANCHOR_Z, anchor.getZ());

        nbt.putLong(TAG_ECHO_WORLD_TIME, record.getWorldTimestamp());
        nbt.putLong(TAG_ECHO_REAL_TIME, record.getRealTimestamp());

        if (record.getEquipment() != null) {
            nbt.put(TAG_ECHO_EQUIPMENT, record.getEquipment().toNbt());
        }

        ListTag frameList = new ListTag();
        for (EchoFrame frame : record.getFrames()) {
            frameList.add(encodeFrame(frame));
        }
        nbt.put(TAG_ECHO_FRAMES, frameList);

        ListTag seenList = new ListTag();
        for (UUID seenId : record.getSeenBy()) {
            seenList.add(StringTag.valueOf(seenId.toString()));
        }
        nbt.put(TAG_ECHO_SEEN_BY, seenList);

        return nbt;
    }

    private EchoFrame decodeFrame(CompoundTag nbt) {
        try {
            float relX = nbt.getFloat(TAG_FRAME_REL_X).orElse(0f);
            float relY = nbt.getFloat(TAG_FRAME_REL_Y).orElse(0f);
            float relZ = nbt.getFloat(TAG_FRAME_REL_Z).orElse(0f);
            float yaw = nbt.getFloat(TAG_FRAME_YAW).orElse(0f);
            float pitch = nbt.getFloat(TAG_FRAME_PITCH).orElse(0f);
            float limbSwing = nbt.getFloat(TAG_FRAME_LIMB_SWING).orElse(0f);
            EchoAnimState animState = EchoAnimState.valueOf(nbt.getString(TAG_FRAME_ANIM_STATE).orElse(""));
            int tickOffset = nbt.getInt(TAG_FRAME_TICK_OFFSET).orElse(0);
            return new EchoFrame(relX, relY, relZ, yaw, pitch, limbSwing, animState, tickOffset);
        } catch (Exception e) {
            return null;
        }
    }

    private CompoundTag encodeFrame(EchoFrame frame) {
        CompoundTag nbt = new CompoundTag();
        nbt.putFloat(TAG_FRAME_REL_X, frame.getRelX());
        nbt.putFloat(TAG_FRAME_REL_Y, frame.getRelY());
        nbt.putFloat(TAG_FRAME_REL_Z, frame.getRelZ());
        nbt.putFloat(TAG_FRAME_YAW, frame.getYaw());
        nbt.putFloat(TAG_FRAME_PITCH, frame.getPitch());
        nbt.putFloat(TAG_FRAME_LIMB_SWING, frame.getLimbSwing());
        nbt.putString(TAG_FRAME_ANIM_STATE, frame.getAnimationState().name());
        nbt.putInt(TAG_FRAME_TICK_OFFSET, frame.getTickOffset());
        return nbt;
    }

    private void readPlayerData(ListTag list) {
        playerData.clear();
        for (Tag element : list) {
            if (!(element instanceof CompoundTag nbt)) {
                continue;
            }
            UUID playerId = parseUuid(nbt.getString(TAG_PLAYER_UUID).orElse(""));
            if (playerId == null)
                continue;
            PlayerEchoData data = new PlayerEchoData();

            // Biomes
            ListTag biomeList = nbt.getList(TAG_PLAYER_BIOMES).orElse(new ListTag());
            for (Tag biomeElement : biomeList) {
                if (biomeElement instanceof CompoundTag biomeNbt) {
                    String biomeIdStr = biomeNbt.getString(TAG_BIOME_ID).orElse("");
                    if (!biomeIdStr.isEmpty()) {
                        Identifier biomeId = Identifier.tryParse(biomeIdStr);
                        if (biomeId != null) {
                            ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME, biomeId);
                            data.addDiscoveredBiome(biomeKey);
                        }
                    }
                }
            }

            // Structures
            ListTag structureList = nbt.getList(TAG_PLAYER_STRUCTURES).orElse(new ListTag());
            for (Tag structElement : structureList) {
                if (structElement instanceof CompoundTag structNbt) {
                    String idStr = structNbt.getString(TAG_STRUCTURE_ID).orElse("");
                    if (!idStr.isEmpty()) {
                        Identifier id = Identifier.tryParse(idStr);
                        if (id == null)
                            continue;
                        BlockPos pos = new BlockPos(
                                structNbt.getInt(TAG_POS_X).orElse(0),
                                structNbt.getInt(TAG_POS_Y).orElse(0),
                                structNbt.getInt(TAG_POS_Z).orElse(0));
                        data.addDiscoveredStructure(new VisitedStructure(id, pos));
                    }
                }
            }

            // Dimensions
            ListTag dimensionList = nbt.getList(TAG_PLAYER_DIMENSIONS).orElse(new ListTag());
            for (Tag dimElement : dimensionList) {
                String dimStr = dimElement.asString().orElse("");
                if (!dimStr.isEmpty()) {
                    Identifier dimId = Identifier.tryParse(dimStr);
                    if (dimId != null) {
                        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimId);
                        data.addVisitedDimension(dimKey);
                    }
                }
            }

            // Crafted milestones
            ListTag milestoneList = nbt.getList(TAG_PLAYER_MILESTONES).orElse(new ListTag());
            for (Tag msElement : milestoneList) {
                String itemId = msElement.asString().orElse("");
                if (!itemId.isEmpty()) {
                    data.addCraftedMilestone(itemId);
                }
            }

            // Seen echoes
            ListTag seenList = nbt.getList(TAG_PLAYER_SEEN).orElse(new ListTag());
            for (Tag seenElement : seenList) {
                try {
                    String seenElementStr = seenElement.asString().orElse("");
                    if (!seenElementStr.isEmpty()) {
                        UUID seenId = UUID.fromString(seenElementStr);
                        data.markEchoSeen(seenId);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }

            data.setOptedOut(nbt.getBoolean(TAG_PLAYER_OPTED_OUT).orElse(false));
            data.setDisplayOptedOut(nbt.getBoolean(TAG_PLAYER_DISPLAY_OPTED_OUT).orElse(false));

            if (nbt.contains(TAG_PLAYER_SESSION_ORIGIN)) {
                CompoundTag posNbt = nbt.getCompound(TAG_PLAYER_SESSION_ORIGIN).orElse(new CompoundTag());
                BlockPos origin = new BlockPos(
                        posNbt.getInt(TAG_POS_X).orElse(0),
                        posNbt.getInt(TAG_POS_Y).orElse(0),
                        posNbt.getInt(TAG_POS_Z).orElse(0));
                data.setSessionOrigin(origin);
            }

            data.addToSessionDistanceTraveled(nbt.getFloat(TAG_PLAYER_SESSION_DISTANCE).orElse(0f));

            playerData.put(playerId, data);
        }
    }

    private ListTag writePlayerData() {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, PlayerEchoData> entry : playerData.entrySet()) {
            UUID playerId = entry.getKey();
            PlayerEchoData data = entry.getValue();
            CompoundTag nbt = new CompoundTag();
            nbt.putString(TAG_PLAYER_UUID, playerId.toString());

            // Biomes
            ListTag biomeList = new ListTag();
            for (ResourceKey<Biome> biomeKey : data.getDiscoveredBiomes()) {
                CompoundTag biomeNbt = new CompoundTag();
                biomeNbt.putString(TAG_BIOME_ID, biomeKey.identifier().toString());
                biomeList.add(biomeNbt);
            }
            nbt.put(TAG_PLAYER_BIOMES, biomeList);

            // Structures
            ListTag structureList = new ListTag();
            for (VisitedStructure structure : data.getDiscoveredStructures()) {
                CompoundTag structNbt = new CompoundTag();
                structNbt.putString(TAG_STRUCTURE_ID, structure.structureId().toString());
                BlockPos pos = structure.approximatePos();
                structNbt.putInt(TAG_POS_X, pos.getX());
                structNbt.putInt(TAG_POS_Y, pos.getY());
                structNbt.putInt(TAG_POS_Z, pos.getZ());
                structureList.add(structNbt);
            }
            nbt.put(TAG_PLAYER_STRUCTURES, structureList);

            // Dimensions
            ListTag dimensionList = new ListTag();
            for (ResourceKey<Level> dimKey : data.getVisitedDimensions()) {
                dimensionList.add(StringTag.valueOf(dimKey.identifier().toString()));
            }
            nbt.put(TAG_PLAYER_DIMENSIONS, dimensionList);

            // Crafted milestones
            ListTag milestoneList = new ListTag();
            for (String itemId : data.getCraftedMilestones()) {
                milestoneList.add(StringTag.valueOf(itemId));
            }
            nbt.put(TAG_PLAYER_MILESTONES, milestoneList);

            // Seen echoes
            ListTag seenList = new ListTag();
            for (UUID seenId : data.getSeenEchos()) {
                seenList.add(StringTag.valueOf(seenId.toString()));
            }
            nbt.put(TAG_PLAYER_SEEN, seenList);

            nbt.putBoolean(TAG_PLAYER_OPTED_OUT, data.isOptedOut());
            nbt.putBoolean(TAG_PLAYER_DISPLAY_OPTED_OUT, data.isDisplayOptedOut());

            BlockPos origin = data.getSessionOrigin();
            if (origin != null) {
                CompoundTag posNbt = new CompoundTag();
                posNbt.putInt(TAG_POS_X, origin.getX());
                posNbt.putInt(TAG_POS_Y, origin.getY());
                posNbt.putInt(TAG_POS_Z, origin.getZ());
                nbt.put(TAG_PLAYER_SESSION_ORIGIN, posNbt);
            }

            nbt.putFloat(TAG_PLAYER_SESSION_DISTANCE, data.getSessionDistanceTraveled());

            list.add(nbt);
        }
        return list;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isEmpty())
            return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
