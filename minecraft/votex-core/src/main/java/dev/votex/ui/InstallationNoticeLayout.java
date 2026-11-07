package dev.votex.ui;

/** Workspace status uses the lower edge, below fixed controls; gameplay retains the full card. */
public record InstallationNoticeLayout(float scale,int x,int y,int width,int height,int viewportWidth,int viewportHeight,boolean ribbon) {
 public static InstallationNoticeLayout measure(int guiWidth,int guiHeight,double guiScale,boolean workspace){
  var ui=WorkspaceLayout.measure(guiWidth,guiHeight,guiScale);int width=workspace?ui.logicalWidth()-24:Math.min(360,ui.logicalWidth()-24),height=workspace?18:105;
  return new InstallationNoticeLayout(ui.scale(),workspace?12:ui.logicalWidth()-width-16,workspace?ui.logicalHeight()-height:18,width,height,ui.logicalWidth(),ui.logicalHeight(),workspace);
 }
 public boolean contains(double guiX,double guiY){double px=guiX/scale,py=guiY/scale;return px>=x&&px<x+width&&py>=y&&py<y+height;}
}
