package dev.votex.config;

/** Local in-game material preferences, independent of exported launcher themes. */
public final class GlassConfig {
 public enum Quality { ULTRA, HIGH, BALANCED, PERFORMANCE, OFF;
  public int divisor(){return switch(this){case ULTRA->2;case HIGH->3;case BALANCED->4;case PERFORMANCE->6;case OFF->0;};}
  public double refraction(){return switch(this){case ULTRA->1;case HIGH->.85;case BALANCED->.6;case PERFORMANCE->.18;case OFF->0;};}
 }
 public enum Mode { DARK, LIGHT, ADAPTIVE }
 public enum Material { CLEAR, REGULAR, THICK, ULTRA }
 public enum Preset { CRYSTAL_CLEAR, LIQUID_BUBBLE, MINIMAL_GLASS }
 public Quality quality=Quality.BALANCED;
 public Mode mode=Mode.ADAPTIVE;
 public int intensity=70,blur=55,refraction=65,reflection=40,roundness=75,thickness=55,transparency=65,speed=55;
 public String tint="#b9d9ef";
 public boolean hover=true,reduceMotion=false,reduceTransparency=false;
 public void preset(Preset p){
  mode=Mode.ADAPTIVE;hover=true;reduceTransparency=false;
  switch(p){
   case CRYSTAL_CLEAR->{quality=Quality.HIGH;intensity=65;blur=25;refraction=85;reflection=38;roundness=70;thickness=35;transparency=82;speed=55;reduceMotion=false;tint="#cde6f1";}
   case LIQUID_BUBBLE->{quality=Quality.ULTRA;intensity=85;blur=65;refraction=80;reflection=52;roundness=92;thickness=80;transparency=66;speed=50;reduceMotion=false;tint="#b9d9ef";}
   case MINIMAL_GLASS->{quality=Quality.PERFORMANCE;intensity=45;blur=30;refraction=15;reflection=18;roundness=60;thickness=30;transparency=45;speed=70;reduceMotion=true;tint="#c9d4de";}
  }
 }
 public boolean valid(){return quality!=null&&mode!=null&&tint!=null&&tint.matches("#[a-fA-F0-9]{6}")&&bounded(intensity)&&bounded(blur)&&bounded(refraction)&&bounded(reflection)&&bounded(roundness)&&bounded(thickness)&&bounded(transparency)&&bounded(speed);}
 private static boolean bounded(int x){return x>=0&&x<=100;}
 public int color(){return 0xff000000|Integer.parseInt(tint.substring(1),16);}
 public int radius(int w,int h,int hint){int limit=Math.min(w,h)>96?40:Math.min(w,h)/2;return Math.max(2,Math.min(limit,Math.max(hint,(int)(Math.min(w,h)*.48*roundness/100.))));}
 public boolean motion(ThemeConfig theme,boolean menuAnimations){return quality!=Quality.OFF&&menuAnimations&&theme.motion()&&!reduceMotion;}
}
