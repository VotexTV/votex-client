package dev.votex.ui;
import dev.votex.config.ThemeConfig;

/** Critically damped bounded channels; input rectangles never follow the visual spring. */
public final class HoverMotion {
 private static final class Channel {
  double value,velocity;
  void step(double target,double dt,double omega,boolean animate){
   if(!animate){value=target;velocity=0;return;}
   double offset=value-target,derivative=velocity+omega*offset,decay=Math.exp(-omega*dt),next=target+(offset+derivative*dt)*decay;
   velocity=(velocity-omega*derivative*dt)*decay;value=Math.clamp(next,0,1);if(value!=next)velocity=0;
   if(Math.abs(value-target)<.0001&&Math.abs(velocity)<.0001){value=target;velocity=0;}
  }
 }
 private final Channel hover=new Channel(),near=new Channel(),lightX=new Channel(),lightY=new Channel();
 public HoverMotion(){lightX.value=lightY.value=.5;}
 public void step(double seconds,boolean over,boolean active,double mx,double my,int x,int y,int w,int h,ThemeConfig theme,boolean menuMotion){
  var fx=theme.effects();double dt=Math.clamp(Double.isFinite(seconds)?seconds:0,0,.1),rate=fx.response();boolean animate=menuMotion&&theme.motion();
  hover.step(active&&fx.hover()&&over?1:0,dt,rate,animate&&fx.hover()&&active);
  double radius=40+fx.proximity()*1.6,distance=Math.hypot(Math.max(Math.max(x-mx,0),mx-x-w),Math.max(Math.max(y-my,0),my-y-h));boolean light=active&&fx.light()>0;
  near.step(light?Math.max(0,1-distance/radius):0,dt,rate,animate&&light);
  // The cursor is an input, not a spring target. Only opacity/lift ease in and out.
  lightX.step(Math.clamp((mx-x)/Math.max(1,w),0,1),0,rate,false);lightY.step(Math.clamp((my-y)/Math.max(1,h),0,1),0,rate,false);
 }
 public float hover(){return (float)hover.value;}public float near(){return (float)near.value;}public float x(){return (float)lightX.value;}public float y(){return (float)lightY.value;}
}
