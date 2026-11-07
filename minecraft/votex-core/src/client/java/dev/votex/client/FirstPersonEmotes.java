package dev.votex.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.PlayerModelPart;

/** Actual skin arms use the same authored pose as third person, without changing the camera. */
public final class FirstPersonEmotes {
 private FirstPersonEmotes(){}
 private static long frames;
 private static PlayerModel classic,slim;
 private static final org.joml.Vector3f HAND_POSITION=new org.joml.Vector3f();
 private static final org.joml.Matrix4f PARENT_FRAME=new org.joml.Matrix4f(),LOCAL_FRAME=new org.joml.Matrix4f();
 private static PlayerModel model(boolean narrow){
  if(narrow){if(slim==null)slim=new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,true),64,64).bakeRoot(),true);return slim;}
  if(classic==null)classic=new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,false),64,64).bakeRoot(),false);return classic;
 }
 public static long submittedFrames(){return frames;}
 static void placeArm(PoseStack stack,ModelPart arm,boolean right){
  placeArm(stack,arm,right,Minecraft.getInstance().options.fov().get());
 }
 static float cameraScale(float fov){return 1;}
 static void placeArm(PoseStack stack,ModelPart arm,boolean right,float fov){placeArm(stack,arm,right,fov,null);}
 static void placeArm(PoseStack stack,ModelPart arm,boolean right,float fov,String gesture){
  var origin=arm.getInitialPose();
  // Minecraft supplies the hand projection independently from the world FOV.
  // Keep full-size skin geometry, with shoulders below the camera's lower edge.
  stack.translate(right?.30f:-.30f,-.35f,-.40f);
  stack.scale(-1,-1,1);
  stack.translate(-origin.x()/16,-origin.y()/16,-origin.z()/16);
  float pitch=arm.xRot,roll=arm.zRot;
  try {
   arm.xRot-=.45f;
   if(right&&"wave".equals(gesture)){arm.xRot=-2.5f;arm.zRot=-.30f+(roll+2.35f);}
   if("clap".equals(gesture))arm.xRot-=.35f;
   arm.translateAndRotate(stack);
  } finally {arm.xRot=pitch;arm.zRot=roll;}
 }
 public static boolean submit(PoseStack stack,SubmitNodeCollector collector,int light){
  var mc=Minecraft.getInstance();var player=mc.player;
  if(player==null||player.isSpectator()||player.isInvisible()||!mc.options.getCameraType().isFirstPerson())return false;
  double[] pose=EmotePlayer.firstPersonPose();if(pose==null)return false;
  var model=model(player.getSkin().model()==net.minecraft.world.entity.player.PlayerModelType.SLIM);
  ModelPart[] parts={model.head,model.body,model.leftArm,model.rightArm,model.leftLeg,model.rightLeg};
  {
   for(var part:parts)part.resetPose();
   EmotePlayer.applyPose(model,pose,1);
   var state=new AvatarRenderState();state.id=player.getId();state.skin=player.getSkin();
   EmotePlayer.orientHandshake(player,state);var contact=((EmotePreviewState)state).votex$contactPose();
   if(contact!=null){for(var part:parts)part.resetPose();EmotePlayer.applyContact(model,contact);}
   var texture=LocalLooks.skin(state);if(texture==null)texture=player.getSkin().body().texturePath();
   model.rightArm.visible=model.leftArm.visible=true;
   model.rightSleeve.visible=player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);model.leftSleeve.visible=player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
   String gesture=EmotePlayer.localEmoteId();
   for(int side=0;side<2;side++){
    var arm=side==0?model.rightArm:model.leftArm;
    stack.pushPose();
    try{placeArm(stack,arm,side==0,mc.options.fov().get(),gesture);
     // SubmitNodeCollector retains the part. Freeze its pose into the submitted matrix,
     // then keep our private mesh neutral until the deferred draw has consumed it.
     arm.loadPose(PartPose.ZERO);collector.submitModelPart(arm,stack,RenderTypes.entityTranslucent(texture),light,OverlayTexture.NO_OVERLAY,null);}
    finally{stack.popPose();}
   }
   frames++;return true;
  }
 }
}
