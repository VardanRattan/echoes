package com.vardanrattan.echoes.mixin;

import com.vardanrattan.echoes.events.MilestoneEchoHandler;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TameableEntity.class)
public abstract class TameableEntityMixin {

    @Inject(method = "setOwner", at = @At("TAIL"))
    private void echoes$onTame(PlayerEntity owner, CallbackInfo ci) {
        TameableEntity entity = (TameableEntity) (Object) this;
        if (owner instanceof ServerPlayerEntity serverPlayer && entity.isTamed()) {
            MilestoneEchoHandler.onTame(serverPlayer, entity.getBlockPos());
        }
    }
}
