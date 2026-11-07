#version 330
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
in vec2 localPos;
in vec2 sceneUV;
flat in vec2 bodySize;
flat in vec2 pointer;
flat in vec4 tint;
flat in ivec2 meta;
flat in vec3 optics;
out vec4 fragColor;
float roundedBox(vec2 p,vec2 halfSize,float r){vec2 q=abs(p)-halfSize+r;return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;}
void main(){
 int shape=meta.x&65535,materialBits=meta.y&65535;
 float radius=min(float(shape&4095)/16.0,min(bodySize.x,bodySize.y)*.5);
 int mode=(shape>>12)&3;bool reduceTransparency=(shape&16384)!=0;
 float intensity=float(materialBits&255)/255.0,blur=float((materialBits>>8)&255)/255.0;
 vec2 p=localPos-bodySize*.5;
 float d=roundedBox(p,bodySize*.5,radius),aa=max(fwidth(d),.55);
 float coverage=1.0-smoothstep(-aa,aa,d);if(coverage<.001)discard;
 // Finite SDF gradient gives the convex boundary normal, even for long capsules.
 vec2 normal=normalize(vec2(roundedBox(p+vec2(.5,0),bodySize*.5,radius)-roundedBox(p-vec2(.5,0),bodySize*.5,radius),roundedBox(p+vec2(0,.5),bodySize*.5,radius)-roundedBox(p-vec2(0,.5),bodySize*.5,radius))+vec2(.0001));
 float edge=exp(-max(0.0,-d)/(2.5+optics.z*10.0));
 vec2 texel=1.0/vec2(textureSize(Sampler0,0));
 vec2 offset=(normal*edge*(3.0+optics.z*15.0)+p/max(bodySize,vec2(1))*.7)*optics.x*intensity*texel;
 vec2 uv=clamp(sceneUV+offset,texel,vec2(1)-texel);
 vec3 sharp=texture(Sampler0,uv).rgb,soft=texture(Sampler1,uv).rgb;
 vec3 scene=mix(sharp,soft,smoothstep(0.,.65,blur)*(.82+.18*optics.z)*intensity);
 float luminance=dot(scene,vec3(.2126,.7152,.0722));
 vec3 base=mode==1?mix(vec3(.88,.94,.97),tint.rgb,.12):mix(vec3(.025,.052,.075),tint.rgb,.055);
 float density=reduceTransparency?.97:tint.a*intensity;
 // Adaptive local backing bounds the white-ink contrast on snow and bright shaders.
 if(mode==2)density=max(density,clamp((luminance-.15)/max(.01,luminance-.035),0.,.89));
 if(mode==1)density=max(density,.72);
 vec3 color=mix(scene,base,density);
 float fresnel=pow(edge,2.0)*(.08+.15*optics.z);
 float highlight=pow(max(0.,dot(normal,normalize(vec2(-.6,-1.)+(pointer-.5)*1.2))),7.0)*edge;
 color+=vec3(.73,.87,1.)*(highlight*.22+fresnel*.45)*optics.y*intensity;
 float rim=1.0-smoothstep(.35,1.6,-d);
 color=mix(color,mix(vec3(.8,.92,1.),tint.rgb,.18),rim*.21*intensity);
 // Background is already included in color; blend only antialiased coverage.
 fragColor=vec4(color,coverage);
}
