/** Offline import of exact curated Kenney CC0 identities. Stage by default; --install applies reviewed models. */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {Matrix4,Matrix3,Vector3,Quaternion,Color} from 'three';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const source=path.join(root,'assets-source/third-party/kenney-curated');
const stage=path.join(root,'work/catalog-expansion/staged');
const read=p=>fs.readFileSync(p),json=p=>JSON.parse(read(p));
const round=n=>Math.round(n*100000)/100000;
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
fs.mkdirSync(path.join(stage,'textures'),{recursive:true});
function texture(bytes,id){
 if(!bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])))throw Error('Only PNG textures');
 if(bytes.readUInt32BE(16)>2048||bytes.readUInt32BE(20)>2048)throw Error('Texture budget exceeded');
 const name='kenney-'+hash(bytes).slice(0,24)+'.png';fs.writeFileSync(path.join(stage,'textures',name),bytes);return name;
}
function glb(file, id) {
  const b = read(path.join(source, file));
  if (b.toString("ascii", 0, 4) !== "glTF" || b.readUInt32LE(4) !== 2)
    throw Error("Expected GLB 2");
  const end = 20 + b.readUInt32LE(12),
    j = JSON.parse(b.toString("utf8", 20, end)),
    bin = b.subarray(end + 8);
  if (
    j.extensionsRequired?.some(
      (x) => !["KHR_materials_unlit", "KHR_texture_transform"].includes(x),
    )
  )
    throw Error("Unsupported GLB extension");
  function accessor(i) {
    const a = j.accessors[i],
      v = j.bufferViews[a.bufferView];
    if (a.sparse || v.buffer !== 0)
      throw Error("Unsupported sparse/external accessor");
    const sizes = { SCALAR: 1, VEC2: 2, VEC3: 3, VEC4: 4, MAT4: 16 },
      types = {
        5126: [4, "readFloatLE"],
        5125: [4, "readUInt32LE"],
        5123: [2, "readUInt16LE"],
        5121: [1, "readUInt8"],
      };
    const [bytes, method] = types[a.componentType] || [];
    if (!bytes) throw Error("Unsupported accessor type");
    const count = sizes[a.type],
      stride = v.byteStride || bytes * count,
      start = (v.byteOffset || 0) + (a.byteOffset || 0);
    return Array.from({ length: a.count }, (_, n) =>
      Array.from({ length: count }, (_, k) =>
        bin[method](start + n * stride + k * bytes),
      ),
    );
  }
  const worlds = [],
    parents = new Map();
  j.nodes?.forEach((n, i) => n.children?.forEach((c) => parents.set(c, i)));
  function world(i) {
    if (worlds[i]) return worlds[i];
    const n = j.nodes[i],
      m = n.matrix
        ? new Matrix4().fromArray(n.matrix)
        : new Matrix4().compose(
            new Vector3().fromArray(n.translation || [0, 0, 0]),
            new Quaternion().fromArray(n.rotation || [0, 0, 0, 1]),
            new Vector3().fromArray(n.scale || [1, 1, 1]),
          );
    worlds[i] = parents.has(i) ? world(parents.get(i)).clone().multiply(m) : m;
    return worlds[i];
  }
  const textures = new Map();
  function image(i) {
    if (textures.has(i)) return textures.get(i);
    const im = j.images[i];
    let bytes;
    if (im.bufferView !== undefined) {
      const v = j.bufferViews[im.bufferView];
      bytes = bin.subarray(
        v.byteOffset || 0,
        (v.byteOffset || 0) + v.byteLength,
      );
    } else {
      const base = path.dirname(path.join(source, file)),
        p = path.resolve(base, im.uri);
      if (!p.startsWith(base + path.sep))
        throw Error("External texture escapes source directory");
      bytes = read(p);
    }
    const name = texture(bytes, `${id}-${i}`);
    textures.set(i, name);
    return name;
  }
  const parts = [];
  j.nodes.forEach((node, ni) => {
    if (node.mesh === undefined) return;
    const mat = world(ni),
      nm = new Matrix3().getNormalMatrix(mat);
    for (const primitive of j.meshes[node.mesh].primitives) {
      if (primitive.mode !== undefined && primitive.mode !== 4)
        throw Error("Only triangle primitives supported");
      const p = accessor(primitive.attributes.POSITION).map((v) =>
        new Vector3().fromArray(v).applyMatrix4(mat).toArray(),
      );
      const normals =
        primitive.attributes.NORMAL !== undefined
          ? accessor(primitive.attributes.NORMAL).map((v) =>
              new Vector3().fromArray(v).applyNormalMatrix(nm).toArray(),
            )
          : null;
      const triangles =
        primitive.indices !== undefined
          ? accessor(primitive.indices).flat()
          : p.map((_, i) => i);
      const material = j.materials?.[primitive.material],
        pbr = material?.pbrMetallicRoughness || {},
        linear = pbr.baseColorFactor || [1, 1, 1, 1],
        color = new Color().fromArray(linear).convertLinearToSRGB().toArray();
      const part = {
        p,
        normals,
        triangles,
        color,
        roughness: pbr.roughnessFactor ?? 0.8,
      };
      if (pbr.baseColorTexture) {
        const tex = pbr.baseColorTexture;
        if (tex.texCoord && tex.texCoord !== 0)
          throw Error("Unsupported UV channel");
        part.texture = image(j.textures[tex.index].source);
        part.uvs = accessor(primitive.attributes.TEXCOORD_0);
      }
      parts.push(part);
    }
  });
  return parts;
}
function bounds(parts) {
  const min = [Infinity, Infinity, Infinity],
    max = [-Infinity, -Infinity, -Infinity];
  for (const p of parts)
    for (const v of p.p)
      for (let i = 0; i < 3; i++) {
        min[i] = Math.min(min[i], v[i]);
        max[i] = Math.max(max[i], v[i]);
      }
  return {
    min,
    max,
    size: max.map((v, i) => v - min[i]),
    center: min.map((v, i) => (v + max[i]) / 2),
  };
}
function surface(raw, transform, joint, motion) {
  const p = raw.p.map(transform),
    normals = Array.from({ length: p.length }, () => [0, 0, 0]);
  // Recompute area-weighted normals after the fitted transform, including reflection for the opposite wing.
  const zero = new Vector3().fromArray(transform([0, 0, 0]));
  const basis = [
    [1, 0, 0],
    [0, 1, 0],
    [0, 0, 1],
  ].map((v) => new Vector3().fromArray(transform(v)).sub(zero));
  const mirrored = basis[0].dot(basis[1].clone().cross(basis[2])) < 0;
  const indices = raw.triangles.slice();
  if (mirrored)
    for (let i = 0; i < indices.length; i += 3)
      [indices[i + 1], indices[i + 2]] = [indices[i + 2], indices[i + 1]];
  for (let i = 0; i < indices.length; i += 3) {
    const [a, b, c] = indices.slice(i, i + 3),
      n = new Vector3()
        .fromArray(p[b])
        .sub(new Vector3().fromArray(p[a]))
        .cross(new Vector3().fromArray(p[c]).sub(new Vector3().fromArray(p[a])))
        .normalize();
    for (const k of [a, b, c])
      for (let d = 0; d < 3; d++) normals[k][d] += n.getComponent(d);
  }
  const part = {
    joint,
    positions: p.flat().map(round),
    normals: normals.flatMap((n) =>
      (Math.hypot(...n) > 1e-8
        ? new Vector3().fromArray(n).normalize().toArray()
        : [0, 0, 1]
      ).map(round),
    ),
    colors: p.flatMap(() => raw.color.map(round)),
    quads: [],
    material: {
      color: "#ffffff",
      metalness: 0,
      roughness: raw.roughness,
      opacity: 1,
    },
  };
  for (let i = 0; i < indices.length; i += 3)
    part.quads.push(indices[i], indices[i + 1], indices[i + 2], indices[i + 2]);
  if (raw.texture) {
    part.texture = raw.texture;
    part.uvs = raw.uvs.flat().map(round);
  }
  if (motion) part.motion = motion;
  if (
    part.positions.length > 60000 ||
    part.quads.length > 24000 ||
    p.some((v) => v.some((n) => !Number.isFinite(n) || Math.abs(n) > 100))
  )
    throw Error("Model exceeds Core mesh bounds");
  return part;
}

const manifest=json(path.join(source,'manifest.json'));
const items=[],report=[],geometries=new Map();
function fingerprint(parts){
 const b=bounds(parts),s=Math.max(...b.size);
 const triangles=[];
 for(const part of parts)for(let i=0;i<part.triangles.length;i+=3){
  const vertices=part.triangles.slice(i,i+3).map(index=>part.p[index].map((v,k)=>Math.round((v-b.center[k])/s*1e5)).join(',')).sort();
  triangles.push(vertices.join('/'));
 }
 return hash([...new Set(triangles)].sort().join(';'));
}
function fitted(raw,entry){
 let sourceBounds=bounds(raw),size=sourceBounds.size;
 let coordinates=v=>[v[0],v[1],v[2]];
 if(entry.fit==='wreath'){
  const axes=[0,1,2].sort((a,b)=>size[b]-size[a]);
  coordinates=v=>[v[axes[0]],v[axes[2]],v[axes[1]]];
 }else if(entry.fit==='back-shield'||entry.fit==='back-long'){
  const axes=[0,1,2].sort((a,b)=>size[b]-size[a]);
  coordinates=v=>[v[axes[1]],v[axes[0]],v[axes[2]]];
 }
 raw=raw.map(p=>({...p,p:p.p.map(coordinates)}));
 const b=bounds(raw),center=b.center;
 let transform,joint;
 if(entry.fit==='pet'){
  const s=Math.min(7.5/b.size[0],8.5/b.size[1],8.5/b.size[2]);
  transform=v=>[-8.2+(v[0]-center[0])*s,.2-(v[1]-b.min[1])*s,-(v[2]-center[2])*s];joint='body';
 }else if(entry.fit==='novelty'){
  const s=Math.min(9.3/b.size[0],7.4/b.size[1],9.3/b.size[2]);
  transform=v=>[(v[0]-center[0])*s,-8.3-(v[1]-b.min[1])*s,-(v[2]-center[2])*s];joint='head';
 }else if(entry.fit==='wreath'){
  const s=Math.min(10.7/b.size[0],10.7/b.size[2],1.7/b.size[1]);
  transform=v=>[(v[0]-center[0])*s,-8.5-(v[1]-b.min[1])*s,-(v[2]-center[2])*s];joint='head';
 }else if(entry.fit==='back-pack'){
  const s=Math.min(9/b.size[0],10/b.size[1],7/b.size[2]);
  transform=v=>[(v[0]-center[0])*s,6-(v[1]-center[1])*s,2.4+(v[2]-b.min[2])*s];joint='body';
 }else{
  const s=Math.min(10/b.size[0],entry.fit==='back-long'?19/b.size[1]:13/b.size[1],3.5/b.size[2]);
  const angle=entry.fit==='back-long'?.25:0,c=Math.cos(angle),sine=Math.sin(angle);
  transform=v=>{const x=(v[0]-center[0])*s,y=5-(v[1]-center[1])*s;return [x*c-y*sine,x*sine+y*c,2.6+(v[2]-b.min[2])*s];};joint='body';
 }
 return raw.map(p=>surface(p,transform,joint));
}
for(const entry of manifest.items){
 const file=path.join(source,entry.sourceFile),bytes=read(file);
 if(hash(bytes)!==entry.sourceSha256)throw Error('Source hash mismatch: '+entry.id);
 const raw=glb(entry.sourceFile,entry.id),identity=fingerprint(raw);
 if(geometries.has(identity)){report.push({id:entry.id,status:'rejected-identical-geometry',duplicateOf:geometries.get(identity)});continue;}
 geometries.set(identity,entry.id);
 const parts=fitted(raw,entry);
 const faces=parts.reduce((n,p)=>n+p.quads.length/4,0);
 if(faces>8500||parts.length>100)throw Error('Curated model exceeds cosmetic budget: '+entry.id);
 const description=entry.fit==='pet'?'Ein eigenständig geformter Begleiter aus Kenneys CC0-Bibliothek, an der Schulter befestigt.':entry.fit==='novelty'?'Ein verspielter Kopfschmuck aus Kenneys CC0-Bibliothek. Das Modell sitzt vollständig über dem Minecraft-Kopf.':'Ein eigenständiges CC0-Modell von Kenney, für die Votex-Garderobe angepasst.';
 const model={id:entry.id,name:entry.name,slot:entry.slot,description,collection:entry.slot==='pet'?'Kleine Begleiter':entry.slot==='head'?'Verspielte Atelierstücke':'Abenteuer-Gepäck',
  creator:{name:'Kenney',url:entry.sourceUrl,license:'CC0',licenseUrl:entry.licenseUrl,changes:'Originalgeometrie und UV-Textur beibehalten; ausgerichtet, skaliert und an Spieler-Gelenke gebunden. Originalanimationen nicht übernommen.'},
  sourceAsset:{pack:entry.pack,path:entry.sourceFile,sha256:entry.sourceSha256,archiveSha256:entry.sourceArchiveSha256,geometrySha256:identity},schemaVersion:2,parts};
 const serialized=JSON.stringify(model);
 if(Buffer.byteLength(serialized)>1_950_000)throw Error('Catalog model exceeds file budget');
 fs.writeFileSync(path.join(stage,entry.id+'.json'),serialized);
 const {parts:ignored,...item}=model;items.push(item);
 const fittedBounds=bounds(parts.map(p=>({p:Array.from({length:p.positions.length/3},(_,i)=>p.positions.slice(i*3,i*3+3))})));
 report.push({id:entry.id,status:'staged-awaiting-visual-review',triangles:faces,parts:parts.length,bytes:Buffer.byteLength(serialized),sourceGeometrySha256:identity,bounds:fittedBounds});
}
fs.writeFileSync(path.join(stage,'index.json'),JSON.stringify(items,null,2)+'\n');
fs.writeFileSync(path.join(stage,'audit.json'),JSON.stringify({sourceCandidates:manifest.items.length,distinctStaged:items.length,notARelease:true,items:report},null,2)+'\n');
if(process.argv.includes('--install')){
 const reviewed=new Set(json(path.join(source,'reviewed.json')).approvedIds);
 if(items.some(item=>!reviewed.has(item.id)))throw Error('Unreviewed fitted model cannot be installed');
 const digests=json(path.join(source,'reviewed.json')).sha256ById;
 if(items.some(item=>digests[item.id]!==hash(read(path.join(stage,item.id+'.json')))))throw Error('Fitting changed after projection review');
 const web=path.join(root,'apps/launcher/public/assets/catalog'),core=path.join(root,'minecraft/votex-core/src/main/resources/assets/votex_client');
 for(const item of items){
  for(const directory of [web,path.join(core,'catalog')])fs.copyFileSync(path.join(stage,item.id+'.json'),path.join(directory,item.id+'.json'));
  fs.copyFileSync(path.join(stage,item.id+'.svg'),path.join(web,item.id+'.svg'));
  fs.copyFileSync(path.join(stage,item.id+'.png'),path.join(core,'textures/previews',item.id+'.png'));
 }
 for(const name of fs.readdirSync(path.join(stage,'textures')))for(const directory of [path.join(web,'textures'),path.join(core,'textures/cosmetics')])fs.copyFileSync(path.join(stage,'textures',name),path.join(directory,name));
 const combined=[...json(path.join(web,'index.json')).filter(x=>!items.some(item=>item.id===x.id)),...items];
 for(const file of [path.join(web,'index.json'),path.join(core,'catalog/index.json'),path.join(root,'apps/launcher/src/data/cosmetic-manifest.json')])fs.writeFileSync(file,JSON.stringify(combined,null,2)+'\n');
 console.log('Installed '+items.length+' reviewed exact model identities; catalog '+combined.length+'.');
}else console.log('Staged '+items.length+' geometry-distinct models; runtime catalog unchanged.');
