"""Votex Atelier base models: original surfaces shared by the launcher and Core.
Run import-creator-cosmetics.mjs and generate-creator-thumbnails.py afterward.
Restore the reviewed curated collection last with import-kenney-cosmetics.mjs --install.
"""
import math,json
from pathlib import Path
R=Path(__file__).resolve().parents[1];WEB=R/'apps/launcher/public/assets/catalog';CORE=R/'minecraft/votex-core/src/main/resources/assets/votex_client/catalog'
OLD=json.loads((WEB/'index.json').read_text(encoding='utf8'));CURRENT=[];PARTS=[]
M=math

def add(a,b):return [a[i]+b[i] for i in range(3)]
def sub(a,b):return [a[i]-b[i] for i in range(3)]
def mul(a,s):return [n*s for n in a]
def norm(a):
 d=sum(n*n for n in a)**.5
 return [n/d for n in a] if d>1e-9 else [0,0,-1]
def cross(a,b):return [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
def rgb(c):return [int(c[i:i+2],16)/255 for i in (1,3,5)]
def mix(a,b,t):return [a[i]*(1-t)+b[i]*t for i in range(len(a))]
def bez(a,b,c,t):return add(add(mul(a,(1-t)**2),mul(b,2*t*(1-t))),mul(c,t*t))
def part(joint='body',color='#dde4ed',metal=.0,rough=.6,opacity=1,motion=None):
 p=dict(joint=joint,positions=[],colors=[],normals=[],quads=[],material=dict(color='#ffffff',metalness=metal,roughness=rough,opacity=opacity))
 p['_c']=rgb(color)
 if motion:p['motion']=motion
 PARTS.append(p);return p

def vertex(p,pos,color=None):
 i=len(p['positions'])//3;p['positions']+=pos;p['colors']+=color or p['_c'];return i

def quad(p,a,b,c,d):p['quads'] += [a,b,c,d]
def finalize(p):
 n=[[0.,0.,0.] for _ in range(len(p['positions'])//3)];v=p['positions']
 for i in range(0,len(p['quads']),4):
  a,b,c,d=p['quads'][i:i+4];nrm=cross(sub(v[b*3:b*3+3],v[a*3:a*3+3]),sub(v[c*3:c*3+3],v[a*3:a*3+3]))
  for x in (a,b,c,d):n[x]=add(n[x],nrm)
 p['normals']=[round(x,4) for nn in n for x in norm(nn)]
 p['positions']=[round(x,4) for x in p['positions']];p['colors']=[round(x,4) for x in p['colors']];p.pop('_c')

def ell(j,c,r,color,seg=20,rings=10,metal=0,rough=.6,opacity=1,motion=None):
 p=part(j,color,metal,rough,opacity,motion)
 for iy in range(rings+1):
  th=M.pi*iy/rings
  for ix in range(seg+1):
   a=M.tau*ix/seg;v=[c[0]+r[0]*M.sin(th)*M.cos(a),c[1]-r[1]*M.cos(th),c[2]+r[2]*M.sin(th)*M.sin(a)]
   tint=mix(rgb(color),[1,1,1],.08*(1-iy/rings));vertex(p,v,tint)
 for y in range(rings):
  for x in range(seg):a=y*(seg+1)+x;quad(p,a,a+seg+1,a+seg+2,a+1)
 return p

def tube(j,path,r,color,radial=7,metal=0,rough=.5,motion=None):
 p=part(j,color,metal,rough,motion=motion);count=len(path)
 for i,center in enumerate(path):
  tangent=norm(sub(path[min(count-1,i+1)],path[max(0,i-1)]));normal=norm(cross(tangent,[0,0,1] if abs(tangent[2])<.9 else [0,1,0]));bi=cross(tangent,normal)
  radius=r[i] if isinstance(r,list) else r
  for k in range(radial+1):a=k*M.tau/radial;vertex(p,add(center,add(mul(normal,M.cos(a)*radius),mul(bi,M.sin(a)*radius))))
 for i in range(count-1):
  for k in range(radial):a=i*(radial+1)+k;quad(p,a,a+1,a+radial+2,a+radial+1)
 return p

def curve(j,a,b,c,r,color,steps=12,**kw):return tube(j,[bez(a,b,c,i/steps) for i in range(steps+1)],r,color,**kw)
def torus(j,c,r,t,color,axis='y',seg=40,rs=6,metal=0,rough=.4,**kw):
 p=part(j,color,metal,rough,**kw)
 for i in range(seg+1):
  a=i*M.tau/seg
  for k in range(rs+1):
   b=k*M.tau/rs;v=[(r+t*M.cos(b))*M.cos(a),t*M.sin(b),(r+t*M.cos(b))*M.sin(a)]
   if axis=='z':v=[v[0],v[2],v[1]]
   if axis=='x':v=[v[1],v[0],v[2]]
   vertex(p,add(c,v))
 for i in range(seg):
  for k in range(rs):a=i*(rs+1)+k;quad(p,a,a+rs+1,a+rs+2,a+1)
 return p

def rounded(j,c,size,r,color,metal=0,rough=.5):
 p=part(j,color,metal,rough);outline=[]
 w,h,d=[v/2 for v in size];r=min(r,w,h)
 for ox,oy,base in [(w-r,-h+r,-M.pi/2),(w-r,h-r,0),(-w+r,h-r,M.pi/2),(-w+r,-h+r,M.pi)]:
  for k in range(6):a=base+k*M.pi/10;outline.append((ox+M.cos(a)*r,oy+M.sin(a)*r))
 # bevel silhouette through four depth rings
 for z,scale in [(-d,.92),(-d+.3,1),(d-.3,1),(d,.92)]:
  for x,y in outline:vertex(p,add(c,[x*scale,y*scale,z]))
 n=len(outline)
 for l in range(3):
  for i in range(n):quad(p,l*n+i,l*n+(i+1)%n,(l+1)*n+(i+1)%n,(l+1)*n+i)
 for side,level in [(-1,0),(1,3)]:
  center=vertex(p,add(c,[0,0,side*d]))
  for i in range(n):
   a=level*n+i;b=level*n+(i+1)%n
   quad(p,center,b,a,a) if side<0 else quad(p,center,a,b,b)
 return p

def motion(s):return dict(type='wing',side=s,pivot=[s*2.6,3,3])
def feather(s,start,control,end,width,c1,c2,gloss=.6):
 steps=12;cross_n=4
 p=part('body',c1,.04,gloss,motion=motion(s))
 for face in [-1,1]:
  offset=len(p['positions'])//3
  for i in range(steps+1):
   t=i/steps;center=bez(start,control,end,t);d=norm(sub(bez(start,control,end,min(1,t+.01)),bez(start,control,end,max(0,t-.01))));perp=norm([-d[1],d[0],0]);wid=width*(.1+.9*M.sin(M.pi*t)**.72)*(1-.4*t)
   for k in range(cross_n+1):
    u=k/cross_n*2-1;v=add(center,mul(perp,wid*u));v[2]+=face*(.07+.17*(1-u*u))*M.sin(M.pi*t);color=mix(rgb(c1),rgb(c2),t*.75+abs(u)*.12);vertex(p,v,color)
  for i in range(steps):
   for k in range(cross_n):
    a=offset+i*(cross_n+1)+k
    quad(p,a,a+1,a+cross_n+2,a+cross_n+1) if face>0 else quad(p,a,a+cross_n+1,a+cross_n+2,a+1)
 return p

def end(id,description=None):
 global PARTS
 item=next(i for i in OLD if i['id']==id).copy();item['schemaVersion']=2;item.pop('creator',None)
 if id=='cloud-feathers':item.update(name='Cloud Feathers',collection='Cloudworks')
 if description:item['description']=description
 for p in PARTS:finalize(p)
 model=dict(**item,parts=PARTS)
 assert sum(len(p['quads'])//4 for p in PARTS)<6000,(id,sum(len(p['quads'])//4 for p in PARTS))
 CURRENT.append(model);PARTS=[]
# Individual curved flight feathers, not columns of boxes.
for id,c1,c2 in [('cloud-feathers','#7894bd','#f5f4ee'),('solar-phoenix','#a82d32','#ffe8a2'),('midnight-raven','#131d35','#637eab')]:
 for s in [-1,1]:
  curve('body',[s*3,4,3],[s*9,-9,4],[s*22,-9,5],.45,c1,metal=.2,motion=motion(s))
  count={'cloud-feathers':12,'solar-phoenix':9,'midnight-raven':10}[id]
  for i in range(count):
   t=i/(count-1);arc={'cloud-feathers':17,'solar-phoenix':21,'midnight-raven':19}[id];x=4+arc*t
   y=3-({'cloud-feathers':11,'solar-phoenix':7,'midnight-raven':4}[id])*M.sin(t*M.pi/2)
   sweep={'cloud-feathers':3.8,'solar-phoenix':7,'midnight-raven':5}[id]
   length={'cloud-feathers':12-2*t,'solar-phoenix':10+5*M.sin(t*M.pi),'midnight-raven':13+3*t}[id]
   width={'cloud-feathers':1.05,'solar-phoenix':.9,'midnight-raven':1.5}[id]
   feather(s,[s*x,y,4+t],[s*(x+sweep),y+length*.35,5.7+t],[s*(x+sweep),y+length,6+t],width,c1,c2,.35 if id=='solar-phoenix' else .6)
  for i in range(6 if id=='midnight-raven' else 5):feather(s,[s*(3+i*2.1),2-i*.9,3.3],[s*(5+i*2.1),3-i*.8,3.5],[s*(7+i*2.1),6-i*1.1,4],1.4 if id=='midnight-raven' else 1.15,c1,c2)
  if id=='solar-phoenix':
   for i in range(2):feather(s,[s*(6+i*2),4,4],[s*(11+i*3),13,5],[s*(9+i*5),23-i*2,6],.8,c1,c2,.35)
 end(id,{'cloud-feathers':'Weiche, einzeln geschwungene Flugfedern mit perlmuttfarbenen Spitzen.','solar-phoenix':'Weit geschwungene Flammenfedern und zwei langgezogene goldene Schweifspitzen.','midnight-raven':'Breite dunkle Schwungfedern, eine kraftvolle Silhouette und dicht gefächerte Schultern.'}[id])
# Continuous silk membranes with curved boundary and drawn veins.
def membrane(s,boundary,center,base,edge,eyes=False):
 p=part('body',base,.05,.36,1,motion(s));n=len(boundary);rings=6
 for r in range(rings+1):
  t=r/rings
  for x,y in boundary:
   xx=center[0]+(x-center[0])*t;yy=center[1]+(y-center[1])*t;z=4+.65*M.sin(t*M.pi)+.5*M.sin(yy*.2)
   col=mix(rgb(base),rgb(edge),max(0,(t-.65)/.35))
   if eyes:
    dist=((xx-16)/2.8)**2+((yy+2)/2.4)**2
    if dist<1.2:col=rgb('#d4bf83') if dist>.48 else rgb('#354849')
   vertex(p,[s*xx,yy,z],col)
 for r in range(rings):
  for i in range(n):a=r*n+i;b=r*n+(i+1)%n;quad(p,a,b,b+n,a+n) if s>0 else quad(p,a,a+n,b+n,b)
 return p
# Smooth Catmull-Rom closed outlines.
def outline(points,steps=5):
 result=[]
 for i in range(len(points)):
  p0=points[(i-1)%len(points)];p1=points[i];p2=points[(i+1)%len(points)];p3=points[(i+2)%len(points)]
  for k in range(steps):
   t=k/steps;result.append([.5*((2*p1[j])+(-p0[j]+p2[j])*t+(2*p0[j]-5*p1[j]+4*p2[j]-p3[j])*t*t+(-p0[j]+3*p1[j]-3*p2[j]+p3[j])*t*t*t) for j in range(2)])
 return result
for s in [-1,1]:
 border=outline([(3,3),(9,-7),(19,-11),(24,-4),(20,3),(14,6),(18,12),(13,16),(7,11)],5)
 membrane(s,border,[8,4],'#b9cc9b','#30483b',True)
 for x,y in [(19,-9),(23,-3),(13,15)]:curve('body',[s*4,4,4],[s*11,y*.5,4.7],[s*x,y,4.1],.07,'#677859',radial=5,motion=motion(s))
 curve('body',[s*8,11,4],[s*13,17,4],[s*12,22,4],.17,'#c3b57d',motion=motion(s))
end('luna-moth','Seidig geschwungene Flügellappen, organische Augenzeichnung, zarte Adern und lange Schleifen.')
for s in [-1,1]:
 points=[(3,5),(8,-8),(20,-12),(23,-7),(27,2),(21,2),(20,10),(14,6),(8,12),(5,8)]
 membrane(s,outline(points,3),[8,3],'#64c8dd','#164c78')
 for tip in [[s*20,-12,4],[s*27,2,4],[s*20,10,4],[s*8,12,4]]:curve('body',[s*3,5,3.6],[s*8,-4,4.8],tip,.24,'#2f6388',metal=.28,motion=motion(s))
 for i in range(3):ell('body',[s*(17+i*1.7),-11+i*.7,4],[.5,1.5,.55],'#d8f9fb',seg=8,rings=4,motion=motion(s))
end('glacier-dragon','Gewölbte Drachenmembranen mit geschwungenen Streben, Eiskrallen und glatten Materialflächen.')
for s in [-1,1]:
 for i in range(7):
  x=s*(5+i*2.3);y=-5+i*.8
  p=part('body','#aa93dc',.35,.16,.96,motion(s));verts=[[x-1.1,y-2,4],[x+1.1,y-2,4],[x+1.3,y+5,4],[x,y+9,4],[x-1.3,y+5,4],[x,y+1,2.7],[x,y+1,5.3]]
  for k,v in enumerate(verts):vertex(p,v,rgb(['#5f548f','#c3b4f1','#9488ce','#e8e4ff'][k%4]))
  for k in range(5):quad(p,k,(k+1)%5,5,5);quad(p,(k+1)%5,k,6,6)
end('amethyst-prism','Geschliffene Kristallklingen mit schmalen Facetten und lichtfangenden Spitzen.')
for s in [-1,1]:
 points=outline([(3,3),(12,-5),(25,0),(18,8),(8,10)],5);membrane(s,points,[7,3],'#bba67d','#556b6e')
 for y in [-3,1,5]:curve('body',[s*3,4,3.8],[s*12,y,4.5],[s*23,y+3,4.5],.18,'#c7a973',metal=.8,motion=motion(s))
 ell('body',[s*4,4,4],[1.2,1.6,1.1],'#4a606f',metal=.65,motion=motion(s))
end('brass-glider','Gespanntes Segeltuch in einem geschwungenen Messingrahmen mit gefassten Gelenken.')
for s in [-1,1]:
 p=part('body','#e8ddd6',.04,.58,motion=motion(s));vertices=[[s*3,4,3],[s*20,-9,4],[s*25,5,4.5],[s*13,12,5],[s*13,1,2.8],[s*8,5,4]]
 for i,v in enumerate(vertices):vertex(p,v,rgb(['#f4e9dd','#d7bfc9','#f3e9e0','#ba9dab','#e8d8db','#e5dcd2'][i]))
 for q in [(0,1,4,4),(1,2,4,4),(2,3,4,4),(3,5,4,4),(5,0,4,4)]:quad(p,*q)
end('paper-crane','Bewusst gefaltete Papierflächen: klare Silhouette, dünne Kanten und echte Facetten.')
# Rounded knit animal headwear.
def beanie(base,trim):
 ell('head',[0,-8.5,0],[4.65,1.7,4.55],base);torus('head',[0,-8.1,0],4.1,.55,trim,seg=32)
 for a in range(12):
  t=a*M.tau/12;curve('head',[M.cos(t)*4.3,-8.5,M.sin(t)*4.3],[M.cos(t)*3,-10,M.sin(t)*3],[0,-10.1,0],.045,trim,steps=6,radial=4)
def eyes(j,x,y,z,spread=.95,scale=1):
 for s in [-1,1]:ell(j,[x+s*spread,y,z],[.25*scale,.37*scale,.16*scale],'#222d38',seg=10,rings=6);ell(j,[x+s*spread-.07,y-.1,z-.14],[.065*scale]*3,'#f9f3df',seg=6,rings=4)
for id,base,trim,ear in [('red-panda','#b17759','#e1cbb3','#59423d'),('fox-beanie','#c4814b','#e8d4ba','#72503d'),('axolotl-cap','#cda1c3','#ecd1dc','#b171a5')]:
 beanie(base,trim)
 if id=='axolotl-cap':
  for s in [-1,1]:
   for i in range(3):curve('head',[s*4,-9,0],[s*(6+i*.2),-11+i,0],[s*(6+i*.3),-10+i*1.4,0],.25,ear,steps=6)
 else:
  for s in [-1,1]:
   ell('head',[s*3.1,-10.7,-.2],[1.25,1.7 if id=='fox-beanie' else 1.15,.85],ear,seg=16,rings=8)
   ell('head',[s*3.1,-10.8,-.9],[.7,1 if id=='fox-beanie' else .65,.2],trim,seg=12,rings=6)
 ell('head',[0,-8.8,-4.2],[1.1,.65,.5],trim,seg=16,rings=8);eyes('head',0,-9.5,-4.3,1.7);ell('head',[0,-9,-4.75],[.35,.22,.22],'#4d3b3d',seg=12,rings=6)
 end(id,'Geschnittene weiche Formen, runde Ohren, modellierte Schnauze und feine Stricknähte.')
# Starlit felt hat, progressive curved tip and elliptical brim.
ell('head',[0,-8.5,0],[6.3,.36,6],'#292d4b',seg=32,rings=5)
curve('head',[0,-8.4,0],[0,-15,0],[3,-15.5,0],[3.7-(i/18)*3.5 for i in range(19)],'#414666',steps=18,radial=24)
for x,y in [(-2,-10),(1,-12),(2,-9.5)]:ell('head',[x,y,-3.5],[.28,.5,.12],'#e3c998',seg=8,rings=5)
end('star-witch','Eine weich geknickte Filzspitze mit breiter Krempe und aufgenähten Sternen.')
# helmet: open rounded shell, glass in front, circular ear modules.
for s in [-1,1]:ell('head',[s*4.4,-4,0],[.9,4.6,4.6],'#dde3dc',metal=.25);torus('head',[s*5.1,-4,0],1.6,.35,'#7d9fa9',axis='x',seg=24,metal=.6)
rounded('head',[0,-8.3,0],[9.1,1.3,8.9],.6,'#e0e4df',metal=.18);rounded('head',[0,.2,0],[9.1,1,8.7],.5,'#e0e4df',metal=.18)
ell('head',[0,-4,-3.9],[4.25,3.8,.7],'#8fc5cf',seg=32,rings=16,rough=.13,opacity=.58)
end('orbit-helmet','Abgerundete Schale, gebogenes getöntes Visier und gefasste Kommunikationsmodule.')
for s in [-1,1]:
 curve('head',[s*3,-7,0],[s*5,-12,0],[s*5.8,-15,1],[.55-i*.02 for i in range(13)],'#987958')
 for i in range(2):curve('head',[s*4.5,-10-i*2,0],[s*(7+i),-12-i,0],[s*(7.3+i),-14-i,.5],.22,'#b59a75',steps=8)
 for i in range(3):ell('head',[s*(3.4+i*.7),-8.5-i,1],[1,.22,.6],'#8aab7e',seg=12,rings=5)
end('forest-antlers','Natürlich verzweigtes Geweih mit sanften Übergängen und geformten Blättern.')
torus('head',[0,-8.9,0],4.6,.22,'#68846b',seg=36)
for i in range(6):
 a=i*M.tau/6;c=[M.cos(a)*4.6,-9.1,M.sin(a)*4.6]
 for k in range(5):b=k*M.tau/5;ell('head',add(c,[M.cos(b)*.55,-.2,M.sin(b)*.55]),[.55,.18,.35],'#e5bfd2',seg=10,rings=5)
 ell('head',add(c,[0,-.35,0]),[.25,.2,.25],'#d5bb7c',seg=10,rings=5)
end('lotus-wreath','Sechs geformte Lotusblüten, organische Blätter und ein feiner geflochtener Kranz.')
torus('head',[0,-11.5,0],5.1,.16,'#d9deed',seg=56,metal=.75,rough=.18)
for i in range(4):a=i*M.pi/2;ell('head',[M.cos(a)*5.1,-11.5,M.sin(a)*5.1],[.38,.38,.38],'#b3cef0',seg=12,rings=6,metal=.5)
end('storm-halo','Ein filigraner Metallreif mit vier gefassten Lichtperlen und glatter Rundung.')
curve('head',[-4.7,-4,0],[0,-14,0],[4.7,-4,0],.45,'#3c4660',steps=20,radial=10)
for s in [-1,1]:ell('head',[s*4.6,-4,0],[.9,2.1,1.7],'#313b50',seg=20,rings=12);ell('head',[s*5.25,-4,0],[.28,1.6,1.25],'#c6afd8',seg=20,rings=12,metal=.6,rough=.2)
end('orbit-headphones','Gepolsterte Ohrmuscheln, glatter Kopfbügel und eloxierte Metallabdeckungen.')
# Face.
rounded('head',[0,-4.5,-4.5],[8.8,3,.6],1.2,'#28334a',metal=.45)
rounded('head',[0,-4.5,-4.9],[7.8,2.1,.25],.9,'#ac9cd4',metal=.65,rough=.16)
for s in [-1,1]:curve('head',[s*4.1,-4.5,-4],[s*4.5,-4.5,0],[s*4.2,-4.4,3.5],.16,'#37435d')
end('neon-visor','Gerundetes Panoramavisier mit eingefasster Linse und schlanken Brillenbügeln.')
for s in [-1,1]:
 torus('head',[s*2,-4.5,-4.5],1.55,.18,'#b69c72',axis='z',seg=32,metal=.75,rough=.2)
 ell('head',[s*2,-4.5,-4.6],[1.35,1.35,.13],'#8ab3c3',seg=24,rings=10,metal=.3,rough=.1,opacity=.65)
curve('head',[-.5,-4.5,-4.5],[0,-4.9,-4.5],[.5,-4.5,-4.5],.12,'#b69c72',steps=6,metal=.6)
end('brass-goggles','Runde Messingfassungen, gewölbte Gläser und ein fein geschwungener Nasensteg.')
ell('head',[0,-1.8,-4.3],[3.5,2.3,.6],'#658c73',seg=24,rings=14)
for s in [-1,1]:ell('head',[s*1.3,-2.7,-4.85],[.8,.3,.12],'#283f39',seg=16,rings=6)
curve('head',[0,-3.7,-5],[.4,-1,-5.4],[0,.2,-4.8],.1,'#a6b48c',steps=10)
end('grove-mask','Sanft gewölbte Holzmaske mit eingeschnittenen Augen und einer feinen Blattrippe.')
# Rounded shoulder companions.
for id,color in [('sky-whale','#9ec4d7'),('pocket-fox','#c28a5c'),('night-owl','#a8987e'),('cloud-sprite','#e0e8ed')]:
 if id=='sky-whale':
  ell('body',[-6,-.9,0],[2.4,1.5,3.5],color);ell('body',[-6,-.1,-1],[1.9,.8,2.5],'#e3e9e5')
  for s in [-1,1]:ell('body',[-6+s*2.3,-.2,.2],[1.2,.22,1.2],'#6b9fb5',seg=16,rings=6)
  for s in [-1,1]:ell('body',[-6+s*1.3,-1,4],[1.5,.24,.8],color,seg=16,rings=6)
  eyes('body',-6,-1.2,-3.1,1.2,.6)
 elif id=='pocket-fox':
  ell('body',[-6,-.2,0],[1.6,1.5,1.9],color);ell('body',[-6,-2.1,-.8],[1.7,1.5,1.6],color)
  for s in [-1,1]:ell('body',[-6+s*1.1,-3.6,-.8],[.6,1,.55],'#835b46',seg=16,rings=8)
  ell('body',[-6,-1.5,-2.1],[.9,.6,.8],'#ead6b7');eyes('body',-6,-2.3,-2.2,.8,.7)
  curve('body',[-5,.4,1],[-2,-.3,3],[-5,-1,3.3],[.6+i*.045 for i in range(13)],color)
  ell('body',[-5,-1,3.3],[.8,.8,.8],'#ead9bd')
 elif id=='night-owl':
  ell('body',[-6,-1,0],[1.8,2.4,1.6],color)
  for s in [-1,1]:ell('body',[-6+s*.8,-2.1,-1.3],[.85,1,.35],'#e3d6bd');ell('body',[-6+s*1.7,-.6,0],[.45,1.8,1],'#71675e')
  eyes('body',-6,-2.1,-1.65,.8,.9);ell('body',[-6,-1.2,-1.8],[.25,.5,.4],'#c3a36b')
 else:
  for x,y,r in [(-7,-.6,1.4),(-5.4,-.9,1.7),(-6,-2,1.4)]:ell('body',[x,y,0],[r,r*.8,1.25],color,seg=20,rings=10)
  eyes('body',-6,-1.2,-1.3,.65,.7);ell('body',[-6,.9,0],[.25,.65,.25],'#93c8db')
 end(id,'Ein vollständig geformter kleiner Begleiter mit weichen Konturen, Gesicht und eigenem Charakter.')
# Bags, blade, tail.
rounded('body',[0,6,4],[6.5,8.5,3],1.5,'#607868',rough=.8);rounded('body',[0,8,5.8],[5.4,3.4,1.2],.7,'#b69b76',rough=.8)
for s in [-1,1]:curve('body',[s*2.5,2,3],[s*4,5,-2],[s*2.5,10,3],.24,'#b4a180');rounded('body',[s*3.6,6,4],[1.2,4,2],.5,'#81957a')
end('trail-pack','Weiche Canvasflächen, gerundete Taschen und geschwungene Lederriemen.')
for s in [-1,1]:ell('body',[s*2.2,5,4],[1.35,3.8,1.5],'#7d96a5',metal=.7,rough=.22);torus('body',[s*2.2,8.5,4],.9,.18,'#b8cad0',seg=24,metal=.8);ell('body',[s*2.2,8.7,4],[.6,.5,.6],'#a8e2e2',seg=16,rings=6)
rounded('body',[0,5,3.8],[2.2,6,2],.5,'#46566b',metal=.45)
end('orbit-thrusters','Abgerundete Triebwerksgehäuse, gefasste Düsen und präzise Metallflächen.')
p=part('body','#bdccdd',.8,.17);pts=[[-5,-3,3],[-3,-4,3],[-2,-2,3],[7,13,3],[-4,-2,3],[-3,-2,2.5]]
for i,v in enumerate(pts):vertex(p,v,rgb('#e0e6eb') if i%2 else rgb('#8398b4'))
for q in [(0,1,5,5),(1,2,5,5),(2,3,5,5),(3,4,5,5),(4,0,5,5)]:quad(p,*q)
curve('body',[-6,-1,3],[-3,-3,3],[-1,-5,3],.2,'#c0a37b',metal=.8)
end('moonblade','Eine schmale geschliffene Klinge, definierte Lichtkante und geschwungene Parierstange.')
curve('body',[0,10,3],[2,17,9],[0,13,15],[.8*(1-i/18)+.12 for i in range(19)],'#7d92ba',steps=18,radial=10)
ell('body',[0,13,15],[.45,.8,.45],'#dfdff3',metal=.3)
end('comet-tail','Ein geschwungener, sanft zulaufender Schweif mit feiner heller Spitze.')
# Floating sculptural orbit composition.
for i in range(7):
 a=i*M.tau/7;c=[M.cos(a)*10,5+M.sin(a)*8,M.sin(a)*5]
 ell('body',c,[.7,1.4,.32],'#dbc994',seg=12,rings=6,metal=.6)
 ell('body',c,[1.2,.38,.32],'#f0e1b2',seg=12,rings=6,metal=.6)
end('orbital-stars','Sieben weich geschliffene Sternanhänger in einer ruhigen räumlichen Umlaufbahn.')
for i in range(7):
 a=i*M.pi/3;c=[M.cos(a)*10,-2+i*2.5,M.sin(a)*6]
 for k in range(5):t=k*M.tau/5;ell('body',add(c,[M.cos(t)*.45,0,M.sin(t)*.45]),[.7,.15,.45],'#d9acc8',seg=10,rings=5)
 ell('body',add(c,[0,-.1,0]),[.2,.25,.2],'#d6b980',seg=8,rings=5)
end('petal-spiral','Sieben fein geformte Blüten als offene Spirale, ohne Partikel-Emitter.')
# Tailored overlays and rounded footwear.
rounded('body',[0,4,0],[8.8,6,5],1.1,'#557965',rough=.85)
for j in ['leftArm','rightArm']:rounded(j,[0,1,0],[4.9,4,4.8],.9,'#688774',rough=.8)
curve('body',[-4,5,-2.7],[0,8,-3],[4,5,-2.7],.2,'#b6bd91',steps=14)
end('forest-poncho','Gerundete Schulterstücke, schwer fallender Stoff und ein sauber eingefasster Saum.')
rounded('body',[0,5,0],[8.7,9,4.7],1.1,'#697f9f',metal=.5,rough=.3);rounded('body',[0,4,-2.55],[5,5,.65],.8,'#9ab5ca',metal=.65)
for j in ['leftArm','rightArm']:ell(j,[0,1,0],[2.8,1.8,2.8],'#849bbb',metal=.55,rough=.25)
end('starlight-armor','Geformte Schulterpanzer und ein geschliffenes Brustschild mit weichen Metallreflexen.')
for id,base,sole in [('cloud-boots','#dae4e6','#93adbf'),('brass-walkers','#5f728b','#b29b76')]:
 for j in ['leftLeg','rightLeg']:
  rounded(j,[0,10.7,-.4],[4.6,3.2,5.6],1.1,base,metal=.1 if id=='cloud-boots' else .4)
  rounded(j,[0,12,-.6],[4.8,.8,5.9],.35,sole,rough=.65)
  for k in range(3):curve(j,[-1.3,9.9+k*.5,-3.2],[0,9.8+k*.5,-3.5],[1.3,9.9+k*.5,-3.2],.07,'#e8e5d8',steps=6,radial=5)
 end(id,'Gerundete Zehenkappen, modellierte Sohlen und feine geformte Schnürleisten.')
# Actual cape textures from v1 remain the authoritative source; only the display geometry changes.
from PIL import Image
for id in ['cloud-tapestry','aurora-weave','sakura-scroll']:
 img=Image.open(WEB/'capes'/f'{id}.png');p=part('body','#ffffff',0,.85)
 for y in range(17):
  for x in range(11):
   col=img.getpixel((min(10,x+1),min(16,y+1)));vertex(p,[x-5,y,3+.6*M.sin(y/16*M.pi)+.15*M.sin(x*.6)],list(n/255 for n in col[:3]))
 for y in range(16):
  for x in range(10):a=y*11+x;quad(p,a,a+1,a+12,a+11)
 end(id,'Ein originales Votex-Stoffmotiv auf einer weich gewölbten Cape-Fläche.')
# Render exact model polygons as vector product views (no substitute illustration).
def thumb(model):
 faces=[];allpoints=[]
 offsets={'head':[0,0,0],'body':[0,0,0],'leftArm':[5,2,0],'rightArm':[-5,2,0],'leftLeg':[2,12,0],'rightLeg':[-2,12,0]}
 def project(v):return [v[0]*.91+v[2]*.42,v[1]*.96-v[2]*.27+v[0]*.1]
 light=norm([-.5,-.8,-1])
 for p in model['parts']:
  pos=p['positions'];colors=p['colors'];offset=offsets[p['joint']]
  for i in range(0,len(p['quads']),4):
   ids=p['quads'][i:i+4];vv=[add(pos[k*3:k*3+3],offset) for k in ids];nn=norm(cross(sub(vv[1],vv[0]),sub(vv[2],vv[0])));brightness=.55+.45*abs(sum(nn[k]*light[k] for k in range(3)))
   col=[sum(colors[q*3+k] for q in ids)/4 for k in range(3)];color='#'+''.join(f'{min(255,max(0,int((c**.85)*brightness*255))):02x}' for c in col)
   points=[project(v) for v in vv];allpoints+=points;depth=sum(v[2]-v[0]*.1 for v in vv)/4
   faces.append((depth,'<path d="M'+' L'.join(f'{x:.2f},{y:.2f}' for x,y in points)+' Z" fill="'+color+'"/>'))
 minx=min(v[0] for v in allpoints)-2;miny=min(v[1] for v in allpoints)-2;w=max(v[0] for v in allpoints)-minx+2;h=max(v[1] for v in allpoints)-miny+2
 return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{minx} {miny} {w} {h}"><g stroke-linejoin="round">'+''.join(v for _,v in sorted(faces,key=lambda f:f[0],reverse=True))+'</g></svg>'
priority=['cloud-feathers','glacier-dragon','luna-moth','solar-phoenix','amethyst-prism','brass-glider','midnight-raven','paper-crane']
CURRENT.sort(key=lambda m:priority.index(m['id']) if m['id'] in priority else 8)
manifest=[]
for m in CURRENT:
 for dst in [WEB,CORE]:(dst/f'{m["id"]}.json').write_text(json.dumps(m,separators=(',',':')),encoding='utf8')
 (WEB/f'{m["id"]}.svg').write_text(thumb(m),encoding='utf8')
 manifest.append({k:v for k,v in m.items() if k!='parts'})
assert len(manifest)==37
for dst in [WEB,CORE]:(dst/'index.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf8')
(R/'apps/launcher/src/data/cosmetic-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf8')
print('Atelier v2:',len(CURRENT),'surface models;',sum(sum(len(p['quads'])//4 for p in m['parts']) for m in CURRENT),'quads; max/model',max(sum(len(p['quads'])//4 for p in m['parts']) for m in CURRENT))
