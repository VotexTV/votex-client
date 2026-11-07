package dev.votex.ui;

/** Physical-pixel social feedback above the vanilla hotbar, independent of GUI scale. */
public record SocialToastLayout(float scale,int x,int y,int width,int height,int viewportWidth,int viewportHeight) {
 public static SocialToastLayout measure(int gw,int gh,double guiScale,int lines){
  var ui=WorkspaceLayout.measure(gw,gh,guiScale);
  int width=Math.min(460,ui.logicalWidth()-24),height=32+14*Math.clamp(lines,1,3);
  // Reserve the native hotbar/XP area in GUI units, then convert to our logical density.
  int y=Math.max(12,(int)Math.floor((gh-58)/ui.scale())-height);
  return new SocialToastLayout(ui.scale(),(ui.logicalWidth()-width)/2,y,width,height,ui.logicalWidth(),ui.logicalHeight());
 }
}
