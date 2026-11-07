package dev.votex.motion;

/**
 * Fixed-length, render-only contact solve for two consenting upright player models.
 * Entity positions, attribute scale, camera and input never enter the output.
 * The torso rotates around the existing hip; the arm cube and its child sleeve
 * keep their authored size. Both players target the same world midpoint/height.
 */
public final class HandshakeRig {
 public static final double GRIP_Y=9,PLAYER_MODEL_SCALE=.9375,MODEL_ORIGIN=1.501;
 public static final double MAX_DISTANCE=1.8,MIN_HORIZONTAL=.45,MAX_LEAN=1.15,MIN_LEAN=-.16,PUMP=.012;
 private HandshakeRig(){}
 public static double gripX(boolean slim){return slim?-.5:-1;}
 public static double blend(double seconds){return EmoteTimeline.smooth((seconds-.15)/.55)*EmoteTimeline.smooth((3.2-seconds)/.45);}
 public static double pump(double seconds){return PUMP*Math.sin((seconds-.7)*8);}
 private static double unit(double scale){return PLAYER_MODEL_SCALE*scale/16;}

 /** Canonical A's feet are at Y=0; B's feet at deltaY. Null means a physically unreachable stance. */
 public static Plan plan(double distance,double deltaY,double scaleA,double scaleB,boolean slimA,boolean slimB){
  if(!Double.isFinite(distance)||!Double.isFinite(deltaY)||!Double.isFinite(scaleA)||!Double.isFinite(scaleB)||scaleA<=0||scaleB<=0||distance<MIN_HORIZONTAL||Math.hypot(distance,deltaY)>MAX_DISTANCE+.0001)return null;
  double low=Math.max(.5*scaleA,deltaY+.5*scaleB),high=Math.min(1.6*scaleA,deltaY+1.6*scaleB),preferred=deltaY/2+1.05*(scaleA+scaleB)/2;
  if(low>=high)return null;double best=Double.POSITIVE_INFINITY,height=Double.NaN;
  // This bounded search runs once per changed pair geometry, never once per rendered vertex/frame.
  for(int i=0;i<=384;i++){
   double h=low+(high-low)*i/384,score=2*(h-preferred)*(h-preferred);boolean reachable=true;
   for(int participant=0;participant<2&&reachable;participant++)for(int edge=-1;edge<=1;edge++){
    double lean=lean(distance,h+edge*PUMP-(participant==0?0:deltaY),participant==0?scaleA:scaleB,participant==0?slimA:slimB);
    if(!Double.isFinite(lean)){reachable=false;break;}score+=lean*lean/3+(lean<0?lean*lean*2:0);
   }
   if(reachable&&score<best){best=score;height=h;}
  }
  return Double.isFinite(height)?new Plan(distance,deltaY,scaleA,scaleB,slimA,slimB,height):null;
 }
 /** Immutable geometric solution. pose output = torso pitch, native arm XYZ, common contact Y. */
 public record Plan(double distance,double deltaY,double scaleA,double scaleB,boolean slimA,boolean slimB,double height){
  public void pose(boolean first,double seconds,double[] out){
   if(out.length<5)throw new IllegalArgumentException("Five solution channels required");
   double scale=first?scaleA:scaleB,base=first?0:deltaY,h=height+pump(seconds),u=unit(scale),bend=lean(distance,h-base,scale,first?slimA:slimB);
   if(!Double.isFinite(bend))throw new IllegalStateException("Contact escaped the prevalidated envelope");
   double shoulderY=u*(MODEL_ORIGIN*16-12+10*Math.cos(bend));
   double dx=5,dy=(base+shoulderY-h)/u,dz=-(distance/2-10*u*Math.sin(bend))/u,c=gripX(first?slimA:slimB);
   // Ry(yaw) * Rx(pitch) maps the actual palm point (c,9,0) to the target.
   double pitch=-Math.acos(Math.clamp(dy/GRIP_Y,-1,1)),initial=Math.atan2(c,GRIP_Y*Math.sin(pitch)),goal=Math.atan2(dx,dz);
   out[0]=bend;out[1]=pitch;out[2]=Math.atan2(Math.sin(goal-initial),Math.cos(goal-initial));out[3]=0;out[4]=h;
  }
 }
 private static double lean(double distance,double height,double scale,boolean slim){
  double u=unit(scale),torso=10*u,forward=distance/2,up=height-u*(MODEL_ORIGIN*16-12),radius=Math.hypot(forward,up),gx=gripX(slim);
  double reachSquared=u*u*(GRIP_Y*GRIP_Y+gx*gx-25),cos=(radius*radius+torso*torso-reachSquared)/(2*radius*torso);
  if(radius<1e-8||cos< -1||cos>1)return Double.NaN;
  double center=Math.atan2(forward,up),spread=Math.acos(Math.clamp(cos,-1,1)),a=center-spread,b=center+spread;
  boolean av=a>=MIN_LEAN&&a<=MAX_LEAN,bv=b>=MIN_LEAN&&b<=MAX_LEAN;
  return av&&bv?Math.abs(a)<Math.abs(b)?a:b:av?a:bv?b:Double.NaN;
 }
}
