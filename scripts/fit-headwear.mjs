import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const ids=['axolotl-cap','fox-beanie','red-panda','creator-fox-hat','creator-frog-hat','orbit-helmet','star-witch'];
for(const directory of ['apps/launcher/public/assets/catalog','minecraft/votex-core/src/main/resources/assets/votex_client/catalog']){
  for(const id of ids){const file=path.join(root,directory,id+'.json'),model=JSON.parse(fs.readFileSync(file,'utf8'));if(model.fitRevision===1)continue;
    for(const part of model.parts.filter(p=>p.joint==='head')){
      for(let i=0;i<part.positions.length;i+=3){part.positions[i]*=1.045;part.positions[i+1]=(part.positions[i+1]+4)*1.025-4-.35;part.positions[i+2]*=1.045;}
      for(let i=0;i<part.normals.length;i+=3){let x=part.normals[i]/1.045,y=part.normals[i+1]/1.025,z=part.normals[i+2]/1.045;const length=Math.hypot(x,y,z)||1;part.normals.splice(i,3,x/length,y/length,z/length);}
    }
    model.fitRevision=1;fs.writeFileSync(file,JSON.stringify(model));
  }
}
console.log('Seven covering headwear models fitted in both catalogs; headphones and floating headwear preserved.');
