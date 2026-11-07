package dev.votex.client.mixin;
import dev.votex.client.ItemPhysicsState;
import dev.votex.client.VotexClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Visual item physics: stable ground pose, tumbling airborne item. No gameplay simulation changes. */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
 @Shadow @Final private RandomSource random;
 @Inject(method="extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",at=@At("TAIL"))
 private void votex$extract(ItemEntity entity,ItemEntityRenderState state,float partial,CallbackInfo ci){((ItemPhysicsState)state).votex$grounded(entity.onGround());}
 @Inject(method="submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",at=@At("HEAD"),cancellable=true)
 private void votex$physics(ItemEntityRenderState state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera,CallbackInfo ci){var core=VotexClient.INSTANCE;if(core==null||!core.modules.enabled("item_physics")||state.item.isEmpty())return;var bounds=state.item.getModelBoundingBox();boolean flat=bounds.getZsize()<=0.0625;boolean ground=((ItemPhysicsState)state).votex$grounded();if(!ground&&!core.modules.config().itemAirTumble)return;pose.pushPose();if(ground){pose.translate(0,flat?bounds.getZsize()/2+0.025:-bounds.minY+0.025,0);pose.mulPose(Axis.YP.rotationDegrees(Math.floorMod(state.seed,360)));if(flat)pose.mulPose(Axis.XP.rotationDegrees(90));}else{double cx=(bounds.minX+bounds.maxX)*.5,cy=(bounds.minY+bounds.maxY)*.5,cz=(bounds.minZ+bounds.maxZ)*.5;
 pose.translate(0,.12,0);
 pose.translate(cx,cy,cz);
 float seedAngle=Math.floorMod(state.seed,360);
 pose.mulPose(Axis.YP.rotationDegrees(state.ageInTicks*10+seedAngle));
 pose.mulPose(Axis.XP.rotationDegrees(state.ageInTicks*(flat?8:5)+seedAngle*.3f));
 pose.translate(-cx,-cy,-cz);}ItemEntityRenderer.submitMultipleFromCount(pose,collector,state.lightCoords,state,random,bounds);pose.popPose();ci.cancel();}
}
