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
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.Projectile;
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
                    ServerLevel world = killer.level();
                    EchoWorldState state = EchoWorldState.get(world);
                    var playerData = state.getOrCreatePlayerData(killer.getUUID());
                    if (playerData.isOptedOut()) {
                        return;
                    }
                    
                    String bossType = getBossType(entity);
                    emitBossKillEcho(world, killer, state, killer.blockPosition(), bossType);
                }
            }
        });
    }

    private static boolean isBoss(Entity entity) {
        return entity instanceof WitherBoss || 
               entity instanceof EnderDragon || 
               entity instanceof ElderGuardian;
    }

    private static String getBossType(Entity entity) {
        if (entity instanceof EnderDragon) return "ender_dragon";
        if (entity instanceof WitherBoss) return "wither";
        if (entity instanceof ElderGuardian) return "elder_guardian";
        return "unknown";
    }

    private static ServerPlayer resolvePlayerKiller(Entity attacker) {
        if (attacker instanceof ServerPlayer player) {
            return player;
        }
        if (attacker instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            if (owner instanceof ServerPlayer player) {
                return player;
            }
        }
        if (attacker instanceof TamableAnimal tameable && tameable.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }

    private static void emitBossKillEcho(ServerLevel world, ServerPlayer player, EchoWorldState state, BlockPos pos, String bossType) {
        List<EchoFrame> frames = FrameSampler.sampleFrames(world, player, pos, 80);
        if (frames.isEmpty()) return;

        var equipment = EquipmentSnapshot.capture(player);
        
        var record = EchoService.createEchoFromFrames(
                world,
                player,
                EchoEventType.BOSS_KILL,
                bossType,
                pos,
                frames,
                equipment
        );
        state.addEcho(record);
        EchoService.onEchoCreated(record);
        state.setDirty();
    }
}
