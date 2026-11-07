package dev.votex.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.votex.motion.EmoteTimeline;
import java.util.*;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import org.joml.Vector3f;

/** Directional checks on the same baked model/matrix used by actual first-person submissions. */
final class FirstPersonCameraGameChecks {
 private FirstPersonCameraGameChecks(){}
 public static void main(String[] args){System.out.println(dev.votex.config.AtomicJson.GSON.toJson(run()));}
 static Map<String,Object> run(){
  List<Map<String,Object>> cases=new ArrayList<>();
  for(boolean slim:new boolean[]{false,true}){
   var model=new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot(),slim);
   var neutral=palm(model,new double[19],true,70);var leftNeutral=palm(model,new double[19],false,70);
   var wave=palm(model,EmoteTimeline.raw("wave",1.2),true,70,"wave");
   var point=palm(model,EmoteTimeline.raw("point",1.2),true,70);
   var clapRight=palm(model,EmoteTimeline.raw("clap",Math.PI/5.5),true,70,"clap");var clapLeft=palm(model,EmoteTimeline.raw("clap",Math.PI/5.5),false,70,"clap");
   if(wave.x()<neutral.x()+.25f||wave.y()<neutral.y()+.25f)throw new AssertionError("First-person right wave moved inward/down");
   if(point.y()<neutral.y()+.4f)throw new AssertionError("First-person pointing arm did not rise into the view");
   if(clapRight.distance(clapLeft)>.2f||clapRight.distance(clapLeft)>=neutral.distance(leftNeutral))throw new AssertionError("First-person clapping hands separated instead of meeting; right="+xyz(clapRight)+" left="+xyz(clapLeft)+" neutralGap="+neutral.distance(leftNeutral));
   for(var pointInCamera:List.of(neutral,leftNeutral,wave,point,clapRight,clapLeft))if(pointInCamera.z()>=-.05f)throw new AssertionError("A first-person palm crossed behind the near camera plane");
   for(String gesture:new String[]{"wave","sit","dance","point","clap","salute","celebrate","bow","handshake"})for(boolean side:new boolean[]{false,true}){
    double[] pose=EmoteTimeline.raw(gesture,1.2);Vector3f reference=palm(model,pose,side,70,gesture);
    for(float fov:new float[]{30,45,70,90,110}){
     Vector3f actual=palm(model,pose,side,fov,gesture);
     if(Math.abs(actual.x()/actual.z()-reference.x()/reference.z())>.0001||Math.abs(actual.y()/actual.z()-reference.y()/reference.z())>.0001)throw new AssertionError("First-person "+gesture+" framing changed with FOV "+fov);
     if(actual.z()>=-.05f)throw new AssertionError("First-person "+gesture+" palm crossed the camera plane at FOV "+fov);
    }
   }
   cases.add(Map.of("model",slim?"slim":"classic","neutralRight",xyz(neutral),"waveRight",xyz(wave),"pointRight",xyz(point),"clapRight",xyz(clapRight),"clapLeft",xyz(clapLeft),"clapPalmGap",clapRight.distance(clapLeft),"gesturesChecked",List.of("wave","sit","dance","point","clap","salute","celebrate","bow","handshake"),"fovValuesChecked",List.of(30,45,70,90,110)));
  }
  return Map.of("rightWaveOutwardAndUp",true,"pointForwardAndUp",true,"clapHandsMeet",true,"palmsInFrontOfCamera",true,"stableProjectionForEveryGestureAndSide",true,"nativeBakedModels",cases);
 }
 private static Vector3f palm(PlayerModel model,double[] pose,boolean right,float fov){
  return palm(model,pose,right,fov,null);
 }
 private static Vector3f palm(PlayerModel model,double[] pose,boolean right,float fov,String gesture){
  for(var part:model.root().getAllParts())part.resetPose();EmotePlayer.applyPose(model,pose,1);
  var stack=new PoseStack();FirstPersonEmotes.placeArm(stack,right?model.rightArm:model.leftArm,right,fov,gesture);
  return stack.last().pose().transformPosition(new Vector3f(0,10f/16,0));
 }
 private static List<Float> xyz(Vector3f point){return List.of(point.x(),point.y(),point.z());}
}
