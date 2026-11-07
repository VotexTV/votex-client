package dev.votex.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import dev.votex.client.UiSampling;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Store a smooth sampler in Votex draw states without changing the shared atlas. */
@Mixin(GuiGraphicsExtractor.class)
abstract class UiSurfaceSamplingMixin {
 @ModifyVariable(method={
  "innerBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/textures/GpuSampler;IIIIFFFFI)V",
  "innerTiledBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/textures/GpuSampler;IIIIIIFFFFI)V"
 },at=@At("HEAD"),argsOnly=true,ordinal=0)
 private GpuSampler votex$smoothOwnSurface(GpuSampler original){return UiSampling.surface()?RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR):original;}
}
