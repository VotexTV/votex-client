package dev.votex.client.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.votex.client.FirstPersonEmotes;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemInHandRenderer.class)
public abstract class FirstPersonEmoteMixin {
 @Inject(method="submitHandsWithItems",at=@At("HEAD"),cancellable=true)
 private void votex$emote(float partialTick,PoseStack stack,SubmitNodeCollector collector,LocalPlayer player,int light,CallbackInfo ci){if(FirstPersonEmotes.submit(stack,collector,light))ci.cancel();}
}
