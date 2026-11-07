package dev.votex.client.mixin;

import dev.votex.client.VotexMenuBlur;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The extraction gate and frame's shader uniform both read this native getter. */
@Mixin(Options.class)
public abstract class MenuBlurMixin {
 @Inject(method="getMenuBackgroundBlurriness",at=@At("RETURN"),cancellable=true)
 private void votex$menuRadius(CallbackInfoReturnable<Integer> result){
  int original=result.getReturnValueI(),radius=VotexMenuBlur.radius(original);
  if(radius!=original)result.setReturnValue(radius);
 }
}
