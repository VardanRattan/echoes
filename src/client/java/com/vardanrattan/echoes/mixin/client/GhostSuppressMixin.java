package com.vardanrattan.echoes.mixin.client;

import com.vardanrattan.echoes.render.GhostPlayerRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side mixin used by the ghost renderer.
 *
 * Suppresses the shadow radius for our stand-in ghost entities
 * so that ghosts cast no shadow.
 */
@Mixin(EntityRenderer.class)
public class GhostSuppressMixin {

    /**
     * When the ghost renderer is actively rendering a ghost (i.e.
     * {@link GhostPlayerRenderer#isRenderingGhost()} returns true) we suppress
     * the shadow by forcing a radius of 0.
     *
     * The flag is set/cleared by GhostPlayerRenderer around every call to
     * EntityRenderDispatcher#render, so this injection is only active for ghost
     * entities.
     */
    @Inject(method = "getShadowRadius", at = @At("HEAD"), cancellable = true)
    private void echoes$suppressGhostShadow(Entity entity, CallbackInfoReturnable<Float> cir) {
        if (GhostPlayerRenderer.isRenderingGhost()) {
            cir.setReturnValue(0.0f);
        }
    }
}
