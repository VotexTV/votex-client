package dev.votex.appearance;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Maps a portrait motif to every face of Minecraft's 10x16x1 cape box. */
public final class CapeAtlas {
 private CapeAtlas(){}
 public record Crop(double x,double y,double width,double height){}
 public static Crop crop(int width,int height,double aspect,double zoom,double cx,double cy){
  if(width<1||height<1||!Double.isFinite(aspect)||aspect<=0||!Double.isFinite(zoom)||!Double.isFinite(cx)||!Double.isFinite(cy))throw new IllegalArgumentException("Invalid crop");
  double w=Math.min(width,height*aspect)/Math.clamp(zoom,1,8),h=w/aspect;
  return new Crop(Math.clamp(cx*width-w/2,0,width-w),Math.clamp(cy*height-h/2,0,height-h),w,h);
 }
 public static BufferedImage create(BufferedImage source,Crop crop,boolean readyAtlas){
  BufferedImage sampled=new BufferedImage(readyAtlas?64:10,readyAtlas?32:16,BufferedImage.TYPE_INT_ARGB);
  var g=sampled.createGraphics();try{g.setComposite(java.awt.AlphaComposite.Src);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);int x=(int)Math.floor(crop.x),y=(int)Math.floor(crop.y),right=Math.min(source.getWidth(),Math.max(x+1,(int)Math.ceil(crop.x+crop.width))),bottom=Math.min(source.getHeight(),Math.max(y+1,(int)Math.ceil(crop.y+crop.height)));g.drawImage(source,0,0,sampled.getWidth(),sampled.getHeight(),x,y,right,bottom,null);}finally{g.dispose();}
  if(readyAtlas)return sampled;
  BufferedImage atlas=new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB);
  for(int y=0;y<16;y++)for(int x=0;x<10;x++){int argb=sampled.getRGB(x,y);atlas.setRGB(1+x,1+y,argb);atlas.setRGB(12+x,1+y,argb);}
  for(int y=0;y<16;y++){atlas.setRGB(0,y+1,sampled.getRGB(0,y));atlas.setRGB(11,y+1,sampled.getRGB(9,y));}
  for(int x=0;x<10;x++){atlas.setRGB(x+1,0,sampled.getRGB(x,0));atlas.setRGB(x+11,0,sampled.getRGB(x,15));}
  sampled.flush();return atlas;
 }
}
