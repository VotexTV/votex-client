package dev.votex.ui;

/** A displayed frame and its exact inverse; input follows the frame the player saw. */
public final class WorkspaceMotion {
 private WorkspaceMotion(){}
 public record Frame(float scale,float x,float y){
  public double localX(double x){return (x-this.x)/scale;}
  public double localY(double y){return (y-this.y)/scale;}
  public double screenX(double x){return x*scale+this.x;}
  public double screenY(double y){return y*scale+this.y;}
 }
 public static Frame frame(long now,long opened,long closing,boolean enabled,boolean workspaceTransition,int width,int height){
  return frame(now,opened,closing,enabled,workspaceTransition,width,height,520);
 }
 public static Frame frame(long now,long opened,long closing,boolean enabled,boolean workspaceTransition,int width,int height,double durationMillis){
  if(!Double.isFinite(durationMillis)||durationMillis<=0)throw new IllegalArgumentException("Invalid motion duration");
  float progress=enabled?(float)Math.clamp((now-opened)/(durationMillis*1_000_000d),0,1):1;
  float enter=1-(float)Math.pow(1-progress,4);
  float out=closing==0?0:(float)Math.clamp((now-closing)/180_000_000d,0,1);
  float scale=workspaceTransition?1:1-(1-enter)*.065f-out*.05f;
  return new Frame(scale,width/2f*(1-scale),height/2f*(1-scale)+(1-enter)*12);
 }
}
