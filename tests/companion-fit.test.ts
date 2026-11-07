import { expect,it } from "vitest";
import { readFileSync } from "node:fs";
import catalog from "../apps/launcher/src/data/cosmetic-manifest.json";
import { companionFit } from "../apps/launcher/src/data/companionFit";

it("keeps every catalog companion outside the static head/earcup envelope",()=>{
 for(const item of catalog.filter(item=>item.slot==="pet")){
  const model=JSON.parse(readFileSync(`apps/launcher/public/assets/catalog/${item.id}.json`,"utf8"));
  for(const headset of [false,true]){
   const fit=companionFit(model.parts,headset);
   let maxX=-Infinity,minY=Infinity,maxY=-Infinity;
   for(const part of model.parts)for(let i=0;i+2<part.positions.length;i+=3){
    const x=-6+(part.positions[i]+6)*fit.scale+fit.x,y=part.positions[i+1]*fit.scale+fit.y;
    maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
   }
   expect(maxX,item.id).toBeLessThanOrEqual(-4.65+1e-6);
   if(headset)expect(minY,item.id).toBeGreaterThanOrEqual(-3.15-1e-6);
   expect(maxY,item.id).toBeLessThanOrEqual(-.15+1e-6);
  }
 }
});
