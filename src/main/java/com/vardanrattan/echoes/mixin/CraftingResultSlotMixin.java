package com.vardanrattan.echoes.mixin;

import com.vardanrattan.echoes.events.MilestoneEchoHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingResultSlot.class)
public abstract class CraftingResultSlotMixin {

    @Shadow @Final private PlayerEntity player;

    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void echoes$onTakeCraftedItem(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (this.player instanceof ServerPlayerEntity serverPlayer) {
            MilestoneEchoHandler.onCraft(serverPlayer, stack);
        }
    }
}
