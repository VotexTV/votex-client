"""Focused authored voxel feathers; preserves IDs and unrelated catalogue entries."""
from pathlib import Path
from copy import deepcopy
import json,math
from PIL import Image
R=Path(__file__).resolve().parents[1];W=R/'apps/launcher/public/assets/catalog';C=R/'minecraft/votex-core/src/main/resources/assets/votex_client/catalog';B=R/'work/cosmetics2-20261106/original-models';B.mkdir(exist_ok=True)
def load(id):
 p=B/(id+'.json')
 if not p.exists():p.write_bytes((W/p.name).read_bytes())
 return json.loads(p.read_text(encoding='utf-8'))
def save(m):
 if m['id']=='creator-swan-wings':
  # This licensed source has dense fitted geometry. Quantize to hundredths of a
  # player pixel; the maximum movement error stays below 0.01 px and the model
  # remains within the Core's bounded 2 MB catalogue limit.
  for p in m['parts']:
   for key in ['positions','normals','colors']:
    p[key]=[round(v,2) if isinstance(v,float) else v for v in p[key]]
 data=json.dumps(m,separators=(',',':'),ensure_ascii=False).encode();(W/(m['id']+'.json')).write_bytes(data);(C/(m['id']+'.json')).write_bytes(data)
def part(motion=None,color='#ffffff'):
 p=dict(joint='body',positions=[],normals=[],colors=[],quads=[],uvs=[],texture='votex-feather-2.png',pixelated=True,material=dict(color='#ffffff',metalness=0,roughness=.9,opacity=1))
 if motion:p['motion']=motion
 return p
def solid(color='#ffffff'):
 p=part(color=color);p.pop('texture',None);p.pop('pixelated',None);p['material']={'color':'#ffffff','metalness':0,'roughness':.8,'opacity':1};return p
def fan_segment(p,offset,x,y,angle):
 # Rotate the complete feather segment, including its normal and raised shaft.
 # The individual feathers can vary in scale and direction within one wing.
 c,s=math.cos(angle),math.sin(angle)
 for i in range(offset,len(p['positions']),3):
  px,py=p['positions'][i]-x,p['positions'][i+1]-y
  p['positions'][i:i+2]=[round(x+px*c-py*s,4),round(y+px*s+py*c,4)]
  nx,ny=p['normals'][i:i+2];p['normals'][i:i+2]=[round(nx*c-ny*s,4),round(nx*s+ny*c,4)]
def box(p,x,y,z,w,h,d,color):
 rgb=[int(color[i:i+2],16)/255 for i in (1,3,5)];v=[(x-w/2,y-h/2,z-d/2),(x+w/2,y-h/2,z-d/2),(x+w/2,y+h/2,z-d/2),(x-w/2,y+h/2,z-d/2),(x-w/2,y-h/2,z+d/2),(x+w/2,y-h/2,z+d/2),(x+w/2,y+h/2,z+d/2),(x-w/2,y+h/2,z+d/2)]
 for face in [(0,3,2,1),(4,5,6,7),(0,4,7,3),(1,2,6,5),(0,1,5,4),(3,7,6,2)]:
  a,b,c=[v[i] for i in face[:3]];u=[b[i]-a[i] for i in range(3)];t=[c[i]-a[i] for i in range(3)];n=[u[1]*t[2]-u[2]*t[1],u[2]*t[0]-u[0]*t[2],u[0]*t[1]-u[1]*t[0]];l=math.sqrt(sum(i*i for i in n));base=len(p['positions'])//3
  for k in face:p['positions'].extend(round(i,4) for i in v[k]);p['normals'].extend(round(i/l,4) for i in n);p['colors'].extend(rgb)
  p['quads'].extend(range(base,base+4));p['uvs'].extend([0,0,1,0,1,1,0,1])
def feathers(id,dark=False):
 m=load(id);m['parts']=[];m['rig']='feathered';m['attachment']={'anchor':[0,3,3.5]};m['designRevision']='cosmetics2-voxel-feathers-1';m['description']='Geschichtete Pixel-Federn mit gestuften Spitzen, sichtbaren Kielen und beweglichen Außenfedern.'
 palette=['#222b38','#3a4656','#586676'] if dark else ['#c6d4df','#e4edf1','#f5f4e8']
 for side in [-1,1]:
  motion={'type':'wing','side':side,'axis':'z','pivot':[side*2.5,3,3.6]};root=part(motion);tips=part({**motion,'secondary':.55,'secondaryPivot':[side*10,0,4.5]})
  # Continuous stepped upper coverts; no detached curved rod.
  for i in range(9):box(root,side*(3.4+i*1.55),2.2-math.sin(i/8*math.pi)*5,4.7,2.3,2.2,.95,palette[1])
  for row,count in [(0,10),(1,9),(2,8)]:
   for i in range(count):
    start=3.5+i*1.55+(row*.45);sy=1.7-math.sin(i/(count-1)*math.pi)*4.8+row*.6
    length=(7.6+math.sin(i/(count-1)*math.pi)*3.5+(2.4 if dark else 0))*(1-row*.27);drift=(i/(count-1)-.25)*(3.3 if dark else 2.4);p=tips if row==0 and i>=4 else root
    for j in range(3):
     offset=len(p['positions'])
     f=(j+.5)/3;width=(1.8-row*.12)*(1-j*.24);x=side*start;yy=sy+length*f;zz=4.35+row*.6+i*.035
     box(p,x,yy,zz,width,length/3,.55,palette[(j+row)%3]);box(p,x,yy,zz+.31,.18,length/3,.12,palette[0 if dark else 1])
     fan_segment(p,offset,side*start,sy,side*(.09-i/(count-1)*.32))
  m['parts'] += [root,tips]
 # Keep the original model/source credit in the catalog even when the mesh is
 # rebuilt as voxel geometry. The changes note distinguishes source credit
 # from the Votex-authored redesign.
 if 'creator' in m:
  m['creator']['changes']='Geschichtete, spiegelbildlich voxelartige Pixel-Federgeometrie für Votex neu aufgebaut; DevMops bleibt als ursprüngliche Quelle der Engel-Flügel-Inspiration genannt.'
 m['collection']='Votex Originals';save(m)
def swan_feathers():
 m=load('creator-swan-wings');m['parts']=[];m['rig']='feathered';m['attachment']={'anchor':[0,2.5,3.8]};m['designRevision']='cosmetics2-swan-voxel-feathers-1'
 m['description']='Gehobene, geschichtete Pixel-Federn mit symmetrischer Form und sanft beweglichen Außenfedern.'
 m['creator']['changes']='Für Votex in eine kompakte, symmetrische Minecraft-Federform neu gestaltet und am Spieler befestigt; ursprüngliche Schwanenflügel als Designquelle, Michael Fuchs und CC BY 3.0 beibehalten.'
 palette=['#c8d5df','#e5edf0','#faf7eb']
 for side in [-1,1]:
  motion={'type':'wing','side':side,'axis':'z','pivot':[side*2.3,1.8,3.7]};root=part(motion);tips=part({**motion,'secondary':.42,'secondaryPivot':[side*10,1.2,4.8]})
  for i in range(8):
   x=side*(3.1+i*1.35);y=1.2+math.sin(i/7*math.pi)*2.5
   box(root,x,y,4.65,2.0,2.0,.8,palette[1])
  for row,count in [(0,8),(1,7),(2,6)]:
   for i in range(count):
    t=i/(count-1);start=3.0+i*1.55+row*.35;base=0.2+math.sin(t*math.pi)*2.6+row*.6
    length=(7.1+math.sin(t*math.pi)*2.5-row*1.25);drift=(t-.23)*2.1;p=tips if row==0 and i>=5 else root
    for j in range(3):
     f=(j+.5)/3;x=side*(start+drift*f);y=base+length*f;z=4.1+row*.48
     width=(1.62-row*.12)*(1-j*.18)
     box(p,x,y,z,width,length/3+.16,.62,palette[(i+j+row)%3])
     box(p,x,y,z+.34,.16,length/3+.06,.12,palette[0])
  m['parts'] += [root,tips]
 save(m)
def split(m,transform):
 out=[]
 for source in m['parts']:
  groups={}
  for n in range(0,len(source['quads']),4):
   inds=source['quads'][n:n+4];cx=sum(source['positions'][i*3] for i in inds)/4;side=-1 if cx<0 else 1;p=groups.setdefault(side,{**deepcopy(source),'positions':[],'normals':[],'colors':[],'quads':[],**({'uvs':[]} if 'uvs' in source else {})})
   for idx in inds:
    pos=transform(source['positions'][idx*3:idx*3+3],side);p['quads'].append(len(p['positions'])//3);p['positions']+=pos;p['normals']+=source['normals'][idx*3:idx*3+3];p['colors']+=source['colors'][idx*3:idx*3+3]
    if 'uvs' in source:p['uvs']+=source['uvs'][idx*2:idx*2+2]
  for side,p in groups.items():p['motion']={'type':'wing','side':side,'axis':'z','pivot':[side*2.4,2.5,3.7]};out.append(p)
 m['parts']=out;m['attachment']={'anchor':[0,2.5,3.7]};return m
feathers('cloud-feathers');feathers('midnight-raven',True);swan_feathers()
def voxel_headset(id,orbital=False):
 m=load(id);frame=solid();detail=solid();mic=solid()
 for p in [frame,detail,mic]:p['joint']='head'
 accent='#b396ff' if orbital else '#51c7df'
 box(frame,0,-8.85,0,10.2,.4,1.8,'#263342')
 box(detail,0,-9.075,0,7.8,.1,.7,accent)
 for side in [-1,1]:
  box(frame,side*5,-8,0,.6,1.7,1.8,'#263342')
  box(frame,side*5,-6.8,0,.6,1.4,1.8,'#263342')
  box(frame,side*5.35,-5.05,0,1.35,2.3,2.3,'#263342')
  box(frame,side*5.35,-6.3,0,1.1,.35,1.9,'#334658')
  box(frame,side*5.35,-3.75,0,1.1,.35,1.9,'#334658')
  box(frame,side*4.64,-5.05,0,.1,2.05,2.05,'#14202b')
  if orbital:
   # A distinct stepped orbital ear grille, rather than a recoloured studio cup.
   for y,z in [(-5.8,-.65),(-5.8,0),(-5.8,.65),(-5.05,-.9),(-5.05,.9),(-4.3,-.65),(-4.3,0),(-4.3,.65)]:box(detail,side*6.1,y,z,.12,.3,.3,accent)
   box(detail,side*6.12,-5.05,0,.15,.55,.55,'#edf3ff')
   box(detail,side*5,-9.45,0,.55,.8,.8,'#334658')
  else:
   for z in [-.65,0,.65]:box(detail,side*6.1,-5.05,z,.12,1.4,.18,accent)
 # The boom points toward the face (-Z), with space outside the outer skin layer.
 box(mic,-5.4,-4.5,-2.8,.32,.32,3.4,'#263342')
 box(mic,-4.65,-4.2,-4.9,1.5,.32,.32,'#334658')
 box(mic,-3.8,-3.9,-4.95,.8,.55,.65,'#14202b')
 box(detail,-3.8,-3.9,-5.3,.35,.22,.1,accent)
 m['parts']=[frame,detail,mic];m['designRevision']='cosmetics2-voxel-headset-2'
 m['description']='Ein gestuftes Voxel-Headset mit Ohrpolstern, Mikrofon und Freiraum für Schulterbegleiter.'
 if 'creator' in m:m['creator']['changes']='Für Votex als gestuftes Voxel-Headset mit gepolsterten Ohrmuscheln und Mikrofon neu gestaltet; J-Toastie und CC BY 3.0 als ursprüngliche Designquelle beibehalten.'
 save(m)
voxel_headset('creator-headphones');voxel_headset('orbit-headphones',True)
# Small, low-cost shoulder companion motion: gentle breathing and a damped tail wag.
fox=load('pocket-fox')
fox['parts'][0]['motion']={'type':'oscillate','axis':'y','amplitude':.012,'speed':.75,'phase':0,'pivot':[-6,-.2,0]}
for index,phase in [(9,0),(10,.18)]:fox['parts'][index]['motion']={'type':'oscillate','axis':'y','amplitude':.105 if index==9 else .08,'speed':.95,'phase':phase,'pivot':[-4.9,-1.1,1.2]}
fox['description']='Ein kleiner Schulterfuchs mit ruhiger Atembewegung und sanft nachschwingendem Schwanz.';fox['designRevision']='cosmetics2-pocket-fox-motion-1';save(fox)
woodland=load('creator-fox')
xs=[p['positions'][i] for p in woodland['parts'] for i in range(0,len(p['positions']),3)]
ys=[p['positions'][i+1] for p in woodland['parts'] for i in range(0,len(p['positions']),3)]
dx,dy=-4.75-max(xs),.1-max(ys)
for p in woodland['parts']:
 for i in range(0,len(p['positions']),3):
  p['positions'][i]=round(p['positions'][i]+dx,5);p['positions'][i+1]=round(p['positions'][i+1]+dy,5)
woodland['designRevision']='cosmetics2-woodland-fox-clearance-1'
woodland['creator']['changes']=woodland['creator'].get('changes','')+' Votex Cosmetics 2: Position seitlich vom Kopf korrigiert; tiefste Pfotenfläche an Schulterhöhe ausgerichtet.'
save(woodland)
m=split(load('creator-bat-wings'),lambda p,s:[round(p[0]*.91,4),p[1],p[2]]);m['rig']='membrane';m['designRevision']='cosmetics2-compact-membrane-1'
for wing in m['parts']:wing['motion']['pivot'][0]=round(wing['motion']['pivot'][0]*.91,4)
m['creator']['changes']='Für Votex um 9 Prozent schmaler angepasst, in links/rechts bewegliche Flügel geteilt und am Rücken befestigt; Autor und CC BY 3.0 beibehalten.';save(m)
# Monochrome pixel feather striation; vertex palettes supply each model's own colors.
im=Image.new('RGBA',(16,16));im.putdata([(v,v,v,255) for y in range(16) for x in range(16) for v in [255 if x in [7,8] else 214 if (x+y*2)%7==0 else 238]])
for p in [W/'textures/votex-feather-2.png',C.parent/'textures/cosmetics/votex-feather-2.png']:im.save(p)
# Keep the same IDs and selections in both catalogue indices and the launcher manifest.
for path in [W/'index.json',C/'index.json',R/'apps/launcher/src/data/cosmetic-manifest.json']:
 data=json.loads(path.read_text(encoding='utf-8'))
 for entry in data:
  if entry['id'] in ['cloud-feathers','midnight-raven']:
   model=json.loads((W/(entry['id']+'.json')).read_text(encoding='utf-8'));entry['description']=model['description'];entry['collection']='Votex Originals'
  if entry['id'] in ['creator-headphones','creator-bat-wings','creator-swan-wings','creator-fox']:
   model=json.loads((W/(entry['id']+'.json')).read_text(encoding='utf-8'));entry['creator']=model['creator']
   if entry['id'] in ['creator-headphones','creator-swan-wings']:entry['description']=model['description']
  if entry['id']=='pocket-fox':
   model=json.loads((W/(entry['id']+'.json')).read_text(encoding='utf-8'));entry['description']=model['description']
  if entry['id']=='orbit-headphones':entry['description']=json.loads((W/(entry['id']+'.json')).read_text(encoding='utf-8'))['description']
 path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Four wing models, headset and companion revisions synchronized; source credits retained.')
