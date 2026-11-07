package dev.votex.client.mixin;
import dev.votex.client.WearableLayer;
import dev.votex.client.LocalLooks;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
 @Inject(method="extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",at=@At("TAIL"))
 private void votex$headwear(Avatar player,AvatarRenderState state,float partialTick,CallbackInfo ci){dev.votex.client.EmotePlayer.orientHandshake(player,state);WearableLayer.captureKinematics(player,state);if(LocalLooks.visible(state)&&LocalLooks.coversHead(state))state.showHat=false;String label=LocalLooks.nametag(state);if(label!=null&&state.nameTag!=null)state.nameTag=LocalLooks.local(state)?net.minecraft.network.chat.Component.literal(label):state.nameTag.copy().append(net.minecraft.network.chat.Component.literal(" · "+label));}
 @Inject(method="<init>",at=@At("TAIL"))
 private void votex$layer(CallbackInfo ci){((LivingRendererAccessor)this).votex$layers().add(new WearableLayer((RenderLayerParent)(Object)this));}
 @Inject(method="getTextureLocation(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/resources/Identifier;",at=@At("HEAD"),cancellable=true)
 private void votex$skin(AvatarRenderState state,CallbackInfoReturnable<Identifier> ci){Identifier skin=LocalLooks.skin(state);if(skin!=null)ci.setReturnValue(skin);}
}
