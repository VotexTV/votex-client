package dev.votex.client;
import dev.votex.config.ThemeConfig;
import dev.votex.ui.HoverMotion;
import dev.votex.ui.UiColor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import java.util.*;
/** Supersampled alpha-antialiased nine-slice glass. No raw GL state. */
public final class GlassRender {
 private static final Map<String,Identifier> SPRITES=new HashMap<>();
 private static final int[] RADII={3,4,6,8,10,12,16,20};
 private GlassRender(){}
 private static Identifier sprite(String id){return SPRITES.computeIfAbsent(id,s->Identifier.fromNamespaceAndPath("votex_client","ui/"+s));}
 private static int radius(int requested,int w,int h){int desired=Math.min(requested,Math.min(w,h)/2-1),best=3;for(int r:RADII)if(r<=desired)best=r;return best;}
 public static void round(GuiGraphicsExtractor g,int x,int y,int w,int h,int radius,int color){if(w<=0||h<=0)return;if(VotexClient.INSTANCE!=null&&VotexClient.INSTANCE.modules!=null)radius=VotexClient.INSTANCE.modules.config().glass.radius(w,h,radius);if((color&0xffffff)!=0&&VotexGlassEngine.draw(g,x,y,w,h,radius,color,dev.votex.config.GlassConfig.Material.REGULAR,1))return;if(w<8||h<8){g.fill(x,y,x+w,y+h,color);return;}UiSampling.beginSurface();try{g.blitSprite(RenderPipelines.GUI_TEXTURED,sprite("round_"+radius(radius,w,h)),x,y,w,h,color);}finally{UiSampling.endSurface();}}
 public static void border(GuiGraphicsExtractor g,int x,int y,int w,int h,int r,int color){if(w<=0||h<=0||VotexGlassEngine.active())return;UiSampling.beginSurface();try{g.blitSprite(RenderPipelines.GUI_TEXTURED,sprite("edge_"+radius(r,w,h)),x,y,w,h,color);}finally{UiSampling.endSurface();}}
 public static void icon(GuiGraphicsExtractor g,String icon,int x,int y,int size,int color){UiSampling.beginSurface();try{g.blitSprite(RenderPipelines.GUI_TEXTURED,sprite(icon),x,y,size,size,VotexGlassDesignSystem.ink(color));}finally{UiSampling.endSurface();}}
 public static int alpha(int color,double opacity){return ((int)Math.round(Math.clamp(opacity,0,1)*255)<<24)|(color&0xffffff);}
 public static int surface(int color,ThemeConfig t){return t.effects().glassEffect()?color:UiColor.opaqueOver(color,t.panel());}
 public static int foreground(int fill,ThemeConfig t){return UiColor.foreground(UiColor.opaqueOver(fill,t.panel()),t.text());}
 public static void panel(GuiGraphicsExtractor g,int x,int y,int w,int h,ThemeConfig t){int r=Math.max(8,t.radius());double shadow=t.effects().shadow()/100;if(shadow>0){round(g,x-7,y-3,w+14,h+16,r+4,alpha(0,.20*shadow));round(g,x-3,y-1,w+6,h+7,r+2,alpha(0,.38*shadow));}if(!VotexGlassEngine.draw(g,x,y,w,h,r,t.panel(),dev.votex.config.GlassConfig.Material.THICK,1)){round(g,x,y,w,h,r,t.panel());border(g,x,y,w,h,r,0x3adceaff);}}
 /** Soft light stays inside its surface; it never changes widget bounds or input coordinates. */
 public static void pointerLight(GuiGraphicsExtractor g,int x,int y,int w,int h,int r,ThemeConfig t,HoverMotion motion){
  VotexGlassEngine.interaction(g,x,y,w,h,r,t,motion);
 }
 public static void interaction(GuiGraphicsExtractor g,int x,int y,int w,int h,int r,ThemeConfig t,HoverMotion motion){
  double glow=t.effects().glowAmount()*Math.max(motion.hover(),motion.near());if(glow>.002)border(g,x,y,w,h,r,alpha(t.accent(),.42*glow));
  pointerLight(g,x,y,w,h,r,t,motion);
 }
 /** Floating choices need separation from the text underneath, even in very transparent themes. */
 public static void popover(GuiGraphicsExtractor g,int x,int y,int w,int h,ThemeConfig t){panel(g,x,y,w,h,t.withPanel((Math.max(238,t.panel()>>>24)<<24)|(t.panel()&0xffffff)));}
 public static void cloud(GuiGraphicsExtractor g,int x,int y,int scale,int color){icon(g,"cloud",x,y,16*scale,color);}
 public static int blend(int a,int b,float t){t=Math.clamp(t,0,1);int out=0;for(int shift:new int[]{0,8,16,24})out|=(int)(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t)<<shift;return out;}
}
