/** Rasterize the actual mesh projections with the workspace's Sharp runtime. */
import fs from 'node:fs/promises';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
const sharp=(await import(pathToFileURL(process.argv[2]).href)).default;
const root=process.argv[3]?path.resolve(process.argv[3]):path.resolve(import.meta.dirname,'../work/catalog-expansion/staged');
const items=JSON.parse(await fs.readFile(path.join(root,'index.json'),'utf8'));
for(const item of items)for(const suffix of ['', '-fit']){
 const source=path.join(root,item.id+suffix+'.svg');
 await sharp(await fs.readFile(source),{density:96}).resize(384,300,{fit:'contain',background:'#00000000'}).png().toFile(path.join(root,item.id+suffix+'.png'));
}
const pageSize=16,columns=4,tileWidth=300,tileHeight=286;
for(let page=0;page<Math.ceil(items.length/pageSize);page++){
 const visible=items.slice(page*pageSize,(page+1)*pageSize),composite=[];
 for(let i=0;i<visible.length;i++){
  const item=visible[i],left=(i%columns)*tileWidth,top=Math.floor(i/columns)*tileHeight;
  composite.push({input:await sharp(path.join(root,item.id+'-fit.png')).resize(205,230,{fit:'contain',background:'#00000000'}).png().toBuffer(),left:left+4,top:top+8});
  composite.push({input:await sharp(path.join(root,item.id+'.png')).resize(90,90,{fit:'contain',background:'#00000000'}).png().toBuffer(),left:left+199,top:top+56});
  const safe=item.name.replaceAll('&','&amp;').replaceAll('<','&lt;');
  const credit=`${item.creator?.name||item.collection||'Votex Originals'} ${item.creator?.license||''}`.trim().replaceAll('&','&amp;').replaceAll('<','&lt;');
  const label=Buffer.from(`<svg width="${tileWidth}" height="40" xmlns="http://www.w3.org/2000/svg"><text x="10" y="15" fill="#d8e8f7" font-size="12" font-family="sans-serif">${safe}</text><text x="10" y="33" fill="#88a9bf" font-size="10" font-family="sans-serif">${item.slot} · ${credit} · fitted review</text></svg>`);
  composite.push({input:label,left,top:top+243});
 }
 await sharp({create:{width:columns*tileWidth,height:Math.ceil(visible.length/columns)*tileHeight,channels:4,background:'#101b29'}}).composite(composite).png().toFile(path.join(root,`contact-${page+1}.png`));
}
console.log(`Rasterized ${items.length} source meshes plus fitted contact sheets.`);
