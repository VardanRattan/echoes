package com.vardanrattan.echoes.data;

import com.vardanrattan.echoes.Echoes;
import com.vardanrattan.echoes.config.EchoesConfig;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.*;
import java.util.function.Consumer;

/**
 * Per-world persistent state for all echoes and per-player echo data.
 *
 * This class is intentionally self-contained and only depends on core
 * Minecraft types plus the Echoes data classes, so upgrades to Fabric or
 * the rendering stack should not require touching this file.
 */
public final class EchoWorldState extends PersistentState {

    public static final String NAME = Echoes.MOD_ID + "_world";

    /**
     * In 1.21.1, we use PersistentState.Type to describe the state.
     */
    public static final PersistentState.Type<EchoWorldState> TYPE =
            new PersistentState.Type<>(EchoWorldState::new, EchoWorldState::fromNbt, null);

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

    public static EchoWorldState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, NAME);
    }

    public static EchoWorldState fromNbt(NbtCompound nbt, WrapperLookup registryLookup) {
        EchoWorldState state = new EchoWorldState();
        int version = nbt.getInt(TAG_DATA_VERSION);

        state.readChunkEchoes(nbt.getList(TAG_CHUNK_ECHOS, 10), registryLookup);
        state.readPlayerData(nbt.getList(TAG_PLAYER_DATA, 10), registryLookup);
        state.lastDecayCheckMs = nbt.getLong(TAG_LAST_DECAY_CHECK_MS);
        
        NbtList firsts = nbt.getList(TAG_WORLD_FIRSTS, 8);
        for (NbtElement element : firsts) {
            state.worldFirstsClaimed.add(element.asString());
        }
        
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, WrapperLookup registryLookup) {
        nbt.putInt(TAG_DATA_VERSION, CURRENT_DATA_VERSION);
        nbt.put(TAG_CHUNK_ECHOS, writeChunkEchoes(registryLookup));
        nbt.put(TAG_PLAYER_DATA, writePlayerData(registryLookup));
        nbt.putLong(TAG_LAST_DECAY_CHECK_MS, lastDecayCheckMs);
        
        NbtList firsts = new NbtList();
        for (String first : worldFirstsClaimed) {
            firsts.add(NbtString.of(first));
        }
        nbt.put(TAG_WORLD_FIRSTS, firsts);
        
        return nbt;
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
        ChunkPos chunkPos = new ChunkPos(echo.getAnchorPos());
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
                removeEcho(victim.getUuid());
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
                removeEcho(victim.getUuid());
            }
        }

        chunkList.add(echo);
        markDirty();
    }

    public void removeEcho(UUID echoUuid) {
        if (echoUuid == null) {
            return;
        }
        boolean changed = false;
        for (List<EchoRecord> list : chunkEchoMap.values()) {
            changed |= list.removeIf(e -> echoUuid.equals(e.getUuid()));
        }
        if (changed) {
            markDirty();
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
            markDirty();
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
        markDirty();
        return true;
    }

    public boolean claimWorldFirst(String eventKey) {
        if (worldFirstsClaimed.contains(eventKey)) {
            return false;
        }
        worldFirstsClaimed.add(eventKey);
        markDirty();
        return true;
    }

    public EchoRecord getEchoById(UUID echoUuid) {
        if (echoUuid == null) return null;
        for (List<EchoRecord> list : chunkEchoMap.values()) {
            for (EchoRecord record : list) {
                if (echoUuid.equals(record.getUuid())) {
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
        if (playerUuid == null) return 0;
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
                if (!playerUuid.equals(record.getPlayerUuid())) continue;
                if (tierRank(record.getTier()) > incomingRank) continue;
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
                if (tierRank(record.getTier()) > incomingRank) continue;
                if (oldest == null || record.getWorldTimestamp() < oldest.getWorldTimestamp()) {
                    oldest = record;
                }
            }
        }
        return oldest;
    }

    /**
     * Returns echoes near a given position in the same dimension within the given radius.
     */
    public List<EchoRecord> getEchoesNear(BlockPos center, int radius, RegistryKey<World> dimensionKey) {
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
                    if (center.getSquaredDistance(echo.getAnchorPos()) <= radiusSq) {
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
            markDirty();
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

    private void readChunkEchoes(NbtList chunkList, WrapperLookup registryLookup) {
        chunkEchoMap.clear();
        for (NbtElement element : chunkList) {
            if (!(element instanceof NbtCompound chunkNbt)) {
                continue;
            }
            int chunkX = chunkNbt.getInt(TAG_CHUNK_X);
            int chunkZ = chunkNbt.getInt(TAG_CHUNK_Z);
            ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
            NbtList echoList = chunkNbt.getList(TAG_ECHOS, 10);
            List<EchoRecord> records = new ArrayList<>(echoList.size());
            for (NbtElement echoElement : echoList) {
                if (!(echoElement instanceof NbtCompound echoNbt)) {
                    continue;
                }
                EchoRecord record = decodeEcho(echoNbt, registryLookup);
                if (record != null) {
                    records.add(record);
                }
            }
            if (!records.isEmpty()) {
                chunkEchoMap.put(chunkPos, records);
            }
        }
    }

    private NbtList writeChunkEchoes(WrapperLookup registryLookup) {
        NbtList chunkList = new NbtList();
        for (Map.Entry<ChunkPos, List<EchoRecord>> entry : chunkEchoMap.entrySet()) {
            ChunkPos chunkPos = entry.getKey();
            List<EchoRecord> records = entry.getValue();
            if (records == null || records.isEmpty()) {
                continue;
            }
            NbtCompound chunkNbt = new NbtCompound();
            chunkNbt.putInt(TAG_CHUNK_X, chunkPos.x);
            chunkNbt.putInt(TAG_CHUNK_Z, chunkPos.z);

            NbtList echoList = new NbtList();
            for (EchoRecord record : records) {
                echoList.add(encodeEcho(record, registryLookup));
            }
            chunkNbt.put(TAG_ECHOS, echoList);
            chunkList.add(chunkNbt);
        }
        return chunkList;
    }

    private EchoRecord decodeEcho(NbtCompound nbt, WrapperLookup registryLookup) {
        try {
            UUID id = parseUuid(nbt.getString(TAG_ECHO_UUID));
            if (id == null) id = UUID.randomUUID();
            UUID playerId = parseUuid(nbt.getString(TAG_ECHO_PLAYER_UUID));
            String playerName = nbt.getString(TAG_ECHO_PLAYER_NAME);

            EchoEventType eventType = EchoEventType.valueOf(nbt.getString(TAG_ECHO_EVENT_TYPE));
            EchoTier tier = EchoTier.valueOf(nbt.getString(TAG_ECHO_TIER));

            Identifier dimId = Identifier.tryParse(nbt.getString(TAG_ECHO_DIMENSION));
            if (dimId == null) {
                return null;
            }
            RegistryKey<World> dimensionKey = RegistryKey.of(RegistryKeys.WORLD, dimId);

            BlockPos anchor = new BlockPos(
                    nbt.getInt(TAG_ECHO_ANCHOR_X),
                    nbt.getInt(TAG_ECHO_ANCHOR_Y),
                    nbt.getInt(TAG_ECHO_ANCHOR_Z)
            );

            long worldTime = nbt.getLong(TAG_ECHO_WORLD_TIME);
            long realTime = nbt.getLong(TAG_ECHO_REAL_TIME);

            EquipmentSnapshot equipment = null;
            if (nbt.contains(TAG_ECHO_EQUIPMENT)) {
                equipment = EquipmentSnapshot.fromNbt(nbt.getCompound(TAG_ECHO_EQUIPMENT));
            }

            List<EchoFrame> frames = new ArrayList<>();
            NbtList frameList = nbt.getList(TAG_ECHO_FRAMES, 10);
            for (NbtElement element : frameList) {
                if (element instanceof NbtCompound frameNbt) {
                    EchoFrame f = decodeFrame(frameNbt);
                    if (f != null) frames.add(f);
                }
            }

            Set<UUID> seenBy = new HashSet<>();
            NbtList seenList = nbt.getList(TAG_ECHO_SEEN_BY, 8);
            for (NbtElement element : seenList) {
                try {
                    UUID seenId = UUID.fromString(element.asString());
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
                    seenBy
            );
        } catch (Exception e) {
            Echoes.LOGGER.warn("Failed to decode EchoRecord from NBT", e);
            return null;
        }
    }

    private NbtCompound encodeEcho(EchoRecord record, WrapperLookup registryLookup) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString(TAG_ECHO_UUID, record.getUuid().toString());
        if (record.getPlayerUuid() != null) {
            nbt.putString(TAG_ECHO_PLAYER_UUID, record.getPlayerUuid().toString());
        }
        if (record.getPlayerName() != null) {
            nbt.putString(TAG_ECHO_PLAYER_NAME, record.getPlayerName());
        }
        nbt.putString(TAG_ECHO_EVENT_TYPE, record.getEventType().name());
        nbt.putString(TAG_ECHO_TIER, record.getTier().name());
        nbt.putString(TAG_ECHO_DIMENSION, record.getDimension().getValue().toString());

        BlockPos anchor = record.getAnchorPos();
        nbt.putInt(TAG_ECHO_ANCHOR_X, anchor.getX());
        nbt.putInt(TAG_ECHO_ANCHOR_Y, anchor.getY());
        nbt.putInt(TAG_ECHO_ANCHOR_Z, anchor.getZ());

        nbt.putLong(TAG_ECHO_WORLD_TIME, record.getWorldTimestamp());
        nbt.putLong(TAG_ECHO_REAL_TIME, record.getRealTimestamp());

        if (record.getEquipment() != null) {
            nbt.put(TAG_ECHO_EQUIPMENT, record.getEquipment().toNbt());
        }

        NbtList frameList = new NbtList();
        for (EchoFrame frame : record.getFrames()) {
            frameList.add(encodeFrame(frame));
        }
        nbt.put(TAG_ECHO_FRAMES, frameList);

        NbtList seenList = new NbtList();
        for (UUID seenId : record.getSeenBy()) {
            seenList.add(NbtString.of(seenId.toString()));
        }
        nbt.put(TAG_ECHO_SEEN_BY, seenList);

        return nbt;
    }

    private EchoFrame decodeFrame(NbtCompound nbt) {
        try {
            float relX = nbt.getFloat(TAG_FRAME_REL_X);
            float relY = nbt.getFloat(TAG_FRAME_REL_Y);
            float relZ = nbt.getFloat(TAG_FRAME_REL_Z);
            float yaw = nbt.getFloat(TAG_FRAME_YAW);
            float pitch = nbt.getFloat(TAG_FRAME_PITCH);
            float limbSwing = nbt.getFloat(TAG_FRAME_LIMB_SWING);
            EchoAnimState animState = EchoAnimState.valueOf(nbt.getString(TAG_FRAME_ANIM_STATE));
            int tickOffset = nbt.getInt(TAG_FRAME_TICK_OFFSET);
            return new EchoFrame(relX, relY, relZ, yaw, pitch, limbSwing, animState, tickOffset);
        } catch (Exception e) {
            return null;
        }
    }

    private NbtCompound encodeFrame(EchoFrame frame) {
        NbtCompound nbt = new NbtCompound();
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

    private void readPlayerData(NbtList list, WrapperLookup registryLookup) {
        playerData.clear();
        for (NbtElement element : list) {
            if (!(element instanceof NbtCompound nbt)) {
                continue;
            }
            UUID playerId = parseUuid(nbt.getString(TAG_PLAYER_UUID));
            if (playerId == null) continue;
            PlayerEchoData data = new PlayerEchoData();

            // Biomes
            NbtList biomeList = nbt.getList(TAG_PLAYER_BIOMES, 10);
            for (NbtElement biomeElement : biomeList) {
                if (biomeElement instanceof NbtCompound biomeNbt) {
                    String biomeIdStr = biomeNbt.getString(TAG_BIOME_ID);
                    if (!biomeIdStr.isEmpty()) {
                        Identifier biomeId = Identifier.tryParse(biomeIdStr);
                        if (biomeId != null) {
                            RegistryKey<Biome> biomeKey = RegistryKey.of(RegistryKeys.BIOME, biomeId);
                            data.addDiscoveredBiome(biomeKey);
                        }
                    }
                }
            }

            // Structures
            NbtList structureList = nbt.getList(TAG_PLAYER_STRUCTURES, 10);
            for (NbtElement structElement : structureList) {
                if (structElement instanceof NbtCompound structNbt) {
                    String idStr = structNbt.getString(TAG_STRUCTURE_ID);
                    if (!idStr.isEmpty()) {
                        Identifier id = Identifier.tryParse(idStr);
                        if (id == null) continue;
                        BlockPos pos = new BlockPos(
                                structNbt.getInt(TAG_POS_X),
                                structNbt.getInt(TAG_POS_Y),
                                structNbt.getInt(TAG_POS_Z)
                        );
                        data.addDiscoveredStructure(new VisitedStructure(id, pos));
                    }
                }
            }

            // Dimensions
            NbtList dimensionList = nbt.getList(TAG_PLAYER_DIMENSIONS, 8);
            for (NbtElement dimElement : dimensionList) {
                String dimStr = dimElement.asString();
                if (!dimStr.isEmpty()) {
                    Identifier dimId = Identifier.tryParse(dimStr);
                    if (dimId != null) {
                        RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, dimId);
                        data.addVisitedDimension(dimKey);
                    }
                }
            }

            // Crafted milestones
            NbtList milestoneList = nbt.getList(TAG_PLAYER_MILESTONES, 8);
            for (NbtElement msElement : milestoneList) {
                String itemId = msElement.asString();
                if (!itemId.isEmpty()) {
                    data.addCraftedMilestone(itemId);
                }
            }

            // Seen echoes
            NbtList seenList = nbt.getList(TAG_PLAYER_SEEN, 8);
            for (NbtElement seenElement : seenList) {
                try {
                    UUID seenId = UUID.fromString(seenElement.asString());
                    data.markEchoSeen(seenId);
                } catch (IllegalArgumentException ignored) {
                }
            }

            data.setOptedOut(nbt.getBoolean(TAG_PLAYER_OPTED_OUT));
            data.setDisplayOptedOut(nbt.getBoolean(TAG_PLAYER_DISPLAY_OPTED_OUT));

            if (nbt.contains(TAG_PLAYER_SESSION_ORIGIN)) {
                NbtCompound posNbt = nbt.getCompound(TAG_PLAYER_SESSION_ORIGIN);
                BlockPos origin = new BlockPos(
                        posNbt.getInt(TAG_POS_X),
                        posNbt.getInt(TAG_POS_Y),
                        posNbt.getInt(TAG_POS_Z)
                );
                data.setSessionOrigin(origin);
            }

            data.addToSessionDistanceTraveled(nbt.getFloat(TAG_PLAYER_SESSION_DISTANCE));

            playerData.put(playerId, data);
        }
    }

    private NbtList writePlayerData(WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (Map.Entry<UUID, PlayerEchoData> entry : playerData.entrySet()) {
            UUID playerId = entry.getKey();
            PlayerEchoData data = entry.getValue();
            NbtCompound nbt = new NbtCompound();
            nbt.putString(TAG_PLAYER_UUID, playerId.toString());

            // Biomes
            NbtList biomeList = new NbtList();
            for (RegistryKey<Biome> biomeKey : data.getDiscoveredBiomes()) {
                NbtCompound biomeNbt = new NbtCompound();
                biomeNbt.putString(TAG_BIOME_ID, biomeKey.getValue().toString());
                biomeList.add(biomeNbt);
            }
            nbt.put(TAG_PLAYER_BIOMES, biomeList);

            // Structures
            NbtList structureList = new NbtList();
            for (VisitedStructure structure : data.getDiscoveredStructures()) {
                NbtCompound structNbt = new NbtCompound();
                structNbt.putString(TAG_STRUCTURE_ID, structure.structureId().toString());
                BlockPos pos = structure.approximatePos();
                structNbt.putInt(TAG_POS_X, pos.getX());
                structNbt.putInt(TAG_POS_Y, pos.getY());
                structNbt.putInt(TAG_POS_Z, pos.getZ());
                structureList.add(structNbt);
            }
            nbt.put(TAG_PLAYER_STRUCTURES, structureList);

            // Dimensions
            NbtList dimensionList = new NbtList();
            for (RegistryKey<World> dimKey : data.getVisitedDimensions()) {
                dimensionList.add(NbtString.of(dimKey.getValue().toString()));
            }
            nbt.put(TAG_PLAYER_DIMENSIONS, dimensionList);

            // Crafted milestones
            NbtList milestoneList = new NbtList();
            for (String itemId : data.getCraftedMilestones()) {
                milestoneList.add(NbtString.of(itemId));
            }
            nbt.put(TAG_PLAYER_MILESTONES, milestoneList);

            // Seen echoes
            NbtList seenList = new NbtList();
            for (UUID seenId : data.getSeenEchos()) {
                seenList.add(NbtString.of(seenId.toString()));
            }
            nbt.put(TAG_PLAYER_SEEN, seenList);

            nbt.putBoolean(TAG_PLAYER_OPTED_OUT, data.isOptedOut());
            nbt.putBoolean(TAG_PLAYER_DISPLAY_OPTED_OUT, data.isDisplayOptedOut());

            BlockPos origin = data.getSessionOrigin();
            if (origin != null) {
                NbtCompound posNbt = new NbtCompound();
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
        if (value == null || value.isEmpty()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

