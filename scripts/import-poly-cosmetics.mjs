/** Offline, hash-pinned adaptation of individually licensed original model identities.
 * Staging never changes the installed catalog. Installation requires reviewed fitted JSON hashes.
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {pathToFileURL} from 'node:url';
import {Matrix4, Vector3, Quaternion, Color} from 'three';

const root=path.resolve(import.meta.dirname,'..');
const source=path.join(root,'assets-source/third-party/poly-curated');
const stage=path.join(root,'work/catalog-expansion/poly-staged');
const sharp=(await import(pathToFileURL(process.argv.find(a=>a.endsWith('index.mjs'))||process.env.VOTEX_SHARP_MODULE||'').href)).default;
const read=p=>fs.readFileSync(p),json=p=>JSON.parse(read(p));
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
const round=x=>Math.round(x*100000)/100000;
fs.mkdirSync(path.join(stage,'textures'),{recursive:true});

async function decode(file){
 const bytes=read(file);if(bytes.toString('ascii',0,4)!=='glTF'||bytes.readUInt32LE(4)!==2||bytes.readUInt32LE(8)!==bytes.length)throw Error('Invalid GLB2 header');
 const end=20+bytes.readUInt32LE(12);if(end+8>bytes.length)throw Error('Truncated GLB');
 const g=JSON.parse(bytes.toString('utf8',20,end)),bin=bytes.subarray(end+8);
 if(g.extensionsRequired?.some(x=>!['KHR_materials_unlit','KHR_texture_transform','KHR_materials_pbrSpecularGlossiness'].includes(x)))throw Error('Unsupported required glTF extension');
 const component={5120:[1,'readInt8',127],5121:[1,'readUInt8',255],5122:[2,'readInt16LE',32767],5123:[2,'readUInt16LE',65535],5125:[4,'readUInt32LE',4294967295],5126:[4,'readFloatLE',1]};
 const widths={SCALAR:1,VEC2:2,VEC3:3,VEC4:4,MAT4:16};
 function accessor(index){
  const a=g.accessors[index],v=g.bufferViews[a.bufferView],type=component[a.componentType],width=widths[a.type];
  if(a.sparse||v.buffer!==0||!type||!width||a.count>200000)throw Error('Unsupported or oversized accessor');
  const [size,method,normal]=type,stride=v.byteStride||size*width,start=(v.byteOffset||0)+(a.byteOffset||0);
  if(start<0||start+(a.count-1)*stride+width*size>bin.length)throw Error('Accessor escapes GLB buffer');
  return Array.from({length:a.count},(_,i)=>Array.from({length:width},(_,k)=>{const value=bin[method](start+i*stride+k*size);if(!Number.isFinite(value))throw Error('Non-finite GLB coordinate');return a.normalized?Math.max(-1,value/normal):value;}));
 }
 const parents=new Map(),worlds=new Map(),visiting=new Set();g.nodes.forEach((node,i)=>node.children?.forEach(child=>{if(parents.has(child))throw Error('Multiple node parents');parents.set(child,i);}));
 function world(index){
  if(worlds.has(index))return worlds.get(index);if(visiting.has(index)||visiting.size>100)throw Error('Cyclic/deep node graph');visiting.add(index);
  const n=g.nodes[index],local=n.matrix?new Matrix4().fromArray(n.matrix):new Matrix4().compose(new Vector3().fromArray(n.translation||[0,0,0]),new Quaternion().fromArray(n.rotation||[0,0,0,1]),new Vector3().fromArray(n.scale||[1,1,1]));
  const result=parents.has(index)?world(parents.get(index)).clone().multiply(local):local;worlds.set(index,result);visiting.delete(index);return result;
 }
 const images=new Map();
 async function texture(index){
  if(images.has(index))return images.get(index);const image=g.images[index];let data;
  if(image.bufferView!==undefined){const v=g.bufferViews[image.bufferView];data=bin.subarray(v.byteOffset||0,(v.byteOffset||0)+v.byteLength);}
  else if(/^data:image\/(png|jpeg);base64,/.test(image.uri))data=Buffer.from(image.uri.split(',')[1],'base64');
  else throw Error('External texture not preserved in the pinned original GLB');
  if(data.length>12_000_000)throw Error('Texture source too large');
  const png=await sharp(data,{limitInputPixels:16777216}).resize({width:1024,height:1024,fit:'inside',withoutEnlargement:true}).png().toBuffer();
  const name='poly-'+hash(png).slice(0,24)+'.png';fs.writeFileSync(path.join(stage,'textures',name),png);images.set(index,name);return name;
 }
 const active=new Set();function visit(index){if(active.has(index))return;active.add(index);g.nodes[index].children?.forEach(visit);}for(const index of g.scenes?.[g.scene??0]?.nodes??g.nodes.map((_,i)=>i))visit(index);
 const parts=[];let skinnedVertices=0;
 for(let index=0;index<g.nodes.length;index++){
  const node=g.nodes[index];if(!active.has(index)||node.mesh===undefined)continue;const model=world(index);
  let skinMatrices=null;
  if(node.skin!==undefined){const skin=g.skins[node.skin],inverse=skin.inverseBindMatrices===undefined?skin.joints.map(()=>new Matrix4().toArray()):accessor(skin.inverseBindMatrices);skinMatrices=skin.joints.map((joint,i)=>world(joint).clone().multiply(new Matrix4().fromArray(inverse[i])));}
  for(const primitive of g.meshes[node.mesh].primitives){
   if(primitive.mode!==undefined&&primitive.mode!==4)throw Error('Non-triangle mesh');
   const a=primitive.attributes,positions=accessor(a.POSITION),joints=skinMatrices&&a.JOINTS_0!==undefined?accessor(a.JOINTS_0):null,weights=joints?accessor(a.WEIGHTS_0):null;
   const p=positions.map((position,i)=>{
    if(!joints)return new Vector3().fromArray(position).applyMatrix4(model).toArray();
    const sum=new Vector3();let total=0;for(let k=0;k<4;k++){const weight=weights[i][k];if(weight===0)continue;const bone=skinMatrices[joints[i][k]];if(!bone)throw Error('Invalid skin joint');sum.addScaledVector(new Vector3().fromArray(position).applyMatrix4(bone),weight);total+=weight;}if(Math.abs(total-1)>.001)throw Error('Invalid skin weights');skinnedVertices++;return sum.toArray();
   });
   const material=g.materials?.[primitive.material]||{},spec=material.extensions?.KHR_materials_pbrSpecularGlossiness,pbr=material.pbrMetallicRoughness||{},factor=spec?.diffuseFactor||pbr.baseColorFactor||[1,1,1,1];
   if(factor[3]===0)continue;const colors=a.COLOR_0!==undefined?accessor(a.COLOR_0):null;
   const part={p,triangles:primitive.indices===undefined?p.map((_,i)=>i):accessor(primitive.indices).flat(),roughness:pbr.roughnessFactor??.8,opacity:factor[3]??1,colors:p.map((_,i)=>new Color().fromArray(factor.slice(0,3).map((value,k)=>value*(colors?.[i]?.[k]??1))).convertLinearToSRGB().toArray())};
   if(part.triangles.length%3||part.triangles.some(i=>!Number.isInteger(i)||i<0||i>=p.length))throw Error('Invalid triangle indices');
   const tex=spec?.diffuseTexture||pbr.baseColorTexture;
   if(tex){const transform=tex.extensions?.KHR_texture_transform||{},channel=transform.texCoord??tex.texCoord??0,uv=accessor(a['TEXCOORD_'+channel]),angle=transform.rotation||0,c=Math.cos(angle),s=Math.sin(angle),scale=transform.scale||[1,1],offset=transform.offset||[0,0];part.texture=await texture(g.textures[tex.index].source);part.uvs=uv.map(([u,v])=>[offset[0]+c*u*scale[0]-s*v*scale[1],offset[1]+s*u*scale[0]+c*v*scale[1]]);}
   parts.push(part);
  }
 }
 if(!parts.length)throw Error('No visible geometry');return {parts,skinnedVertices};
}
function bounds(parts){const min=[Infinity,Infinity,Infinity],max=[-Infinity,-Infinity,-Infinity];for(const part of parts)for(const p of part.p)for(let k=0;k<3;k++){min[k]=Math.min(min[k],p[k]);max[k]=Math.max(max[k],p[k]);}return {min,max,size:max.map((x,k)=>x-min[k]),center:max.map((x,k)=>(x+min[k])/2)};}
function fingerprint(parts,precision=1e5){const b=bounds(parts),s=Math.max(...b.size),triangles=[];for(const part of parts)for(let i=0;i<part.triangles.length;i+=3)triangles.push(part.triangles.slice(i,i+3).map(index=>part.p[index].map((x,k)=>Math.round((x-b.center[k])/s*precision)).join(',')).sort().join('/'));return hash([...new Set(triangles)].sort().join(';'));}
function fittedIdentity(parts){return fingerprint(parts.map(part=>({p:Array.from({length:part.positions.length/3},(_,i)=>part.positions.slice(i*3,i*3+3)),triangles:part.quads.filter((_,i)=>i%4!==3)})),10000);}
function surface(raw,transform,joint){
 const p=raw.p.map(transform),normals=p.map(()=>new Vector3()),zero=new Vector3().fromArray(transform([0,0,0])),basis=[[1,0,0],[0,1,0],[0,0,1]].map(v=>new Vector3().fromArray(transform(v)).sub(zero)),mirrored=basis[0].dot(basis[1].clone().cross(basis[2]))<0,triangles=raw.triangles.slice();
 if(mirrored)for(let i=0;i<triangles.length;i+=3)[triangles[i+1],triangles[i+2]]=[triangles[i+2],triangles[i+1]];
 for(let i=0;i<triangles.length;i+=3){const [a,b,c]=triangles.slice(i,i+3),n=new Vector3().fromArray(p[b]).sub(new Vector3().fromArray(p[a])).cross(new Vector3().fromArray(p[c]).sub(new Vector3().fromArray(p[a])));for(const k of [a,b,c])normals[k].add(n);}
 const result={joint,positions:p.flat().map(round),normals:normals.flatMap(n=>(n.lengthSq()>1e-16?n.normalize():n.set(0,0,1)).toArray().map(round)),colors:raw.colors.flat().map(round),quads:[],material:{color:'#ffffff',metalness:0,roughness:raw.roughness,opacity:raw.opacity}};
 for(let i=0;i<triangles.length;i+=3)result.quads.push(triangles[i],triangles[i+1],triangles[i+2],triangles[i+2]);
 if(raw.texture){result.texture=raw.texture;result.uvs=raw.uvs.flat().map(round);}
 if(result.positions.length>60000||result.quads.length>24000||result.positions.some(v=>!Number.isFinite(v)||Math.abs(v)>100))throw Error('Mesh exceeds native per-part bounds');return result;
}
function fitted(raw,entry){
 let sourceBounds=bounds(raw),axes=[0,1,2],size=sourceBounds.size;
 if(['back-shield','back-long','halo'].includes(entry.fit)||entry.slot==='back'&&/book/i.test(entry.name)){const order=axes.slice().sort((a,b)=>size[b]-size[a]);axes=entry.fit==='halo'?[order[0],order[2],order[1]]:[order[1],order[0],order[2]];}
 // glTF's world Y is vertical. Sorting spectacle axes would rotate long stems downward.
 if(['glasses','mask'].includes(entry.fit)&&size[2]>size[0])axes=[2,1,0];
 raw=raw.map(part=>({...part,p:part.p.map(p=>axes.map(i=>p[i]))}));const b=bounds(raw);let transform,joint='body';
 if(entry.fit==='pet'){const s=Math.min(6.7/b.size[0],8.3/b.size[1],7/b.size[2]);transform=p=>[-8.4+(p[0]-b.center[0])*s,-.3-(p[1]-b.min[1])*s,-(p[2]-b.center[2])*s];}
 else if(['hat','crown','ears','antlers','halo'].includes(entry.fit)){const s=Math.min(11.6/b.size[0],8.5/b.size[1],11.6/b.size[2]);joint='head';transform=p=>[(p[0]-b.center[0])*s,-8.25-(p[1]-b.min[1])*s,-(p[2]-b.center[2])*s];}
 else if(entry.fit==='headphones'){joint='head';transform=p=>[(p[0]-b.center[0])*12.5/b.size[0],-4.7-(p[1]-b.center[1])*9.3/b.size[1],-(p[2]-b.center[2])*5.5/b.size[2]];}
 else if(['glasses','mask'].includes(entry.fit)){
  joint='head';const mask=entry.fit==='mask',s=Math.min((mask?8.4:9.6)/b.size[0],(mask?7.6:3.6)/b.size[1]),depth=Math.min(s,(mask?2.4:7.4)/b.size[2]);
  transform=p=>{const x=(p[0]-b.center[0])*s,y=-4.2-(p[1]-b.center[1])*s;let z=mask?-4.3-(p[2]-b.min[2])*depth:-4.4+(b.max[2]-p[2])*depth;
   // Frames stay in front; temple stems may wrap around only outside the cube-head sides.
   if(!mask&&Math.abs(x)<4.25)z=Math.min(z,-4.25);return [x,y,z];};
 }
 else if(entry.fit==='necklace'){const s=Math.min(7/b.size[0],8/b.size[1],1.8/b.size[2]);transform=p=>[(p[0]-b.center[0])*s,5-(p[1]-b.center[1])*s,-2.3-(p[2]-b.min[2])*s];}
 else if(entry.fit==='feet'){
  // One source shoe becomes one articulated pair, counted as one cosmetic identity.
  const middle=b.center[0],group=[];let crosses=false;for(const part of raw){const indices=[];for(let i=0;i<part.triangles.length;i+=3){const face=part.triangles.slice(i,i+3);if(face.some(k=>part.p[k][0]<middle-.00001)&&face.some(k=>part.p[k][0]>middle+.00001))crosses=true;if(face.every(k=>part.p[k][0]>=middle-.00001))indices.push(...face);}if(indices.length){const keys=[...new Set(indices)],remap=new Map(keys.map((key,i)=>[key,i]));group.push({...part,p:keys.map(k=>part.p[k]),colors:keys.map(k=>part.colors[k]),uvs:part.uvs&&keys.map(k=>part.uvs[k]),triangles:indices.map(k=>remap.get(k))});}}
  // Single-shoe sources have no two disjoint foot groups; keep their complete original sculpt.
  const selected=!crosses&&group.reduce((n,p)=>n+p.triangles.length,0)>raw.reduce((n,p)=>n+p.triangles.length,0)*.35&&group.reduce((n,p)=>n+p.triangles.length,0)<raw.reduce((n,p)=>n+p.triangles.length,0)*.65?group:raw;
  const used=selected.map(p=>({...p,p:p.triangles.map(index=>p.p[index])})),foot=bounds(used),result=[];
  for(const side of [-1,1])for(const part of selected)result.push(surface(part,p=>[side*(p[0]-foot.center[0])*5.8/foot.size[0],12.35-(p[1]-foot.min[1])*Math.min(6/foot.size[1],5.8/foot.size[0]),-1.05-(p[2]-foot.center[2])*6.8/foot.size[2]],side<0?'rightLeg':'leftLeg'));return result;
 }
 else {const long=entry.fit==='back-long',s=Math.min(10/b.size[0],(long?19:12)/b.size[1],(entry.fit==='back-pack'?6:3.5)/b.size[2]),angle=long?.25:0;transform=p=>{const x=(p[0]-b.center[0])*s,y=5-(p[1]-b.center[1])*s;return [x*Math.cos(angle)-y*Math.sin(angle),x*Math.sin(angle)+y*Math.cos(angle),2.6+(p[2]-b.min[2])*s];};}
 return raw.map(part=>surface(part,transform,joint));
}

const manifest=json(path.join(source,'manifest.json')),policy=json(path.join(source,'fitting-policy.json')),entries=[],audit=[],geometries=new Map();
const existing=json(path.join(root,'apps/launcher/public/assets/catalog/index.json'));
const priorUrls=new Set(existing.filter(item=>!item.id.startsWith('creator-poly-')).map(item=>item.creator?.url));
const fittedGeometries=new Map(existing.filter(item=>!item.id.startsWith('creator-poly-')&&item.slot!=='cape').map(item=>[fittedIdentity(json(path.join(root,'apps/launcher/public/assets/catalog',item.id+'.json')).parts),item.id]));
for(const entry of manifest.items){try{
 if(policy.excludedIds[entry.id]||policy.excludedFits[entry.fit])throw Error('Fitting review: '+(policy.excludedIds[entry.id]||policy.excludedFits[entry.fit]));
 if(!['CC0 1.0','CC-BY 3.0'].includes(entry.license))throw Error('Unsupported individual asset license');
 if(priorUrls.has(entry.sourceUrl))throw Error('Source identity already present in catalog');
 const file=path.join(source,entry.sourceFile);if(hash(read(file))!==entry.sourceSha256)throw Error('Source digest mismatch');
 const parsed=await decode(file),identity=fingerprint(parsed.parts);if(geometries.has(identity))throw Error('Identical normalized source geometry to '+geometries.get(identity));
 const parts=fitted(parsed.parts,entry),triangles=parts.reduce((n,p)=>n+p.quads.length/4,0);if(triangles>12000||parts.length>100)throw Error('Whole cosmetic exceeds native budget');
 const fittedHash=fittedIdentity(parts);if(fittedGeometries.has(fittedHash))throw Error('Repeated fitted geometry: '+fittedGeometries.get(fittedHash));
 const changes='Originales Modell für den Minecraft-Spieler ausgerichtet, skaliert und an Gelenke gebunden; Hautgewichte in die originale Ruhepose gebacken. Originalanimationen nicht als Votex-Emotes übernommen.';
 const model={schemaVersion:2,id:entry.id,name:entry.name,slot:entry.slot,collection:entry.collection,description:entry.fit==='pet'?'Ein eigenständig geformter Schulterbegleiter von '+entry.author+'.':'Originalmodell von '+entry.author+', für die Votex-Garderobe angepasst.',creator:{name:entry.author,url:entry.sourceUrl,license:entry.license==='CC0 1.0'?'CC0':'CC-BY-3.0',licenseUrl:entry.licenseUrl,changes},sourceAsset:{path:entry.sourceFile,sha256:entry.sourceSha256,sourcePageSha256:entry.sourcePageSha256,geometrySha256:identity},parts};
 const encoded=JSON.stringify(model);if(Buffer.byteLength(encoded)>1_950_000)throw Error('Whole model exceeds JSON size budget');
 fs.writeFileSync(path.join(stage,entry.id+'.json'),encoded);const {parts:ignored,...item}=model;entries.push(item);geometries.set(identity,entry.id);fittedGeometries.set(fittedHash,entry.id);audit.push({id:entry.id,status:'staged-unreviewed',triangles,parts:parts.length,bytes:Buffer.byteLength(encoded),restPoseSkinnedVertices:parsed.skinnedVertices,sourceGeometrySha256:identity});
}catch(error){audit.push({id:entry.id,name:entry.name,status:'rejected',reason:error.message});}}
fs.writeFileSync(path.join(stage,'index.json'),JSON.stringify(entries,null,2)+'\n');fs.writeFileSync(path.join(stage,'audit.json'),JSON.stringify({notARelease:true,sourceCandidates:manifest.items.length,distinctStaged:entries.length,items:audit},null,2)+'\n');
if(process.argv.includes('--install')){
 const reviewed=json(path.join(source,'reviewed.json'));for(const item of entries)if(!reviewed.approvedIds.includes(item.id)||reviewed.sha256ById[item.id]!==hash(read(path.join(stage,item.id+'.json'))))throw Error('Unreviewed fitted identity '+item.id);
 const web=path.join(root,'apps/launcher/public/assets/catalog'),core=path.join(root,'minecraft/votex-core/src/main/resources/assets/votex_client');
 for(const item of entries){for(const dir of [web,path.join(core,'catalog')])fs.copyFileSync(path.join(stage,item.id+'.json'),path.join(dir,item.id+'.json'));fs.copyFileSync(path.join(stage,item.id+'.svg'),path.join(web,item.id+'.svg'));fs.copyFileSync(path.join(stage,item.id+'.png'),path.join(core,'textures/previews',item.id+'.png'));}
 const usedTextures=new Set(entries.flatMap(item=>json(path.join(stage,item.id+'.json')).parts.map(part=>part.texture).filter(Boolean)));
 for(const filename of usedTextures)for(const dir of [path.join(web,'textures'),path.join(core,'textures/cosmetics')])fs.copyFileSync(path.join(stage,'textures',filename),path.join(dir,filename));
 const keep=new Set(entries.map(item=>item.id));
 for(const directory of [web,path.join(core,'catalog'),path.join(core,'textures/previews')])for(const filename of fs.readdirSync(directory)){
  const match=filename.match(/^(creator-poly-[a-f0-9]{12})\.(json|svg|png)$/);if(match&&!keep.has(match[1])){const file=path.join(directory,filename);if(!fs.lstatSync(file).isFile())throw Error('Unexpected generated asset type');fs.unlinkSync(file);}
 }
 for(const directory of [path.join(web,'textures'),path.join(core,'textures/cosmetics')])for(const filename of fs.readdirSync(directory))if(/^poly-[a-f0-9]{24}\.png$/.test(filename)&&!usedTextures.has(filename)){const file=path.join(directory,filename);if(!fs.lstatSync(file).isFile())throw Error('Unexpected generated texture type');fs.unlinkSync(file);}
 const credits='# Individually licensed model adaptations\n\nOriginal authors retain the listed source licenses. These models are not original Votex artwork. The Votex software license does not replace the asset licenses.\n\nChanges: original static/rest-pose geometry converted, fitted and attached to the Minecraft player; source skin weights baked where present, UV/colors preserved, texture containers converted to bounded PNG where needed. Source animation clips are not Votex emotes.\n\n'+entries.map(item=>`- **${item.name}** (${item.id}) — ${item.creator.name}; [${item.creator.license}](${item.creator.licenseUrl}); [original model](${item.creator.url}).`).join('\n')+'\n';
 for(const file of [path.join(source,'ATTRIBUTION.md'),path.join(web,'POLY-ATTRIBUTION.md'),path.resolve(core,'../../licenses/Poly-Catalog.md')])fs.writeFileSync(file,credits);
 const combined=[...existing.filter(item=>!item.id.startsWith('creator-poly-')),...entries];for(const file of [path.join(web,'index.json'),path.join(core,'catalog/index.json'),path.join(root,'apps/launcher/src/data/cosmetic-manifest.json')])fs.writeFileSync(file,JSON.stringify(combined,null,2)+'\n');console.log('Installed '+entries.length+' reviewed models; shared catalog '+combined.length);
}else console.log('Staged '+entries.length+' original model identities; installed catalog unchanged.');
console.log(JSON.stringify({staged:entries.length,rejected:audit.filter(a=>a.status==='rejected').map(({name,reason})=>({name,reason}))},null,2));
