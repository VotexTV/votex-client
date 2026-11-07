package dev.votex.client.mixin;
import dev.votex.client.EmoteWheel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** The radial card's lower slot has its own footer; it must not overlap the hotbar. */
@Mixin(Hud.class)
public abstract class HudVisibilityMixin {
 @Inject(method="extractRenderState",at=@At("HEAD"),cancellable=true)
 private void votex$wheelHud(GuiGraphicsExtractor graphics,DeltaTracker delta,CallbackInfo info){if(Minecraft.getInstance().gui.screen() instanceof EmoteWheel)info.cancel();}
}
