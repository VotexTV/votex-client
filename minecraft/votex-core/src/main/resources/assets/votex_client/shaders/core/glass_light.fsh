#version 330
in vec2 localPos;
flat in vec2 bodySize;
flat in vec2 pointer;
flat in vec3 tint;
flat in vec3 effect;
out vec4 fragColor;
float roundedBox(vec2 p,vec2 halfSize,float r){vec2 q=abs(p)-halfSize+r;return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;}
void main(){
 float r=min(effect.z*64.,min(bodySize.x,bodySize.y)*.5);
 float d=roundedBox(localPos-bodySize*.5,bodySize*.5,r);
 float coverage=1.-smoothstep(-max(fwidth(d),.55),max(fwidth(d),.55),d);
 if(coverage<.001)discard;
 // Subpixel cursor location is copied directly from current input; no trailing spring.
 vec2 radius=vec2(min(100.,max(24.,bodySize.x*.32)),min(64.,max(16.,bodySize.y*.72)));
 vec2 delta=(localPos-pointer)/radius;
 float spot=exp(-dot(delta,delta)*2.8)*effect.x;
 float reflection=exp(-localPos.y/max(5.,bodySize.y*.22))*effect.y;
 float strength=spot+reflection;
 vec3 color=mix(vec3(.88,.94,1.),tint,spot/max(.0001,strength)*.45);
 fragColor=vec4(color,clamp(strength,0.,.3)*coverage);
}
