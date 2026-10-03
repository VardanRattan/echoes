package com.vardanrattan.echoes.data;

import com.vardanrattan.echoes.config.EchoesConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class EchoWorldStateTest {

    private EchoWorldState state;
    private final ResourceKey<Level> overworld = ResourceKey.create(Registries.DIMENSION, Identifier.tryParse("minecraft:overworld"));

    @BeforeAll
    static void initMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void setUp() {
        state = new EchoWorldState();
    }

    @Test
    void testEvictionOldestWhisper() {
        BlockPos pos = new BlockPos(10, 64, 10);
        
        // Fill chunk with 8 WHISPER echoes (assuming default max-echoes-per-chunk is 8)
        for (int i = 0; i < 8; i++) {
            state.addEcho(createDummyEcho(pos.above(i), EchoTier.WHISPER, 1000 + i));
        }
        
        assertEquals(8, state.getEchoesNear(pos, 10, overworld).size());

        // Add a MARK (higher tier) echo
        EchoRecord mark = createDummyEcho(pos, EchoTier.MARK, 5000);
        state.addEcho(mark);

        List<EchoRecord> echoes = state.getEchoesNear(pos, 64, overworld);
        assertEquals(8, echoes.size(), "Should have evicted one whisper to make room for mark");
        
        boolean foundMark = echoes.stream().anyMatch(e -> e.getTier() == EchoTier.MARK);
        assertTrue(foundMark, "Mark echo should be present");
        
        // Verify the oldest whisper (worldTime=1000) was evicted
        boolean foundOldest = echoes.stream().anyMatch(e -> e.getWorldTimestamp() == 1000);
        assertFalse(foundOldest, "Oldest whisper should have been evicted");
    }

    @Test
    void testDecay() {
        long now = System.currentTimeMillis();
        long wayPast = now - (1000L * 60L * 60L * 24L * 10); // 10 days ago (whisper decay default is 7 days)
        
        // Add a whisper that is 10 days old
        EchoRecord expired = new EchoRecord(
                UUID.randomUUID(), UUID.randomUUID(), "OldTimer",
                EchoEventType.DEATH, EchoTier.WHISPER, overworld,
                new BlockPos(0,0,0), 100, wayPast,
                null, Collections.emptyList(), Collections.emptySet()
        );
        state.addEcho(expired);
        
        // Add a fresh whisper
        EchoRecord fresh = createDummyEcho(new BlockPos(1,1,1), EchoTier.WHISPER, 200);
        state.addEcho(fresh);

        assertEquals(2, state.getEchoesNear(new BlockPos(0,0,0), 10, overworld).size());

        // Run decay
        state.runDecayIfNeeded(now);

        List<EchoRecord> remaining = state.getEchoesNear(new BlockPos(0,0,0), 10, overworld);
        assertEquals(1, remaining.size(), "Expired echo should be removed");
        assertEquals(fresh.getUUID(), remaining.get(0).getUUID(), "Fresh echo should remain");
    }

    @Test
    void testGetEchoesNearRadius() {
        BlockPos center = new BlockPos(100, 64, 100);
        
        state.addEcho(createDummyEcho(new BlockPos(105, 64, 100), EchoTier.WHISPER, 0)); // dist 5
        state.addEcho(createDummyEcho(new BlockPos(120, 64, 100), EchoTier.WHISPER, 0)); // dist 20
        state.addEcho(createDummyEcho(new BlockPos(100, 64, 200), EchoTier.WHISPER, 0)); // dist 100

        assertEquals(1, state.getEchoesNear(center, 10, overworld).size(), "Radius 10 filter");
        assertEquals(2, state.getEchoesNear(center, 30, overworld).size(), "Radius 30 filter");
        assertEquals(3, state.getEchoesNear(center, 150, overworld).size(), "Radius 150 filter");
    }

    @Test
    void testDimensionEnterAndManualCrystalTierMapping() {
        assertEquals(EchoTier.MARK, com.vardanrattan.echoes.events.EchoService.tierFor(EchoEventType.DIMENSION_ENTER),
                "Dimension enter should be mapped to Tier 2 MARK");
        assertEquals(EchoTier.SCAR, com.vardanrattan.echoes.events.EchoService.tierFor(EchoEventType.MANUAL_CRYSTAL),
                "Manual crystal recording should be mapped to Tier 3 SCAR");
        assertEquals(20 * 8, com.vardanrattan.echoes.events.EchoService.maxRecordingTicks(EchoEventType.MANUAL_CRYSTAL),
                "Manual crystal recording duration should be 8 seconds");
    }

    @Test
    void testWorldFirstClaimLifecycle() {
        String key = "world_first:BOSS_KILL";
        assertTrue(state.claimWorldFirst(key), "First claim should succeed");
        assertFalse(state.claimWorldFirst(key), "Duplicate claim should fail");
        assertTrue(state.getWorldFirstsClaimed().contains(key), "Claimed world first should be present");
    }

    @Test
    void testGranularWorldFirsts() {
        String dragonKey = "world_first:BOSS_KILL:ender_dragon";
        String witherKey = "world_first:BOSS_KILL:wither";
        
        assertTrue(state.claimWorldFirst(dragonKey), "Dragon world first claim should succeed");
        assertTrue(state.claimWorldFirst(witherKey), "Wither world first claim should succeed independently");
        assertFalse(state.claimWorldFirst(dragonKey), "Duplicate dragon claim should fail");
    }

    @Test
    void testStructureDeduplication() {
        PlayerEchoData data = new PlayerEchoData();
        Identifier stronghold = Identifier.tryParse("minecraft:stronghold");
        Identifier fortress = Identifier.tryParse("minecraft:fortress");

        BlockPos entryPos = new BlockPos(1000, 40, 1000);
        data.addDiscoveredStructure(new VisitedStructure(stronghold, entryPos));

        // Walking 5 blocks away inside the same structure
        BlockPos nextRoom = new BlockPos(1005, 40, 1000);
        assertTrue(data.hasDiscoveredStructureNear(stronghold, nextRoom, 256.0),
                "Should recognize position within 256 blocks of same structure as already discovered");

        // A different structure type at the same position
        assertFalse(data.hasDiscoveredStructureNear(fortress, nextRoom, 256.0),
                "Different structure type should not match");

        // Far away position (10,000 blocks away) of the same structure type
        BlockPos farStronghold = new BlockPos(10000, 40, 10000);
        assertFalse(data.hasDiscoveredStructureNear(stronghold, farStronghold, 256.0),
                "Far away stronghold beyond 256 blocks should be treated as a new discovery");
    }

    @Test
    void testPurgeEchoesByPlayer() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        BlockPos pos = new BlockPos(0, 64, 0);

        state.addEcho(new EchoRecord(
                UUID.randomUUID(), player1, "Player1",
                EchoEventType.DEATH, EchoTier.MARK, overworld,
                pos, 100, System.currentTimeMillis(),
                null, Collections.emptyList(), Collections.emptySet()
        ));
        state.addEcho(new EchoRecord(
                UUID.randomUUID(), player2, "Player2",
                EchoEventType.DEATH, EchoTier.MARK, overworld,
                pos, 101, System.currentTimeMillis(),
                null, Collections.emptyList(), Collections.emptySet()
        ));

        assertEquals(2, state.getEchoesNear(pos, 10, overworld).size());

        state.purgeEchoesByPlayer(player1);

        List<EchoRecord> remaining = state.getEchoesNear(pos, 10, overworld);
        assertEquals(1, remaining.size());
        assertEquals(player2, remaining.get(0).getPlayerUuid());
    }

    private EchoRecord createDummyEcho(BlockPos pos, EchoTier tier, long worldTime) {
        return new EchoRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Player",
                EchoEventType.DEATH,
                tier,
                overworld,
                pos,
                worldTime,
                System.currentTimeMillis(),
                null,
                Collections.emptyList(),
                Collections.emptySet()
        );
    }
}
