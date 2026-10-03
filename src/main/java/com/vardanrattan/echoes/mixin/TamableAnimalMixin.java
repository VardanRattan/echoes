package com.vardanrattan.echoes.mixin;

import com.vardanrattan.echoes.events.MilestoneEchoHandler;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TamableAnimal.class)
public abstract class TamableAnimalMixin {

    @Inject(method = "tame", at = @At("HEAD"))
    private void echoes$onTame(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            TamableAnimal self = (TamableAnimal) (Object) this;
            MilestoneEchoHandler.onTame(serverPlayer, self.blockPosition());
        }
    }
}
