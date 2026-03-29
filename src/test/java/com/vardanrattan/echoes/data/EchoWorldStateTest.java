package com.vardanrattan.echoes.data;

import com.vardanrattan.echoes.config.EchoesConfig;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class EchoWorldStateTest {

    private EchoWorldState state;
    private final RegistryKey<World> overworld = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("minecraft", "overworld"));

    @BeforeEach
    void setUp() {
        state = new EchoWorldState();
    }

    @Test
    void testEvictionOldestWhisper() {
        BlockPos pos = new BlockPos(10, 64, 10);
        
        // Fill chunk with 8 WHISPER echoes (assuming default max-echoes-per-chunk is 8)
        for (int i = 0; i < 8; i++) {
            state.addEcho(createDummyEcho(pos.add(0, i, 0), EchoTier.WHISPER, 1000 + i));
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
        long wayPast = now - (1000L * 60L * 60L * 24L * 10); // 10 days ago
        
        // Add a whisper that is 10 days old (default decay is 3 days)
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
        assertEquals(fresh.getUuid(), remaining.get(0).getUuid(), "Fresh echo should remain");
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

