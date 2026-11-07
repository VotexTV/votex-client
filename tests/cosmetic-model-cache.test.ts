import {afterEach,describe,it,expect,vi} from 'vitest';
import catalog from '../apps/launcher/src/data/cosmetic-manifest.json';
afterEach(()=>{vi.unstubAllGlobals();vi.resetModules();});
describe('bounded parsed model cache',()=>{
 it('retains recently used models and evicts older geometry after16 entries',async()=>{
  vi.resetModules();const request=vi.fn(async(url:string)=>({ok:true,json:async()=>({id:url})}));vi.stubGlobal('fetch',request);const {cosmeticModel}=await import('../apps/launcher/src/data/cosmeticCatalog');const ids=catalog.slice(0,18).map(item=>item.id);
  const first=await cosmeticModel(ids[0]);for(const id of ids.slice(1,15))await cosmeticModel(id);expect(await cosmeticModel(ids[0])).toBe(first);for(const id of ids.slice(15,17))await cosmeticModel(id);expect(await cosmeticModel(ids[0])).toBe(first);expect(request).toHaveBeenCalledTimes(17);await cosmeticModel(ids[1]);expect(request).toHaveBeenCalledTimes(18);
 });
 it('an evicted failing request cannot delete a newer successful request for the same identity',async()=>{
  vi.resetModules();let reject!:(error:Error)=>void;const request=vi.fn().mockImplementationOnce(()=>new Promise((_resolve,rejectOld)=>{reject=rejectOld;})).mockImplementation(async()=>({ok:true,json:async()=>({schemaVersion:2})}));vi.stubGlobal('fetch',request);const {cosmeticModel}=await import('../apps/launcher/src/data/cosmeticCatalog');const ids=catalog.slice(0,17).map(item=>item.id);
  const old=cosmeticModel(ids[0]).catch(()=>undefined);for(const id of ids.slice(1))await cosmeticModel(id);const fresh=await cosmeticModel(ids[0]);reject(new Error('old request failed'));await old;expect(await cosmeticModel(ids[0])).toBe(fresh);expect(request).toHaveBeenCalledTimes(18);
 });
});
