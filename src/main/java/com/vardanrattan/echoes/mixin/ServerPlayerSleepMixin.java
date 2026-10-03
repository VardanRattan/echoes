package com.vardanrattan.echoes.mixin;

import com.vardanrattan.echoes.events.MilestoneEchoHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSleepMixin {

    @Inject(method = "startSleeping", at = @At("HEAD"))
    private void echoes$onStartSleep(BlockPos pos, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        MilestoneEchoHandler.onSleep(player, pos);
    }
}
