package dev.votex.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Orbit a fresh render state: preview interaction never turns the world player or camera. */
final class AvatarPreview {
 float animationAge=Float.NaN;
 private int x1,y1,x2,y2;private boolean dragging;private float yaw,pitch=5,zoom=1;
 private int boundsRevision=Integer.MIN_VALUE;private boolean boundsVisible;private float minY=-8.3f,maxY=24.3f,radiusX=8.3f,radiusZ=4.3f;
 void render(GuiGraphicsExtractor g,int left,int top,int right,int bottom){
  x1=left;y1=top;x2=right;y2=bottom;var mc=Minecraft.getInstance();if(mc.player==null||right<=left||bottom<=top)return;
  var state=mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player,1);
  if(state instanceof AvatarRenderState avatar&&Float.isFinite(animationAge)&&animationAge>=0)avatar.ageInTicks=animationAge;
  state.shadowPieces.clear();state.outlineColor=0;state.nameTag=null;
  if(state instanceof AvatarRenderState avatar){avatar.bodyRot=180+yaw;avatar.yRot=0;avatar.xRot=0;avatar.boundingBoxWidth/=avatar.scale;avatar.boundingBoxHeight/=avatar.scale;avatar.scale=1;}
  var tilt=new Quaternionf().rotateX((float)Math.toRadians(pitch));var pose=new Quaternionf().rotateZ((float)Math.PI).mul(tilt);
  updateBounds();float angle=(float)Math.toRadians(yaw),lean=(float)Math.toRadians(pitch),unit=.9375f/16;
  float halfDepth=radiusZ*Math.abs((float)Math.cos(angle))+radiusX*Math.abs((float)Math.sin(angle));
  float width=2*unit*(radiusX*Math.abs((float)Math.cos(angle))+radiusZ*Math.abs((float)Math.sin(angle)));
  float height=unit*((maxY-minY)*Math.abs((float)Math.cos(lean))+2*halfDepth*Math.abs((float)Math.sin(lean)));
  float fit=Math.min((bottom-top)*.42f,Math.min((right-left)*.86f/Math.max(.1f,width),(bottom-top)*.86f/Math.max(.1f,height)));
  // Offset is the complete outfit's native world-space centre, including tall headpieces.
  float centerY=.9375f*1.501f-(minY+maxY)*unit/2;
  g.entity(state,Math.max(1,fit*zoom),new Vector3f(0,centerY,0),pose,tilt,left,top,right,bottom);
 }
 private void updateBounds(){
  if(boundsRevision==LocalLooks.revision&&boundsVisible==LocalLooks.visible)return;boundsRevision=LocalLooks.revision;boundsVisible=LocalLooks.visible;minY=-8.3f;maxY=24.3f;radiusX=8.3f;radiusZ=4.3f;
  if(!LocalLooks.visible||!LocalLooks.look.has("equipment"))return;float scale=LocalLooks.scale();
  for(var entry:LocalLooks.look.getAsJsonObject("equipment").entrySet())try{
   if(entry.getKey().equals("cape"))continue;var model=WearableLayer.model(entry.getValue().getAsString());if(model==null)continue;
   for(var element:model.getAsJsonArray("parts")){var part=element.getAsJsonObject();String joint=part.get("joint").getAsString();float px=switch(joint){case "leftArm"->5;case "rightArm"->-5;case "leftLeg"->1.9f;case "rightLeg"->-1.9f;default->0;},py=switch(joint){case "leftArm","rightArm"->2;case "leftLeg","rightLeg"->12;default->0;};var positions=part.getAsJsonArray("positions");
    for(int i=0;i+2<positions.size();i+=3){float x=px+positions.get(i).getAsFloat()*scale,y=py+positions.get(i+1).getAsFloat()*scale,z=positions.get(i+2).getAsFloat()*scale;if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)||Math.max(Math.abs(x),Math.max(Math.abs(y),Math.abs(z)))>160)continue;radiusX=Math.max(radiusX,Math.abs(x)+.5f);radiusZ=Math.max(radiusZ,Math.abs(z)+.5f);minY=Math.min(minY,y-.5f);maxY=Math.max(maxY,y+.5f);}
   }
  }catch(RuntimeException invalid){/* Keep the bounded base-player frame for malformed optional models. */}
 }
 boolean contains(double x,double y){return x>=x1&&x<x2&&y>=y1&&y<y2;}
 boolean click(MouseButtonEvent event){if(event.button()==0&&contains(event.x(),event.y())){dragging=true;return true;}return false;}
 boolean drag(double dx,double dy){if(!dragging)return false;yaw=(yaw+(float)dx*.7f)%360;pitch=Math.clamp(pitch+(float)dy*.35f,-35,35);return true;}
 boolean release(){boolean was=dragging;dragging=false;return was;}
 boolean scroll(double x,double y,double vertical){if(!contains(x,y))return false;zoom=Math.clamp(zoom*(float)Math.pow(1.08,vertical),.65f,1.5f);return true;}
}
