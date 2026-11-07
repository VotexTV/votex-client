#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;
out vec2 localPos;
out vec2 sceneUV;
flat out vec2 bodySize;
flat out vec2 pointer;
flat out vec4 tint;
flat out ivec2 meta;
flat out vec3 optics;
void main(){
 gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);
 localPos=UV0;bodySize=vec2(UV1);pointer=vec2(.5);tint=Color;meta=UV2;optics=Normal;
 sceneUV=gl_Position.xy/gl_Position.w*.5+.5;
}
