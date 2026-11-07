"""Project the actual imported mesh, including its real UV texture, into product SVGs."""
import base64, json, math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
WEB=ROOT/'apps/launcher/public/assets/catalog'
def project(v):return [v[0]*.91+v[2]*.42,v[1]*.96-v[2]*.27+v[0]*.1]
def affine(uv,xy):
 u,v=uv[0];du1,dv1=uv[1][0]-u,uv[1][1]-v;du2,dv2=uv[2][0]-u,uv[2][1]-v;det=du1*dv2-du2*dv1
 if abs(det)<1e-10:return None
 x,y=xy[0];dx1,dy1=xy[1][0]-x,xy[1][1]-y;dx2,dy2=xy[2][0]-x,xy[2][1]-y
 a=(dx1*dv2-dx2*dv1)/det;c=(dx2*du1-dx1*du2)/det;b=(dy1*dv2-dy2*dv1)/det;d=(dy2*du1-dy1*du2)/det
 return [a,b,c,d,x-a*u-c*v,y-b*u-d*v]
for entry in json.loads((WEB/'index.json').read_text()):
 if not entry.get('creator'):continue
 m=json.loads((WEB/(entry['id']+'.json')).read_text());faces=[];points=[];defs=[];textures={}
 for part in m['parts']:
  tex=part.get('texture')
  if tex and tex not in textures:
   name=f't{len(textures)}';textures[tex]=name
   data=base64.b64encode((WEB/'textures'/tex).read_bytes()).decode()
   defs.append(f'<image id="{name}" width="1" height="1" preserveAspectRatio="none" href="data:image/png;base64,{data}"/>')
  for i in range(0,len(part['quads']),4):
   ids=part['quads'][i:i+3];vv=[part['positions'][k*3:k*3+3] for k in ids];xy=[project(v) for v in vv];points+=xy
   path='M'+' L'.join(f'{x:.4f},{y:.4f}' for x,y in xy)+' Z';color=[sum(part['colors'][q*3+k] for q in ids)/3 for k in range(3)]
   # Consistent soft directional light; silhouettes and details are the imported source geometry.
   n=[sum(part['normals'][q*3+k] for q in ids)/3 for k in range(3)];norm=math.sqrt(sum(c*c for c in n)) or 1
   light=.58+.42*abs(sum(n[k]*[-.37,-.68,-.63][k] for k in range(3))/norm)
   fill='#'+''.join(f'{max(0,min(255,round(c*light*255))):02x}' for c in color)
   shape=f'<path d="{path}" fill="{fill}"/>'
   if tex:
    uv=[part['uvs'][k*2:k*2+2] for k in ids];matrix=affine(uv,xy)
    if matrix:
     name=f'f{len(faces)}';defs.append(f'<clipPath id="{name}"><path d="{path}"/></clipPath>')
     shape=f'<g clip-path="url(#{name})"><use href="#{textures[tex]}" transform="matrix({" ".join(f"{v:.7f}" for v in matrix)})"/><path d="{path}" fill="black" opacity="{1-light:.3f}"/></g>'
   depth=sum(v[2]-.1*v[0] for v in vv)/3;faces.append((depth,shape))
 minx=min(p[0] for p in points)-1;miny=min(p[1] for p in points)-1;w=max(p[0] for p in points)-minx+1;h=max(p[1] for p in points)-miny+1
 svg=f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{minx} {miny} {w} {h}"><defs>{"".join(defs)}</defs>'+''.join(shape for _,shape in sorted(faces,key=lambda x:x[0],reverse=True))+'</svg>'
 (WEB/(entry['id']+'.svg')).write_text(svg)
 print(entry['id'],len(faces),'textured mesh triangles')
