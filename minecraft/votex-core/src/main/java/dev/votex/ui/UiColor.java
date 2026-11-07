package dev.votex.ui;

/** Material-independent contrast: disabling transparency preserves the perceived fill. */
public final class UiColor {
 private UiColor(){}
 public static int opaqueOver(int fill,int background){
  double alpha=(fill>>>24)/255d;int result=0xff000000;
  for(int shift:new int[]{0,8,16})result|=(int)Math.round(((fill>>>shift)&255)*alpha+((background>>>shift)&255)*(1-alpha))<<shift;
  return result;
 }
 public static double contrast(int a,int b){double first=luminance(a),second=luminance(b);return (Math.max(first,second)+.05)/(Math.min(first,second)+.05);}
 public static int foreground(int background,int preferred){
  if(contrast(background,preferred)>=4.5)return preferred;
  int dark=0xff000000,light=0xffffffff;return contrast(background,dark)>contrast(background,light)?dark:light;
 }
 private static double luminance(int color){return channel(color>>>16)*.2126+channel(color>>>8)*.7152+channel(color)*.0722;}
 private static double channel(int value){double v=(value&255)/255d;return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);}
}
