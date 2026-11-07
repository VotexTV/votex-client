// Reproducible, supersampled UI primitives and catalog previews. No runtime SVG engine.
import fs from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
const sharp=(await import(pathToFileURL(process.argv[2]).href)).default;
const selectedIcons=new Set((process.argv.find(arg=>arg.startsWith('--icons='))?.slice(8)??'').split(',').filter(Boolean));
const root=path.resolve(import.meta.dirname,'../minecraft/votex-core/src/main/resources/assets/votex_client');
await fs.mkdir(path.join(root,'textures/gui/sprites/ui'),{recursive:true});
await fs.mkdir(path.join(root,'textures/previews'),{recursive:true});
if(!selectedIcons.size)for(const r of [3,4,6,8,10,12,16,20]){
 const size=r*3+4;
 for(const outline of [false,true]){
  const name=`${outline?'edge':'round'}_${r}.png`,file=path.join(root,'textures/gui/sprites/ui',name);
  const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="${size*4}" height="${size*4}" viewBox="0 0 ${size} ${size}"><rect x=".25" y=".25" width="${size-.5}" height="${size-.5}" rx="${r}" fill="${outline?'none':'white'}" stroke="${outline?'white':'none'}" stroke-width=".6"/></svg>`;
  await sharp(Buffer.from(svg)).png().toFile(file);
  await fs.writeFile(file+'.mcmeta',JSON.stringify({gui:{scaling:{type:'nine_slice',width:size,height:size,border:r+1,stretch_inner:true}}}));
 }
}
const icons={
 minus:'M5 12h14',
 plus:'M5 12h14M12 5v14',
 circle:'M3 12a9 9 0 1 0 18 0 9 9 0 1 0-18 0',
 arrow_left:'M20 12H4m6-6-6 6 6 6',
 home:'M3 11 12 3l9 8M5 10v10h5v-6h4v6h5V10',
 modules:'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z',
 cosmetics:'m8 3-5 4 3 5 2-1v10h8V11l2 1 3-5-5-4-2 3h-4z',
 skins:'M7 3h10v7H7zM5 13h14v8H5z',
 emotes:'M9 4a3 3 0 1 0 6 0 3 3 0 1 0-6 0M12 10v6m0-4-7 3m7-3 7-3m-7 7-5 6m5-6 5 6',
 performance:'m13 2-9 12h7l-1 8 10-13h-7z',
 settings:'M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1zM8 12a4 4 0 1 0 8 0 4 4 0 1 0-8 0',
 friends:'M6 8a3 3 0 1 0 6 0 3 3 0 1 0-6 0M3 21v-3a6 6 0 0 1 12 0v3m1-16a3 3 0 0 1 0 6m2 4a5 5 0 0 1 3 5',
 search:'M3 10a7 7 0 1 0 14 0 7 7 0 1 0-14 0m12 5 6 6',
 close:'m6 6 12 12M6 18 18 6',
 arrow:'M4 12h16m-6-6 6 6-6 6',
 play:'m8 4 12 8-12 8z',
 check:'m4 12 5 5L20 6',
 hud:'M3 3h18v18H3zM3 8h18M8 8v13',
 cube:'m12 2 9 5v10l-9 5-9-5V7zm-9 5 9 5 9-5m-9 5v10',
 globe:'M2 12a10 10 0 1 0 20 0 10 10 0 1 0-20 0m0 0h20M12 2a21 21 0 0 1 0 20 21 21 0 0 1 0-20',
 voice:'M9 5a3 3 0 0 1 6 0v7a3 3 0 0 1-6 0zM5 11v1a7 7 0 0 0 14 0v-1m-7 8v3m-4 0h8',
 games:'M6 6h12l4 12-3 2-4-4H9l-4 4-3-2zm0 4v6m-3-3h6m7-2h1m2 3h1',
 cloud:'M6 18a5 5 0 1 1 1-10 6 6 0 0 1 11 2 4 4 0 0 1 0 8z',
 heart:'M12 21 3 12A6 6 0 0 1 12 4a6 6 0 0 1 9 8z',
 refresh:'M20 7v5h-5M4 17v-5h5m10-4A8 8 0 0 0 5 6m0 10a8 8 0 0 0 14 2',
 upload:'M12 16V3m-5 5 5-5 5 5M4 15v6h16v-6',
 chat:'M3 3h18v14H9l-6 4zM7 7h10M7 12h7',
 shield:'m12 2 9 4v6c0 5-9 10-9 10S3 17 3 12V6z',
 sun:'M8 12a4 4 0 1 0 8 0 4 4 0 1 0-8 0M12 1v3m0 16v3M1 12h3m16 0h3M4 4l2 2m12 12 2 2M4 20l2-2M18 6l2-2'
};
for(const id of selectedIcons)if(!(id in icons))throw new Error(`Unknown icon: ${id}`);
for(const [id,d]of Object.entries(icons)){
 if(selectedIcons.size&&!selectedIcons.has(id))continue;
 const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96" viewBox="0 0 24 24"><path d="${d}" fill="none" stroke="white" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
 await sharp(Buffer.from(svg)).png().toFile(path.join(root,'textures/gui/sprites/ui',id+'.png'));
}
if(selectedIcons.size){console.log(`Created ${selectedIcons.size} selected UI icons.`);process.exit(0);}
const catalog=JSON.parse(await fs.readFile(path.resolve(import.meta.dirname,'../apps/launcher/public/assets/catalog/index.json'),'utf8'));
for(const item of catalog){
 const source=path.resolve(import.meta.dirname,'../apps/launcher/public/assets/catalog',item.id+'.svg');
 await sharp(await fs.readFile(source),{density:96}).resize(384,300,{fit:'contain',background:'#00000000'}).png().toFile(path.join(root,'textures/previews',item.id+'.png'));
}
console.log(`Created ${Object.keys(icons).length} icons, glass primitives, ${catalog.length} real model previews.`);
