package dev.votex.client.mixin;

import com.mojang.blaze3d.textures.FilterMode;
import dev.votex.client.UiSampling;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** MC26.2 otherwise hard-codes NEAREST even for oversampled TrueType glyphs. */
@Mixin(GlyphRenderState.class)
abstract class UiGlyphSamplingMixin {
 @Shadow public abstract TextRenderable renderable();
 @ModifyArg(method="textureSetup",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/SamplerCache;getClampToEdge(Lcom/mojang/blaze3d/textures/FilterMode;)Lcom/mojang/blaze3d/textures/GpuSampler;"),index=0)
 private FilterMode votex$smoothOwnTypeface(FilterMode original){return UiSampling.font(renderable())?FilterMode.LINEAR:original;}
}
