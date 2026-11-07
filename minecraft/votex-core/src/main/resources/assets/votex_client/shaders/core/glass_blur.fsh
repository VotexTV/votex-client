#version 330
uniform sampler2D Sampler0;
in vec2 texCoord;
out vec4 fragColor;
void main(){
#ifdef HORIZONTAL
 vec2 stepSize=vec2(1.25*abs(dFdx(texCoord.x)),0.);
#else
 vec2 stepSize=vec2(0.,1.25*abs(dFdy(texCoord.y)));
#endif
 vec3 c=texture(Sampler0,texCoord).rgb*.227027;
 c+=(texture(Sampler0,texCoord+stepSize*1.384615).rgb+texture(Sampler0,texCoord-stepSize*1.384615).rgb)*.316216;
 c+=(texture(Sampler0,texCoord+stepSize*3.230769).rgb+texture(Sampler0,texCoord-stepSize*3.230769).rgb)*.070270;
 fragColor=vec4(c,1.);
}
