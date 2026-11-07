package dev.votex.client.mixin;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import dev.votex.client.VotexGlassEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MouseHandler.class)
abstract class GlassMouseMixin {
 @Inject(method="onButton",at=@At("HEAD"))private void votex$count(long window,MouseButtonInfo button,int action,CallbackInfo ci){var mc=Minecraft.getInstance();if(action==1&&mc.player!=null&&mc.gui.screen()==null)VotexGlassEngine.clicks.press(button.button(),System.nanoTime());}
}
