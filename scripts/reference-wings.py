"""Original voxel wing models inspired by the user's feather/leaf/membrane references.

Greedy surface rectangles preserve pixel silhouettes without hidden cube faces.
No images or meshes from the reference screenshots are shipped with the models.
"""
from pathlib import Path
import json, math
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
WEB=ROOT/'apps/launcher/public/assets/catalog'
CORE=ROOT/'minecraft/votex-core/src/main/resources/assets/votex_client/catalog'
FEATHER={
 0:[11],1:list(range(10,13)),2:list(range(9,13)),3:list(range(8,13)),
 4:list(range(8,14)),5:list(range(7,13)),6:list(range(6,12)),7:list(range(5,11)),
 8:list(range(4,10)),9:list(range(3,9)),10:list(range(2,8)),11:list(range(1,7)),
 12:list(range(1,6)),13:list(range(2,5)),14:[2,3],
}
FEATHER={(x,y) for y,xs in FEATHER.items() for x in xs}
LEAF={(x,y) for y,xs in enumerate([[3],[2,3,4],[1,2,3,4,5],list(range(7)),list(range(7)),[1,2,3,4,5],[2,3,4],[3],[3]]) for x in xs}

def part(side,kind='wing',phase=0,secondary=0,pivot=None,speed=1.15,secondary_axis='y'):
 motion={'type':kind,'side':side,'axis':'z','pivot':[side*2.6,2.5,4.1],
         'amplitude':.11,'speed':speed,'phase':phase}
 if secondary:motion.update(secondary=secondary,secondaryAxis=secondary_axis,secondaryPivot=pivot or [side*11,1,4.8])
 return {'joint':'body','positions':[],'normals':[],'colors':[],'quads':[],
         'material':{'color':'#ffffff','metalness':0,'roughness':.9,'opacity':1},'motion':motion}

def rgb(color,factor=1):
 return [round(int(color[i:i+2],16)/255*factor,4) for i in (1,3,5)]

def quad(p,vertices,color,uvs=None):
 a,b,c=vertices[:3];u=[b[i]-a[i] for i in range(3)];v=[c[i]-a[i] for i in range(3)]
 n=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]]
 length=math.sqrt(sum(x*x for x in n))
 if length<1e-8:return
 start=len(p['positions'])//3
 for i,vertex in enumerate(vertices):
  p['positions'].extend(round(x,3) for x in vertex)
  p['normals'].extend(round(x/length,4) for x in n);p['colors'].extend(color)
  if 'texture' in p:p['uvs'].extend(uvs[i] if uvs else [15.5/16,15.5/16])
 p['quads'].extend(range(start,start+4))

def rectangles(cells):
 remaining=dict(cells)
 while remaining:
  x,y=min(remaining,key=lambda q:(q[1],q[0]));color=remaining[(x,y)];w=1
  while remaining.get((x+w,y))==color:w+=1
  h=1
  while all(remaining.get((x+k,y+h))==color for k in range(w)):h+=1
  for yy in range(y,y+h):
   for xx in range(x,x+w):del remaining[(xx,yy)]
  yield x,y,w,h,color

def extrusion(p,cells,transform,depth=.4,z=0):
 origin=transform(0,0,0)
 axes=[[b-a for a,b in zip(origin,transform(*v))] for v in [(1,0,0),(0,1,0),(0,0,1)]]
 u,v,w=axes
 determinant=(u[1]*v[2]-u[2]*v[1])*w[0]+(u[2]*v[0]-u[0]*v[2])*w[1]+(u[0]*v[1]-u[1]*v[0])*w[2]
 def emit_face(vertices,color):quad(p,list(reversed(vertices)) if determinant<0 else vertices,color)
 def verts(x,y,w,h):
  return [transform(xx,yy,zz) for xx,yy,zz in [
   (x,y,z-depth/2),(x+w,y,z-depth/2),(x+w,y+h,z-depth/2),(x,y+h,z-depth/2),
   (x,y,z+depth/2),(x+w,y,z+depth/2),(x+w,y+h,z+depth/2),(x,y+h,z+depth/2)]]
 for x,y,w,h,color in rectangles(cells):
  v=verts(x,y,w,h);emit_face([v[k] for k in (4,5,6,7)],rgb(color));emit_face([v[k] for k in (0,3,2,1)],rgb(color,.86))
 for (x,y),color in cells.items():
  v=verts(x,y,1,1)
  for neighbour,face in [((x-1,y),(0,4,7,3)),((x+1,y),(1,2,6,5)),((x,y-1),(0,1,5,4)),((x,y+1),(3,7,6,2))]:
   if neighbour not in cells:
    vertices=[v[k] for k in face]
    quad(p,list(reversed(vertices)) if determinant<0 else vertices,rgb(color,.72))

def glyph(p,mask,side,x,y,z,length,angle,palette,root=(2.5,14.5),tip=(11.5,.5),gold=False):
 ax,ay=tip[0]-root[0],tip[1]-root[1];size=math.hypot(ax,ay);ax/=size;ay/=size
 # Lateral and axial coordinates permit independent scale and orientation.
 scale=length/size
 def transform(px,py,pz):
  vx,vy=px-root[0],py-root[1];a=(vx*ax+vy*ay)*scale;b=(-vx*ay+vy*ax)*scale
  return [side*(x+a*math.sin(angle)+b*math.cos(angle)),y+a*math.cos(angle)-b*math.sin(angle),z+pz*scale+a*.12+math.sin(a/length*math.pi)*.45]
 cells={};shaft={}
 for px,py in mask:
  spine=abs((px+.5-root[0])*(-ay)+(py+.5-root[1])*ax)<.6
  color=palette[2] if spine else palette[0 if (py//4)%2==0 else 1]
  cells[(px,py)]=color
  if spine:shaft[(px,py)]=palette[2]
 if p.get('texture'):
  # Small warped tiles follow the feather's curl; alpha carries its exact pixel outline.
  for tx in range(0,16,4):
   for ty in range(0,16,4):
    if not any((xx,yy) in mask for xx in range(tx,tx+4) for yy in range(ty,ty+4)):continue
    for sign in [-1,1]:
     coords=[(tx,ty),(tx+4,ty),(tx+4,ty+4),(tx,ty+4)]
     if side*sign>0:coords.reverse()
     quad(p,[transform(xx,yy,sign*.4/scale) for xx,yy in coords],rgb('#ffffff',1 if sign>0 else .86),[[xx/16,yy/16] for xx,yy in coords])
  for (px,py),color in cells.items():
   for neighbour,edge in [((px-1,py),[(px,py),(px,py+1)]),((px+1,py),[(px+1,py+1),(px+1,py)]),((px,py-1),[(px+1,py),(px,py)]),((px,py+1),[(px,py+1),(px+1,py+1)])]:
    if neighbour in mask:continue
    (x0,y0),(x1,y1)=edge
    vv=[transform(x0,y0,-.4/scale),transform(x1,y1,-.4/scale),transform(x1,y1,.4/scale),transform(x0,y0,.4/scale)]
    quad(p,vv if side>0 else list(reversed(vv)),rgb(color,.72))
  rod(p,transform(*root,.52/scale),transform(*tip,.52/scale),.25,palette[2])
 else:
  extrusion(p,cells,transform,.8/scale)
  if shaft:extrusion(p,shaft,transform,.14/scale,z=.48/scale)

def rod(p,a,b,width,color):
 # Bevel-free voxel strut with its long axis following a structural bone.
 d=[b[i]-a[i] for i in range(3)];length=math.sqrt(sum(v*v for v in d));dx,dy,dz=[v/length for v in d]
 u=[-dy,dx,0];l=math.hypot(*u[:2]);u=[v/l*width/2 for v in u]
 v=[dy*u[2]-dz*u[1],dz*u[0]-dx*u[2],dx*u[1]-dy*u[0]]
 points=[]
 for end in [a,b]:
  for su,sv in [(-1,-1),(1,-1),(1,1),(-1,1)]:points.append([end[k]+su*u[k]+sv*v[k] for k in range(3)])
 for face in [(0,3,2,1),(4,5,6,7),(0,4,7,3),(1,2,6,5),(0,1,5,4),(3,7,6,2)]:quad(p,[points[k] for k in face],rgb(color))

def base(id,name,description):
 return {'schemaVersion':2,'id':id,'name':name,'slot':'wings','description':description,'collection':'Votex Originals',
         'designRevision':'reference-wings-1','designReference':'User-supplied visual references; original Votex geometry',
         'attachment':{'anchor':[0,2.5,4.1]},'parts':[]}

def feather_wings(id,name,palette,ascending=False,gold=False):
 m=base(id,name,'Einzeln geschichtete, räumliche Pixel-Federn mit beweglichen Flügelspitzen.' )
 texture=id+'-feather.png'
 im=Image.new('RGBA',(16,16))
 for px,py in FEATHER:
  spine=abs((px+.5-2.5)*(14/math.sqrt(277))+(py+.5-14.5)*(9/math.sqrt(277)))<.6
  color=palette[2] if spine else palette[0 if (py//4)%2==0 else 1]
  im.putpixel((px,py),tuple(int(color[k:k+2],16) for k in (1,3,5))+(255,))
 im.putpixel((15,15),(255,255,255,255))
 for directory in [WEB/'textures',CORE.parent/'textures/cosmetics']:im.save(directory/texture)
 for side in [-1,1]:
  root=part(side);tips=part(side,secondary=.5,pivot=[side*11,3,4.6])
  for p in [root,tips]:p.update(texture=texture,uvs=[])
  for i in range(8):
   if ascending:
    x,y=3.1+i*.26,4.5-i*.22;length=6+i*1.4;angle=.55+i*.25
   else:x,y=3.2+i*1.1,.7-math.sin(i/7*math.pi)*1.5;length=9.5+i*.7;angle=.12+i*.05
   glyph(tips if i>=4 else root,FEATHER,side,x,y,4.4+i*.06,length,angle,palette,gold=gold)
  for i in range(6):
   glyph(root,FEATHER,side,3.4+i*1.2,.1-i*.14 if ascending else -.4,5.4+i*.06,5.3+i*.3,.8 if ascending else .35+i*.035,[palette[1],palette[0],palette[2]],gold=gold)
  if gold:
   for a,b in [((2.8,4.6,5.6),(7.5,.2,6.1)),((7.5,.2,6.1),(12,-4,6.7)),((12,-4,6.7),(17.4,-8.1,7.1))]:
    for offset in [-1.75,.6]:
     rod(root,[side*a[0],a[1],a[2]+offset],[side*b[0],b[1],b[2]+offset],.55,'#e7aa48')
  m['parts'] += [root,tips]
 return m

def inside(x,y,polygon):
 result=False;j=len(polygon)-1
 for i,(xi,yi) in enumerate(polygon):
  xj,yj=polygon[j]
  if (yi>y)!=(yj>y) and x<(xj-xi)*(y-yi)/(yj-yi)+xi:result=not result
  j=i
 return result

def demon():
 m=base('crimson-dread-wings','Crimson Dread','Dunkelrote, gestufte Membranen mit räumlichen Rippen und gebogenen Hornspitzen.')
 polygon=[(2.6,3),(4,-6.5),(11.5,-7.7),(21,-5.3),(18,-2.1),(15,.2),(12,4.1),(10,8),(7,5),(4.3,7),(3.4,10.8),(2.3,9)]
 for side in [-1,1]:
  membrane=part(side,secondary=.25,pivot=[side*10,1,4.7],speed=.9);bones=part(side,secondary=.25,pivot=[side*10,1,4.7],speed=.9)
  cells={}
  for yy in range(-13,22):
   for xx in range(4,43):
    x,y=(xx+.5)*.5,(yy+.5)*.5
    if inside(x,y,polygon):
     shade=min(5,max(0,int((x-3)*.12+(y+7)*.18)+((xx*13+yy*7)%11==0)))
     cells[(xx,yy)]=['#3e1c26','#4c1e2a','#5c2230','#6d2936','#7b2f3b','#883640'][shade]
  extrusion(membrane,cells,lambda x,y,z:[side*x*.5,y*.5,4.5+z],.42)
  lines=[[(2.8,3),(4,-6.6),(11.5,-7.8),(21,-5.3),(22,-5.9)],[(3,3),(9.3,.8),(15.5,-.1)],[(3,3),(6.9,5.1),(10.1,8.2)],[(4,-6.5),(4.7,-9),(4.2,-10.4)],[(3,3),(3.4,10.8)]]
  for line in lines:
   for a,b in zip(line,line[1:]):
    for depth in [4.95,4.05]:rod(bones,[side*a[0],a[1],depth],[side*b[0],b[1],depth],.65,'#241e23')
  m['parts'] += [membrane,bones]
 return m

def autumn():
 m=base('autumn-canopy-wings','Autumn Canopy','Überlappende Eichenblätter, verzweigte Voxel-Äste und nachschwingende Blattspitzen.')
 palettes=[['#c58b32','#a56828','#9f7635'],['#b96b2b','#925327','#ad7730'],['#b99b3b','#847b31','#9b8737']]
 for side in [-1,1]:
  root=part(side,speed=.85);tips=part(side,secondary=.6,pivot=[side*11,1,5.1],speed=.85)
  for i in range(7):
   x=3.8+i*1.65;y=3.6-i*.95
   glyph(tips if i>=4 else root,LEAF,side,x,y,4.5+i*.05,6.8+i*.24,1.2+i*.165,palettes[i%3],root=(3.5,8.5),tip=(3.5,.5))
   glyph(root,LEAF,side,x+.2,y+1.1,5.05+i*.04,5.6+i*.15,.25+i*.08,palettes[(i+1)%3],root=(3.5,8.5),tip=(3.5,.5))
  for a,b in [((2.6,3),(8,-.5)),((8,-.5),(15,-5.5)),((2.6,3),(10,5.2))]:rod(root,[side*a[0],a[1],5.3],[side*b[0],b[1],5.3],.55,'#694321')
  m['parts'] += [root,tips]
  for i in range(2):
   p=part(side,phase=i*1.1,secondary=.7,pivot=[side*(6+i*3),5,5.2],speed=.85,secondary_axis='z')
   rod(p,[side*(6+i*3),5,5.2],[side*(7+i*3),9+i,5.2],.2,'#936126')
   glyph(p,LEAF,side,7+i*3,9+i,5.2,3.5,.2+i*.3,palettes[i],root=(3.5,8.5),tip=(3.5,.5))
   m['parts'].append(p)
 return m

models=[
 feather_wings('vanilla-feather-wings','Vanilla Feather Wings',['#eeeade','#d4dedc','#a6b4b6']),
 demon(),autumn(),
 feather_wings('ivory-seraph-wings','Ivory Seraph',['#faf2dc','#dbe9e4','#c5bfa1'],True,True),
 feather_wings('obsidian-seraph-wings','Obsidian Seraph',['#241729','#3a263d','#52394e'],True,True),
]
for model in models:
 for p in model['parts']:
  assert len(p['positions'])<=60000 and len(p['quads'])<=24000,(model['id'],'part budget')
 assert sum(len(p['quads']) for p in model['parts'])<=100000,model['id']
 data=json.dumps(model,ensure_ascii=False,separators=(',',':')).encode()
 assert len(data)<1_950_000,(model['id'],len(data))
 for directory in [WEB,CORE]:(directory/(model['id']+'.json')).write_bytes(data)
 print(model['id'],sum(len(p['quads'])//4 for p in model['parts']),'quads',len(data),'bytes')

new_ids={m['id'] for m in models};entries=[{k:m[k] for k in ['id','name','slot','description','collection']} for m in models]
backup=ROOT/'work/cosmetics2-20261106/before-reference-index.json'
if not backup.exists():backup.write_bytes((WEB/'index.json').read_bytes())
for path in [WEB/'index.json',CORE/'index.json',ROOT/'apps/launcher/src/data/cosmetic-manifest.json']:
 items=[m for m in json.loads(path.read_text(encoding='utf-8')) if m['id'] not in new_ids]
 items=items[:1]+entries+items[1:]
 path.write_text(json.dumps(items,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
