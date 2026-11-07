package dev.votex.client;

import dev.votex.config.GlassConfig;
import dev.votex.config.ThemeConfig;

/** One material family for panels, controls, popovers, notifications and HUDs. */
public final class VotexGlassDesignSystem {
 private VotexGlassDesignSystem(){}
 public static boolean motion(VotexClient core){return core.modules.config().glass.motion(core.theme,core.modules.config().menuAnimations);}
 public static int transitionMillis(VotexClient core){return 700-core.modules.config().glass.speed*3;}
 public record Surface(int tint,int radius,int packedRadiusMode,int packedIntensityBlur,float refract,float reflect,float thickness){}
 public static Surface surface(GlassConfig c,ThemeConfig theme,GlassConfig.Material material,int width,int height,int radius,int color,double localIntensity){
  double thickness=switch(material){case CLEAR->.55;case REGULAR->.8;case THICK->1.12;case ULTRA->1.35;};
  int r=c.radius(width,height,radius),flags=c.mode.ordinal()<<12|(c.reduceTransparency?16384:0);
  double tintMix=material==GlassConfig.Material.CLEAR?.07:.13;
  int tint=GlassRender.blend(c.color(),color,(float)tintMix);
  double alpha=(color>>>24)/255.;double opacity=c.reduceTransparency?.96:Math.clamp((1-c.transparency/100.)*.65*(.25+.75*alpha)+(material==GlassConfig.Material.THICK?.10:.015),.06,.84);
  tint=GlassRender.alpha(tint,opacity);
  int intensity=(int)Math.round(Math.clamp(c.intensity/100.*localIntensity,0,1)*255),blur=theme.effects().glassEffect()?(int)Math.round(c.blur*2.55):0;
  if(!VotexClient.INSTANCE.modules.config().blurMenus&&net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof VotexMenuSurface)blur=0;
  return new Surface(tint,r,(Math.min(4095,r*16)|flags),(intensity|(blur<<8)),(float)(c.refraction/100.*c.quality.refraction()),(float)(c.reflection/100.*(theme.effects().reflectionEffect()?1:0)),(float)Math.min(1,c.thickness/100.*thickness));
 }
 public static int ink(int color){var core=VotexClient.INSTANCE;if(core==null||core.modules==null||core.modules.config().glass.mode!=GlassConfig.Mode.LIGHT)return color;int r=(color>>16)&255,g=(color>>8)&255,b=color&255;return r+g+b>370?(color&0xff000000)|0x192a38:color;}
}
