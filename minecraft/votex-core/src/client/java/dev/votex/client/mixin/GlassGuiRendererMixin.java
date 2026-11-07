package dev.votex.client.mixin;
import dev.votex.client.VotexGlassEngine;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GuiRenderer.class)
abstract class GlassGuiRendererMixin {
 @Inject(method="render",at=@At("HEAD"))private void votex$capture(CallbackInfo ci){VotexGlassEngine.beginFrame();}
 @Inject(method="render",at=@At("RETURN"))private void votex$timing(CallbackInfo ci){VotexGlassEngine.endFrame();}
 @Inject(method="close",at=@At("HEAD"))private void votex$release(CallbackInfo ci){VotexGlassEngine.close();}
 @ModifyArg(method="executeDraw",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"),index=0)
 private com.mojang.blaze3d.pipeline.RenderPipeline votex$actualDraw(com.mojang.blaze3d.pipeline.RenderPipeline pipeline){VotexGlassEngine.recordDraw(pipeline);return pipeline;}
}
