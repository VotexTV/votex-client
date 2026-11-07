package dev.votex.ui;
/** Physical text density, bounded scroll and gap-aware hit regions shared by chat tools. */
public final class ChatLayout {
 public static final int ROW_STEP=56,ROW_HEIGHT=50;
 private ChatLayout(){}
 public record History(float scale,int viewportWidth,int viewportHeight,int left,int top,int width,int height){
  public int listTop(){return top+116;} public int listHeight(){return Math.max(50,height-178);}
  public int listLeft(){return left+18;} public int listWidth(){return width-44;}
  public double maxScroll(int count){return Math.max(0,count*ROW_STEP-6-listHeight());}
  public boolean inList(double x,double y){return x>=listLeft()&&x<listLeft()+listWidth()&&y>=listTop()&&y<listTop()+listHeight();}
  public int rowAt(double x,double y,double scroll,int count){if(!inList(x,y))return -1;double row=(y-listTop()+scroll)/ROW_STEP;int i=(int)Math.floor(row);return i>=0&&i<count&&(row-i)*ROW_STEP<ROW_HEIGHT?i:-1;}
  public int thumbHeight(int count){return Math.min(listHeight(),Math.max(28,(int)Math.round(listHeight()*listHeight()/(double)Math.max(1,count*ROW_STEP-6))));}
  public double thumbY(double scroll,int count){return listTop()+(listHeight()-thumbHeight(count))*scroll/Math.max(1,maxScroll(count));}
  public double scrollFromThumb(double y,double grab,int count){return Math.clamp((y-listTop()-grab)/Math.max(1,listHeight()-thumbHeight(count))*maxScroll(count),0,maxScroll(count));}
 }
 public record Context(float scale,int viewportWidth,int viewportHeight,int left,int top,int width,int height){public boolean contains(double x,double y){return x>=left&&x<left+width&&y>=top&&y<top+height;}}
 public static History history(int gw,int gh,double guiScale){var ui=WorkspaceLayout.measure(gw,gh,guiScale);int w=Math.min(800,ui.logicalWidth()-32),h=Math.min(520,ui.logicalHeight()-32);return new History(ui.scale(),ui.logicalWidth(),ui.logicalHeight(),(ui.logicalWidth()-w)/2,(ui.logicalHeight()-h)/2,w,h);}
 public static Context context(int gw,int gh,double guiScale,double x,double y){var ui=WorkspaceLayout.measure(gw,gh,guiScale);int w=Math.min(290,ui.logicalWidth()-24),h=Math.min(226,ui.logicalHeight()-24);return new Context(ui.scale(),ui.logicalWidth(),ui.logicalHeight(),(int)Math.clamp(x/ui.scale(),12,Math.max(12,ui.logicalWidth()-w-12)),(int)Math.clamp(y/ui.scale(),12,Math.max(12,ui.logicalHeight()-h-12)),w,h);}
}
