package com.vardanrattan.echoes.mixin;

import com.vardanrattan.echoes.events.MilestoneEchoHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantResultSlot.class)
public abstract class MerchantResultSlotMixin {

    @Shadow @Final private Player player;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void echoes$onTakeTradedItem(Player player, ItemStack stack, CallbackInfo ci) {
        if (this.player instanceof ServerPlayer serverPlayer) {
            MilestoneEchoHandler.onTrade(serverPlayer, serverPlayer.blockPosition());
        }
    }
}
