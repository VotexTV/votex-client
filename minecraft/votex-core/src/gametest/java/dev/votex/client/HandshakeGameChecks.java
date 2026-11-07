package dev.votex.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.votex.motion.HandshakeRig;
import java.lang.reflect.Proxy;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.player.PlayerModelType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Uses the actual native AvatarRenderer root submission and baked right-arm matrices. */
final class HandshakeGameChecks {
 private HandshakeGameChecks(){}
 static Map<String,Object> sweep(Minecraft mc){
  var renderer=(AvatarRenderer)(Object)mc.getEntityRenderDispatcher().getRenderer(mc.player);
  PlayerModel[] models=new PlayerModel[2];for(int i=0;i<2;i++)models[i]=new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,i==1),64,64).bakeRoot(),i==1);
  double maximum=0;int pairs=0;
  for(boolean slimA:new boolean[]{false,true})for(boolean slimB:new boolean[]{false,true})for(double distance:new double[]{.6,1,1.3,1.8})for(double heading:new double[]{-179,-90,0,45,179})for(double dy:new double[]{-.1,0,.1}){
   if(Math.hypot(distance,dy)>1.8)continue;var plan=HandshakeRig.plan(distance,dy,1,.95,slimA,slimB);if(plan==null)throw new AssertionError("Native stance unexpectedly unreachable");Vector3f[] palms=new Vector3f[2];
   for(int side=0;side<2;side++){
    boolean first=side==0;AvatarRenderState state=(AvatarRenderState)renderer.createRenderState(mc.player,1);state.id=-999;state.bodyRot=(float)(heading+side*180);state.scale=(float)(first?plan.scaleA():plan.scaleB());state.yRot=0;state.xRot=0;state.nameTag=null;
    double[] p=new double[5];plan.pose(first,1.2,p);var pose=new EmotePlayer.ContactPose(p[0],p[1],p[2],p[3],1);
    palms[side]=capture(renderer,state,models[(first?slimA:slimB)?1:0],pose,first?slimA:slimB);
    if(!first)palms[side].add((float)(-Math.sin(Math.toRadians(heading))*distance),(float)dy,(float)(Math.cos(Math.toRadians(heading))*distance));
   }
   double gap=palms[0].distance(palms[1]);maximum=Math.max(maximum,gap);if(gap>.00003)throw new AssertionError("Native palm contact gap "+gap+" at "+heading+" / "+distance+" / "+dy);pairs++;
  }
  return Map.of("nativeRendererRoot",true,"classicAndSlimBakedArms",true,"fixedBoneLength",true,"pairCases",pairs,"maximumWorldPalmGap",maximum,"entityMovement","none; only fresh render states and baked models used");
 }
 static Map<String,Object> connectedPair(Minecraft mc,UUID peer){
  var a=mc.player;var b=mc.level.getPlayerByUUID(peer);if(b==null)throw new AssertionError("Real handshake peer missing");
  var result=new LinkedHashMap<String,Object>();Vector3f[] palms=new Vector3f[2];int index=0;
  for(var entity:List.of(a,b)){
   var renderer=(AvatarRenderer)(Object)mc.getEntityRenderDispatcher().getRenderer(entity);var state=(AvatarRenderState)renderer.createRenderState(entity,1);
   var contact=((EmotePreviewState)state).votex$contactPose();if(contact==null||contact.amount()<.999)throw new AssertionError("Real contact has not settled");
   palms[index]=capture(renderer,state,null,null,state.skin.model()==PlayerModelType.SLIM).add((float)entity.getX(),(float)entity.getY(),(float)entity.getZ());
   result.put(index==0?"localPalm":"remotePalm",List.of(palms[index].x(),palms[index].y(),palms[index].z()));index++;
  }
  double gap=palms[0].distance(palms[1]);if(gap>.006)throw new AssertionError("Actual network player's native palm contact gap "+gap);result.put("worldPalmGap",gap);result.put("realPlayerEntities",true);result.put("rootTransformFromNativeSubmission",true);return result;
 }
 private static Vector3f capture(AvatarRenderer renderer,AvatarRenderState state,PlayerModel testModel,EmotePlayer.ContactPose forced,boolean slim){
  Vector3f[] point={null};var collector=(SubmitNodeCollector)Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),new Class<?>[]{SubmitNodeCollector.class},(proxy,method,args)->{
   if(method.getName().equals("order"))return proxy;if(method.getName().equals("toString"))return "Votex native palm collector";if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);if(method.getName().equals("equals"))return proxy==args[0];
   if(method.getName().equals("submitModel")&&args[0] instanceof PlayerModel submitted&&args[1]==state&&point[0]==null){
    PlayerModel model=testModel==null?submitted:testModel;model.setupAnim(state);if(forced!=null)EmotePlayer.applyContact(model,forced);
    PoseStack stack=new PoseStack();stack.mulPose(new Matrix4f(((PoseStack)args[2]).last().pose()));model.root().translateAndRotate(stack);model.rightArm.translateAndRotate(stack);
    point[0]=stack.last().pose().transformPosition(new Vector3f((float)HandshakeRig.gripX(slim)/16,9f/16,0));
    for(var child:List.of(model.rightSleeve,model.leftSleeve,model.jacket,model.hat))if(Math.abs(child.x)+Math.abs(child.y)+Math.abs(child.z)+Math.abs(child.xRot)+Math.abs(child.yRot)+Math.abs(child.zRot)>.00001)throw new AssertionError("Contact detached a skin overlay");
   }return null;
  });
  renderer.submit(state,new PoseStack(),collector,new CameraRenderState());if(point[0]==null)throw new AssertionError("Actual AvatarRenderer submitted no player model");return point[0];
 }
}
