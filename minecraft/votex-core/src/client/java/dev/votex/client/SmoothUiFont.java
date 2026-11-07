package dev.votex.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.EmptyArea;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4fc;

/** The smooth-font marker travels with each immutable glyph, not a GPU debug label.
 * Driver debug labels may be absent. Rendering/font fallback/layout remain native.
 */
final class SmoothUiFont extends Font {
 SmoothUiFont(Provider provider){super(provider);}
 @Override public PreparedText prepareText(String text,float x,float y,int color,boolean shadow,int background){return smooth(super.prepareText(text,x,y,color,shadow,background));}
 @Override public PreparedText prepareText(FormattedCharSequence text,float x,float y,int color,boolean shadow,boolean includeEmpty,int background){return smooth(super.prepareText(text,x,y,color,shadow,includeEmpty,background));}
 @Override public PreparedText prepare8xTextOutline(FormattedCharSequence text,float x,float y,int color){return smooth(super.prepare8xTextOutline(text,x,y,color));}
 private static PreparedText smooth(PreparedText prepared){return prepared instanceof SmoothPrepared?prepared:new SmoothPrepared(prepared);}
 private record SmoothPrepared(PreparedText delegate) implements PreparedText {
  @Override public ScreenRectangle bounds(){return delegate.bounds();}
  @Override public void visit(GlyphVisitor visitor){delegate.visit(new GlyphVisitor(){
   @Override public void acceptGlyph(TextRenderable.Styled glyph){visitor.acceptRenderable(new SmoothGlyph(glyph));}
   @Override public void acceptEffect(TextRenderable effect){visitor.acceptEffect(effect);}
   @Override public void acceptRenderable(TextRenderable glyph){visitor.acceptRenderable(new SmoothGlyph(glyph));}
   @Override public void acceptEmptyArea(EmptyArea empty){visitor.acceptEmptyArea(empty);}
  });}
 }
 record SmoothGlyph(TextRenderable delegate) implements TextRenderable {
  @Override public void render(Matrix4fc matrix,VertexConsumer vertices,int light,boolean gui){delegate.render(matrix,vertices,light,gui);}
  @Override public RenderType renderType(DisplayMode mode){return delegate.renderType(mode);}
  @Override public GpuTextureView textureView(){return delegate.textureView();}
  @Override public RenderPipeline guiPipeline(){return delegate.guiPipeline();}
  @Override public float left(){return delegate.left();}
  @Override public float top(){return delegate.top();}
  @Override public float right(){return delegate.right();}
  @Override public float bottom(){return delegate.bottom();}
 }
}
