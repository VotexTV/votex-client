package dev.votex.ui;

/** Viewport-relative window memory keeps controls reachable after monitor changes. */
public final class WorkspaceWindow {
 private WorkspaceWindow(){}
 public static final class Saved {public double x,y,width,height;public boolean valid(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(width)&&Double.isFinite(height)&&x>=0&&y>=0&&width>=0&&height>=0&&x<=1&&y<=1&&width<=1&&height<=1;}}
 public record Rect(int x,int y,int width,int height){}
 public static Rect restore(Saved saved,int width,int height,Rect fallback){
  if(saved==null||!saved.valid()||saved.width==0||saved.height==0)return clamp(fallback,width,height);
  return clamp(new Rect((int)Math.round(saved.x*width),(int)Math.round(saved.y*height),(int)Math.round(saved.width*width),(int)Math.round(saved.height*height)),width,height);
 }
 public static Saved remember(Rect r,int width,int height){var saved=new Saved();saved.x=r.x/(double)width;saved.y=r.y/(double)height;saved.width=r.width/(double)width;saved.height=r.height/(double)height;return saved;}
 public static Rect clamp(Rect r,int vw,int vh){int w=Math.clamp(r.width,Math.min(546,vw-16),vw-16),h=Math.clamp(r.height,Math.min(330,vh-16),vh-16);return new Rect(Math.clamp(r.x,8,Math.max(8,vw-w-8)),Math.clamp(r.y,8,Math.max(8,vh-h-8)),w,h);}
 /** Left=1 right=2 top=4 bottom=8, move=16. The opposite corner stays fixed. */
 public static Rect drag(Rect start,int edge,int dx,int dy,int vw,int vh){
  start=clamp(start,vw,vh);
  if(edge==16)return clamp(new Rect(start.x+dx,start.y+dy,start.width,start.height),vw,vh);
  int x=start.x,y=start.y,right=x+start.width,bottom=y+start.height,minW=Math.min(546,vw-16),minH=Math.min(330,vh-16);
  if((edge&1)!=0)x=Math.clamp(x+dx,8,right-minW);if((edge&2)!=0)right=Math.clamp(right+dx,x+minW,vw-8);
  if((edge&4)!=0)y=Math.clamp(y+dy,8,bottom-minH);if((edge&8)!=0)bottom=Math.clamp(bottom+dy,y+minH,vh-8);
  return new Rect(x,y,right-x,bottom-y);
 }
 public static int edge(Rect r,double x,double y){if(x<r.x-6||x>r.x+r.width+6||y<r.y-6||y>r.y+r.height+6)return 0;return (Math.abs(x-r.x)<=7?1:Math.abs(x-r.x-r.width)<=7?2:0)|(Math.abs(y-r.y)<=7?4:Math.abs(y-r.y-r.height)<=7?8:0);}
}
