import {describe,it,expect} from 'vitest';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import catalog from '../apps/launcher/src/data/cosmetic-manifest.json';
import sources from '../assets-source/third-party/poly-curated/manifest.json';
import reviewed from '../assets-source/third-party/poly-curated/reviewed.json';
import nativeReviewed from '../assets-source/third-party/poly-curated/native-reviewed.json';
import policy from '../assets-source/third-party/poly-curated/fitting-policy.json';
const hash=(data:Buffer|string)=>createHash('sha256').update(data).digest('hex');
const models=catalog.filter(item=>item.id.startsWith('creator-poly-')).map(item=>JSON.parse(readFileSync(`apps/launcher/public/assets/catalog/${item.id}.json`,'utf8')));
function geometry(parts:any[]){
 const min=[Infinity,Infinity,Infinity],max=[-Infinity,-Infinity,-Infinity];for(const part of parts)for(let i=0;i<part.positions.length;i++){const k=i%3;min[k]=Math.min(min[k],part.positions[i]);max[k]=Math.max(max[k],part.positions[i]);}
 const center=min.map((x,k)=>(x+max[k])/2),scale=Math.max(...max.map((x,k)=>x-min[k])),triangles=new Set<string>();for(const part of parts)for(let i=0;i<part.quads.length;i+=4)triangles.add(part.quads.slice(i,i+3).map((index:number)=>[0,1,2].map(k=>Math.round((part.positions[index*3+k]-center[k])/scale*10000)).join(',')).sort().join('/'));return hash([...triangles].sort().join(';'));
}
describe('individually licensed fitted catalog expansion',()=>{
 it('has over300 usable non-cape entries and exact retained source/approval/attribution for every addition',()=>{
  expect(catalog.filter(item=>item.slot!=='cape').length).toBeGreaterThan(300);expect(models.length).toBe(reviewed.approvedIds.length);
  expect(nativeReviewed.status).toBe('native-neutral-attachment-reviewed');
  expect(nativeReviewed.items.length).toBe(catalog.length);
  expect(Object.keys(nativeReviewed.inspectedSheets)).toHaveLength(20);
  const nativeHashes=new Map(nativeReviewed.items.map(item=>[item.id,item.modelSha256]));
  for(const item of catalog)expect(hash(readFileSync(`apps/launcher/public/assets/catalog/${item.id}.json`)),item.id+' changed since native visual review').toBe(nativeHashes.get(item.id));
  const credits=readFileSync('minecraft/votex-core/src/main/resources/licenses/Poly-Catalog.md','utf8');
  for(const model of models){const original=sources.items.find(item=>item.id===model.id)!;expect(original).toBeDefined();expect(hash(readFileSync(`assets-source/third-party/poly-curated/${original.sourceFile}`))).toBe(original.sourceSha256);expect(model.sourceAsset.sha256).toBe(original.sourceSha256);
   const evidence=JSON.parse(readFileSync(`assets-source/third-party/poly-curated/${original.publicID}.source.json`,'utf8'));expect(evidence.Licence).toBe(original.license);expect(evidence.Creator.Username).toBe(model.creator.name);expect(['CC0 1.0','CC-BY 3.0']).toContain(original.license);
   expect(hash(readFileSync(`apps/launcher/public/assets/catalog/${model.id}.json`))).toBe(reviewed.sha256ById[model.id as keyof typeof reviewed.sha256ById]);expect(credits).toContain(original.sourceUrl);expect(credits).toContain(original.licenseUrl);expect(model.creator.changes.length).toBeGreaterThan(30);
  }
  for(const id of Object.keys(policy.excludedIds))expect(catalog.some(item=>item.id===id)).toBe(false);expect(models.some(model=>model.slot==='feet')).toBe(false);
 });
 it('does not add repeated fitted geometry, palette variants or an identity already in the base catalog',()=>{
  const seen=new Map<string,string>();for(const item of catalog.filter(item=>!item.id.startsWith('creator-poly-')&&item.slot!=='cape')){const model=JSON.parse(readFileSync(`apps/launcher/public/assets/catalog/${item.id}.json`,'utf8'));seen.set(geometry(model.parts),item.id);}
  for(const model of models){const identity=geometry(model.parts);expect(seen.get(identity),model.id+' repeats geometry').toBeUndefined();seen.set(identity,model.id);}
 });
 it('preserves native layout/UV/texture budgets and keeps fitted attachments outside the reference skin',()=>{
  for(const model of models){let vertices=0;for(const part of model.parts){expect(part.positions.length%3).toBe(0);expect(part.positions.length).toBeLessThanOrEqual(60000);expect(part.normals.length).toBe(part.positions.length);expect(part.colors.length).toBe(part.positions.length);expect(part.quads.length%4).toBe(0);expect(part.quads.length).toBeLessThanOrEqual(24000);vertices+=part.quads.length;
   expect(part.positions.every((value:number)=>Number.isFinite(value)&&Math.abs(value)<=100),model.id+' bounded positions').toBe(true);expect(part.quads.every((index:number)=>Number.isInteger(index)&&index>=0&&index<part.positions.length/3),model.id+' bounded indices').toBe(true);
   if(part.texture){expect(part.uvs.length).toBe(part.positions.length/3*2);const bytes=readFileSync(`minecraft/votex-core/src/main/resources/assets/votex_client/textures/cosmetics/${part.texture}`);expect(bytes.subarray(0,8)).toEqual(Buffer.from([137,80,78,71,13,10,26,10]));expect(bytes.readUInt32BE(16)).toBeLessThanOrEqual(1024);expect(bytes.readUInt32BE(20)).toBeLessThanOrEqual(1024);}
   let maxX=-Infinity,maxY=-Infinity,minZ=Infinity;for(let i=0;i<part.positions.length;i+=3){maxX=Math.max(maxX,part.positions[i]);maxY=Math.max(maxY,part.positions[i+1]);minZ=Math.min(minZ,part.positions[i+2]);}if(model.slot==='head'&&model.name!=='Headphones')expect(maxY).toBeLessThanOrEqual(-8.249);if(model.slot==='back')expect(minZ).toBeGreaterThanOrEqual(2.599);if(model.slot==='pet')expect(maxX).toBeLessThanOrEqual(-5.049);
  }expect(vertices).toBeLessThanOrEqual(100000);}
 });
});
