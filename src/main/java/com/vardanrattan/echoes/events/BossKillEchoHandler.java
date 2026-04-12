package com.vardanrattan.echoes.events;

import com.vardanrattan.echoes.config.EchoesConfig;
import com.vardanrattan.echoes.data.EchoEventType;
import com.vardanrattan.echoes.data.EchoFrame;
import com.vardanrattan.echoes.data.EchoWorldState;
import com.vardanrattan.echoes.data.EquipmentSnapshot;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Handles BOSS_KILL echo triggers (E1).
 */
public final class BossKillEchoHandler {

    private BossKillEchoHandler() {
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            EchoesConfig cfg = EchoesConfig.get();
            if (!cfg.isEnabled() || !cfg.isBossKillEnabled()) {
                return;
            }

            if (isBoss(entity)) {
                Entity attacker = damageSource.getEntity();
                ServerPlayer killer = resolvePlayerKiller(attacker);
                
                if (killer != null) {
                    ServerLevel world = (ServerLevel) killer.level();
                    EchoWorldState state = EchoWorldState.get(world);
                    
                    emitBossKillEcho(world, killer, state, entity.blockPosition());
                }
            }
        });
    }

    private static boolean isBoss(Entity entity) {
        return entity instanceof WitherBoss || 
               entity instanceof EnderDragon || 
               entity instanceof ElderGuardian;
    }

    private static ServerPlayer resolvePlayerKiller(Entity attacker) {
        if (attacker instanceof ServerPlayer player) {
            return player;
        }
        // Could be a projectile or tamed mob, but for now we simplify.
        return null;
    }

    private static void emitBossKillEcho(ServerLevel world, ServerPlayer player, EchoWorldState state, BlockPos pos) {
        // Special case: singleFrame with custom anchor (the boss's death pos)
        var buffered = FrameSampler.captureBufferedFrame(player);
        if (buffered == null) return;
        EchoFrame frame = FrameSampler.toRelativeEchoFrame(buffered, pos, 0);
        if (frame == null) return;
        List<EchoFrame> frames = List.of(frame);

        var equipment = EquipmentSnapshot.capture(player);
        
        // E6: World first check will be integrated into EchoService.createEchoFromFrames
        var record = EchoService.createEchoFromFrames(
                world,
                player,
                EchoEventType.BOSS_KILL,
                pos,
                frames,
                equipment
        );
        state.addEcho(record);
        EchoService.onEchoCreated(record);
        state.setDirty();
    }
}
