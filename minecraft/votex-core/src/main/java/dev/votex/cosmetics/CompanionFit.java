package dev.votex.cosmetics;

/** Shoulder clearance in native model pixels, about the shoulder anchor x=-6. */
public record CompanionFit(float scale, float x, float y) {
 public static final CompanionFit IDENTITY=new CompanionFit(1,0,0);
 public static CompanionFit of(float minX,float maxX,float minY,float maxY,boolean headset){
  if(!Float.isFinite(minX)||!Float.isFinite(maxX)||!Float.isFinite(minY)||!Float.isFinite(maxY)||maxX<=minX||maxY<=minY)return IDENTITY;
  float scale=headset?Math.min(1,Math.min(4.5f/(maxX-minX),3f/(maxY-minY))):1;
  float innerEdge=-6+(maxX+6)*scale;
  float y=headset?-.15f-maxY*scale:Math.min(0,-.15f-maxY*scale);
  return new CompanionFit(scale,Math.min(0,-4.65f-innerEdge),y);
 }
}
