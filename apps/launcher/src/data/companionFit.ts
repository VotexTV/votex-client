import type { SurfacePart } from "./cosmeticCatalog";
export function companionFit(parts: SurfacePart[], headset: boolean) {
  let minX=Infinity,maxX=-Infinity,minY=Infinity,maxY=-Infinity;
  for(const part of parts)for(let i=0;i+2<part.positions.length;i+=3){minX=Math.min(minX,part.positions[i]);maxX=Math.max(maxX,part.positions[i]);minY=Math.min(minY,part.positions[i+1]);maxY=Math.max(maxY,part.positions[i+1]);}
  if(![minX,maxX,minY,maxY].every(Number.isFinite)||maxX<=minX||maxY<=minY)return {scale:1,x:0,y:0};
  const scale=headset?Math.min(1,4.5/(maxX-minX),3/(maxY-minY)):1;
  return {scale,x:Math.min(0,-4.65-(-6+(maxX+6)*scale)),y:headset?-.15-maxY*scale:Math.min(0,-.15-maxY*scale)};
}
