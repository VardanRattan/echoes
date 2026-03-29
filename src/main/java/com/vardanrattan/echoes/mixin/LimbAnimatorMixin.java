package com.vardanrattan.echoes.mixin;

import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes LimbAnimator.pos so FrameSampler can read the server-side
 * limb swing accumulator for accurate ghost leg animations (Block B).
 *
 * Applied to LimbAnimator (server-accessible field).
 */
@Mixin(LimbAnimator.class)
public interface LimbAnimatorMixin {

    /** Returns the current limb swing position accumulator. */
    @Accessor("pos")
    float getPos();
}
