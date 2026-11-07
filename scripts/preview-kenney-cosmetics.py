"""Project exact curated surfaces and fitting reference into review SVGs.

Product previews contain real source geometry and UVs, not generated illustrations.
The gray block player in *-fit.svg provides a common attachment-space reference.
"""
import base64, html, json, math, sys
from PIL import Image
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
STAGE=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else ROOT/'work/catalog-expansion/staged'

def project(v): return [v[0]*.91+v[2]*.42,v[1]*.96-v[2]*.27+v[0]*.1]
def affine(uv,xy):
    u,v=uv[0];du1,dv1=uv[1][0]-u,uv[1][1]-v;du2,dv2=uv[2][0]-u,uv[2][1]-v;det=du1*dv2-du2*dv1
    if abs(det)<1e-10:return None
    x,y=xy[0];dx1,dy1=xy[1][0]-x,xy[1][1]-y;dx2,dy2=xy[2][0]-x,xy[2][1]-y
    a=(dx1*dv2-dx2*dv1)/det;c=(dx2*du1-dx1*du2)/det;b=(dy1*dv2-dy2*dv1)/det;d=(dy2*du1-dy1*du2)/det
    return [a,b,c,d,x-a*u-c*v,y-b*u-d*v]

def player():
    result=[]
    for x0,y0,z0,x1,y1,z1 in [(-4,-8,-4,4,0,4),(-4,0,-2,4,12,2),(-8,1,-2,-4,13,2),(4,1,-2,8,13,2),(-4,12,-2,0,24,2),(0,12,-2,4,24,2)]:
        points=[[x0,y0,z0],[x1,y0,z0],[x1,y1,z0],[x0,y1,z0],[x0,y0,z1],[x1,y0,z1],[x1,y1,z1],[x0,y1,z1]]
        result.append({'positions':sum(points,[]),'normals':[0,0,-1]*8,'colors':[.25,.3,.39]*8,'quads':[0,1,2,3,4,7,6,5,0,4,5,1,3,2,6,7,0,3,7,4,1,5,6,2]})
    return result

def svg(parts):
    faces=[];points=[];defs=[];textures={};pixels={}
    for part in parts:
        tex=part.get('texture')
        if tex and tex not in textures:
            name=f't{len(textures)}';textures[tex]=name
            data=base64.b64encode((STAGE/'textures'/tex).read_bytes()).decode()
            defs.append(f'<image id="{name}" width="1" height="1" preserveAspectRatio="none" href="data:image/png;base64,{data}"/>')
            pixels[tex]=Image.open(STAGE/'textures'/tex).convert('RGB')
        for i in range(0,len(part['quads']),4):
            ids=part['quads'][i:i+4]
            # Source triangles are represented by a repeated final quad vertex.
            if ids[-1]==ids[-2]:ids=ids[:-1]
            vv=[part['positions'][k*3:k*3+3] for k in ids];xy=[project(v) for v in vv];points+=xy
            path='M'+' L'.join(f'{x:.4f},{y:.4f}' for x,y in xy)+' Z'
            color=[sum(part['colors'][q*3+k] for q in ids)/len(ids) for k in range(3)]
            n=[sum(part['normals'][q*3+k] for q in ids)/len(ids) for k in range(3)];norm=math.sqrt(sum(c*c for c in n)) or 1
            light=.62+.38*abs(sum(n[k]*[-.37,-.68,-.63][k] for k in range(3))/norm)
            fill='#'+''.join(f'{max(0,min(255,round(c*light*255))):02x}' for c in color)
            shape=f'<path d="{path}" fill="{fill}"/>'
            if tex:
                uv=[part['uvs'][k*2:k*2+2] for k in ids[:3]];matrix=affine(uv,xy[:3])
                if matrix:
                    name=f'f{len(faces)}';defs.append(f'<clipPath id="{name}"><path d="{path}"/></clipPath>')
                    shape=f'<g clip-path="url(#{name})"><use href="#{textures[tex]}" transform="matrix({" ".join(f"{v:.7f}" for v in matrix)})"/><path d="{path}" fill="black" opacity="{1-light:.3f}"/></g>'
                else:
                    # Kenney palettes intentionally use coincident UVs. A singular affine
                    # map is a flat sampled palette color, not an untextured white face.
                    im=pixels[tex];u=sum(q[0] for q in uv)/3;v=sum(q[1] for q in uv)/3
                    sample=im.getpixel((max(0,min(im.width-1,round(u*im.width))),max(0,min(im.height-1,round(v*im.height)))))
                    fill='#'+''.join(f'{max(0,min(255,round(sample[k]*color[k]*light))):02x}' for k in range(3))
                    shape=f'<path d="{path}" fill="{fill}"/>'
            depth=sum(v[2]-.1*v[0] for v in vv)/len(vv);faces.append((depth,shape))
    minx=min(p[0] for p in points)-1;miny=min(p[1] for p in points)-1;w=max(p[0] for p in points)-minx+1;h=max(p[1] for p in points)-miny+1
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{minx} {miny} {w} {h}"><defs>{"".join(defs)}</defs>'+''.join(shape for _,shape in sorted(faces,key=lambda x:x[0],reverse=True))+'</svg>'

if __name__=='__main__':
    items=json.loads((STAGE/'index.json').read_text(encoding='utf-8'))
    for entry in items:
        model=json.loads((STAGE/(entry['id']+'.json')).read_text(encoding='utf-8'))
        (STAGE/(entry['id']+'.svg')).write_text(svg(model['parts']),encoding='utf-8')
        parts=json.loads(json.dumps(model['parts']))
        pivots={'leftArm':(5,2,0),'rightArm':(-5,2,0),'leftLeg':(1.9,12,0),'rightLeg':(-1.9,12,0)}
        for part in parts:
            pivot=pivots.get(part.get('joint'),(0,0,0))
            part['positions']=[v+pivot[i%3] for i,v in enumerate(part['positions'])]
        fitted=player()+parts
        if entry['slot']=='back':
            fitted=json.loads(json.dumps(fitted))
            for part in fitted:
                for key in ['positions','normals']:
                    for i in range(0,len(part[key]),3):part[key][i]*=-1;part[key][i+2]*=-1
        (STAGE/(entry['id']+'-fit.svg')).write_text(svg(fitted),encoding='utf-8')
    print(f'Projected {len(items)} exact model and fitting previews.')
