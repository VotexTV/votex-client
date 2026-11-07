package dev.votex.client.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.votex.client.LocalLooks;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One cape owner: the local Votex cape replaces the vanilla layer for this player. */
@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {
 @Inject(method="submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",at=@At("HEAD"),cancellable=true)
 private void votex$singleCape(PoseStack stack,SubmitNodeCollector collector,int light,AvatarRenderState state,float yRot,float xRot,CallbackInfo callback){
  if(LocalLooks.forState(state)!=null&&LocalLooks.enabled(state,"cape"))callback.cancel();
 }
}
