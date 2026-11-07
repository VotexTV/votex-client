package dev.votex.ui;

/** Native workspace density uses physical pixels; small windows reflow/scroll
 * rather than reducing an entire desktop layout below readable type sizes.
 */
public record WorkspaceLayout(float scale,int logicalWidth,int logicalHeight,int width,int panelHeight,int pageHeight,boolean compact) {
 public static WorkspaceLayout measure(int guiWidth,int guiHeight,double guiScale){
  if(guiWidth<=0||guiHeight<=0||!Double.isFinite(guiScale)||guiScale<=0)throw new IllegalArgumentException("Invalid viewport");
  double pixels=Math.clamp(Math.min(guiWidth*guiScale/1000,guiHeight*guiScale/650),1.3,1.7);
  float scale=(float)(pixels/guiScale);int w=Math.round(guiWidth/scale),h=Math.round(guiHeight/scale),panel=Math.min(620,h-34);
  return new WorkspaceLayout(scale,w,h,Math.min(1000,w-36),panel,Math.max(540,panel),w<790||h<500);
 }
 public int sidebar(){return compact?74:174;}
 public int contentWidth(){return width-sidebar()-22;}
 public int maxScroll(){return pageHeight-panelHeight;}
 public double bodyPixels(double guiScale){return 11*scale*guiScale;}
 /** Height of a continuous list page whose first row and viewport bottom are panel-relative. */
 public static int flowPageHeight(int panelHeight,int viewportBottom,int firstRowY,int rowCount,int rowStep,int itemHeight){
  if(panelHeight<=0||viewportBottom<0||viewportBottom>panelHeight||firstRowY<0||rowCount<0||rowStep<=0||itemHeight<0)throw new IllegalArgumentException("Invalid content flow");
  if(rowCount==0)return panelHeight;
  long contentBottom=(long)firstRowY+(long)(rowCount-1)*rowStep+itemHeight;
  long pageHeight=(long)panelHeight+Math.max(0,contentBottom-viewportBottom);
  return (int)Math.min(Integer.MAX_VALUE,pageHeight);
 }
}
