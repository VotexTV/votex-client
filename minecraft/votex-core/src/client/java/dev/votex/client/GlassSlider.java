package dev.votex.client;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;
import dev.votex.config.GlassConfig;
import dev.votex.ui.SliderMotion;
import dev.votex.ui.HoverMotion;
import java.util.function.IntConsumer;
/** Native keyboard/narration/drag semantics; only its presentation is Votex glass. */
public final class GlassSlider extends AbstractSliderButton {
 private final String label;private final IntConsumer setter;private final SliderMotion motion=new SliderMotion();private final HoverMotion light=new HoverMotion();private boolean pressed;private long sampled=System.nanoTime();
 public GlassSlider(int x,int y,int w,String label,int initial,IntConsumer setter){super(x,y,w,46,Component.literal(label),initial/100.);this.label=label;this.setter=setter;updateMessage();}
 protected void updateMessage(){if(label!=null)setMessage(UiText.component(label+" · "+Math.round(value*100)+" %"));}
 protected void applyValue(){setter.accept((int)Math.round(value*100));}
 private MouseButtonEvent mapped(MouseButtonEvent e){return new MouseButtonEvent(getX()+4+SliderMotion.valueAt(e.x(),getX(),width)*(width-8),e.y(),e.buttonInfo());}
 @Override public void onClick(MouseButtonEvent e,boolean twice){pressed=active;super.onClick(mapped(e),twice);}
 @Override protected void onDrag(MouseButtonEvent e,double dx,double dy){super.onDrag(mapped(e),dx,dy);}
 @Override public void onRelease(MouseButtonEvent e){pressed=false;super.onRelease(mapped(e));}
 @Override public void setFocused(boolean value){super.setFocused(value);if(!value)pressed=false;}
 @Override public void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
  var core=VotexClient.INSTANCE;long now=System.nanoTime();double dt=Math.min(.1,(now-sampled)/1e9);sampled=now;boolean animate=VotexGlassDesignSystem.motion(core);motion.step(value,pressed,dt,animate);
  int x=getX(),y=getY(),span=width-36,cx=x+18+(int)Math.round(span*value),cy=y+32;
  GlassRender.round(g,x,y,width,height,23,0x4cbed9ef);UiText.draw(g,UiText.fit(getMessage().getString(),width-24),x+12,y+8,active?core.theme.text():core.theme.muted());
  GlassRender.round(g,x+12,cy-6,width-24,12,6,0x486f879f);GlassRender.round(g,x+18,cy-2,Math.max(2,cx-x-18),4,2,GlassRender.alpha(core.theme.accent(),.65));
  g.pose().pushMatrix();g.pose().translate(cx,cy);g.pose().rotate(motion.angle());g.pose().scale(motion.scaleX(),motion.scaleY());g.pose().translate(-cx,-cy);
  GlassRender.round(g,cx-12,cy-9,24,23,11,0x30000000);
  if(!VotexGlassEngine.lens(g,cx-11,cy-11,22))GlassRender.round(g,cx-11,cy-11,22,22,11,0xc9d6e9f5);
  light.step(dt,isHoveredOrFocused()||pressed,active,mx,my,cx-11,cy-11,22,22,core.theme,animate);GlassRender.interaction(g,cx-11,cy-11,22,22,11,core.theme,light);g.pose().popMatrix();
  if(isFocused())GlassRender.round(g,x+16,y+height-3,width-32,2,1,GlassRender.alpha(core.theme.accent(),.8));
 }
}
