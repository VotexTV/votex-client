package dev.votex.ui;

/** Editor tools have readable physical density; HUD positions retain Minecraft GUI coordinates. */
public record HudEditorLayout(float scale,int logicalWidth,int logicalHeight,int x,int y,int width,int height) {
 public static HudEditorLayout measure(int guiWidth,int guiHeight,double guiScale){var density=WorkspaceLayout.measure(guiWidth,guiHeight,guiScale);int w=Math.min(520,density.logicalWidth()-24);return new HudEditorLayout(density.scale(),density.logicalWidth(),density.logicalHeight(),(density.logicalWidth()-w)/2,density.logicalHeight()-136,w,124);}
 public int controlsX(){return x+(width-424)/2;}
 public HudEditorLayout atTop(){return new HudEditorLayout(scale,logicalWidth,logicalHeight,x,12,width,height);}
 public boolean contains(double guiX,double guiY){double xx=guiX/scale,yy=guiY/scale;return xx>=x&&xx<=x+width&&yy>=y&&yy<=y+height;}
}
