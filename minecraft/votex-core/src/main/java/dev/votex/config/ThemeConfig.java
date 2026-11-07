package dev.votex.config;
import java.nio.file.*;
import com.google.gson.JsonObject;
/** Schema1 keeps old exports compatible; absent effect controls use launcher defaults. */
public record ThemeConfig(int accent,int text,int muted,int panel,int radius,int wobble,Effects effects) {
 public record Effects(int secondary,boolean hover,boolean reducedMotion,boolean glassEffect,boolean glowEffect,boolean reflectionEffect,boolean proximityEffect,boolean wobbleEffect,double blur,double glow,double reflection,double shadow,double proximity,double lift,double tilt,double speed,double stiffness,double damping){
  public static Effects defaults(){return new Effects(0xff45d8ff,true,false,true,true,true,false,true,42,28,35,40,0,18,0,55,55,65);}
  public double response(){return Math.clamp((12+speed*.10+stiffness*.055)/(0.75+damping*.006),8,32);}
  public double light(){return hover&&!reducedMotion&&proximityEffect?proximity/100:0;}
  public double glowAmount(){return glowEffect?glow/100:0;}
  public double reflectionAmount(){return reflectionEffect?reflection/100:0;}
 }
 public ThemeConfig(int accent,int text,int muted,int panel,int radius,int wobble){this(accent,text,muted,panel,radius,wobble,Effects.defaults());}
 public static ThemeConfig defaults(){return new ThemeConfig(0xff3478ff,0xfff7faff,0xff98a8c5,0xdf101729,12,35);}
 public boolean motion(){return !effects.reducedMotion();}
 public boolean blur(){return effects.glassEffect()&&effects.blur()>0;}
 /** Native kernel units are 0–10; a positive theme strength stays visible at least at 1. */
 public int menuBlurRadius(int vanilla,boolean ownScreen,boolean enabled){return !ownScreen?vanilla:!enabled||!blur()?0:Math.clamp((int)Math.round(effects.blur()/10),1,10);}
 public double transitionMillis(){return Math.clamp(700-effects.speed()*3,360,700);}
 public ThemeConfig withPanel(int value){return new ThemeConfig(accent,text,muted,value,radius,wobble,effects);}
 public static ThemeConfig read(Path path)throws Exception{
  if(!Files.exists(path))return defaults();if(Files.size(path)>8_500_000)throw new IllegalArgumentException("Theme file is too large");
  var value=AtomicJson.GSON.fromJson(Files.readString(path),com.google.gson.JsonObject.class);
  if(value==null||!value.has("schemaVersion")||value.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("Unsupported theme schema");
  boolean reduced=flag(value,"reducedMotion",false),glass=flag(value,"glassEffect",true);
  var fx=new Effects(color(value,"secondary",0xff45d8ff),flag(value,"hover",true),reduced,glass,flag(value,"glowEffect",true),flag(value,"reflectionEffect",true),flag(value,"proximityEffect",false),flag(value,"wobbleEffect",true),number(value,"blur",42),number(value,"glow",28),number(value,"reflection",35),number(value,"shadow",40),number(value,"proximity",0),number(value,"lift",18),number(value,"tilt",0),number(value,"speed",55),number(value,"stiffness",55),number(value,"damping",65));
  double transparency=number(value,"transparency",24);int opacity=glass?(int)(120+transparency*1.2):250,tint=color(value,"tint",0xff101729),wobble=(int)number(value,"wobble",35);
  return new ThemeConfig(color(value,"accent",0xff3478ff),color(value,"text",0xfff7faff),color(value,"muted",0xff98a8c5),(opacity<<24)|(tint&0xffffff),(int)(4+number(value,"radius",55)*.14),reduced?0:wobble,fx);
 }
 private static boolean flag(JsonObject v,String key,boolean fallback){if(!v.has(key))return fallback;var value=v.get(key);if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Invalid theme flag: "+key);return value.getAsBoolean();}
 private static int color(com.google.gson.JsonObject v,String key,int fallback){if(!v.has(key))return fallback;String s=v.get(key).getAsString();if(!s.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Invalid theme color");return 0xff000000|Integer.parseInt(s.substring(1),16);}
 private static double number(com.google.gson.JsonObject v,String key,double fallback){double n=v.has(key)?v.get(key).getAsDouble():fallback;if(!Double.isFinite(n)||n<0||n>100)throw new IllegalArgumentException("Invalid theme value");return n;}
}
