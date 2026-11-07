package dev.votex.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.votex.motion.EmoteTimeline;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.joml.Vector3f;

/** Native 26.2 baked model checks; invoke on the client thread in the consolidated smoke run. */
public final class EmoteRigGameChecks {
 private EmoteRigGameChecks(){}
 public static void run(){
  AvatarRenderState wave=new AvatarRenderState(),sit=new AvatarRenderState(),plain=new AvatarRenderState();
  EmotePlayer.withPreview("wave",1.2,()->{EmotePlayer.capturePreview(wave);EmotePlayer.withPreview("sit",1.2,()->EmotePlayer.capturePreview(sit));});
  if(((EmotePreviewState)wave).votex$previewPose()[11]==((EmotePreviewState)sit).votex$previewPose()[11])throw new AssertionError("Preview poses shared across extracted avatar states");
  try{EmotePlayer.withPreview("wave",1.2,()->{throw new IllegalStateException("scope test");});}catch(IllegalStateException expected){}
  EmotePlayer.capturePreview(plain);if(((EmotePreviewState)plain).votex$previewPose()!=null)throw new AssertionError("Preview leaked into world render state after exception");
  for(boolean slim:new boolean[]{false,true}){
   PlayerModel model=new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot(),slim);
   if(model.rightArm.getChild("right_sleeve")!=model.rightSleeve||model.leftArm.getChild("left_sleeve")!=model.leftSleeve||model.head.getChild("hat")!=model.hat||model.body.getChild("jacket")!=model.jacket)throw new AssertionError("Unexpected native skin hierarchy");
   for(String id:new String[]{"wave","sit","dance","point","clap","salute","celebrate","bow","handshake"}){
    var pose=EmoteTimeline.raw(id,id.equals("bow")?EmoteTimeline.DURATION/2:1.2);
    float[] first=null;
    for(int repeat=0;repeat<40;repeat++){
     // Exactly the reset which EntityModel.setupAnim performs before the mixin's TAIL.
     for(ModelPart part:model.root().getAllParts())part.resetPose();
     EmotePlayer.applyPose(model,pose,1);
     for(ModelPart child:new ModelPart[]{model.leftSleeve,model.rightSleeve,model.leftPants,model.rightPants,model.jacket,model.hat}){
      if(Math.abs(child.x)+Math.abs(child.y)+Math.abs(child.z)+Math.abs(child.xRot)+Math.abs(child.yRot)+Math.abs(child.zRot)>.00001)throw new AssertionError("Child layer double-transformed: "+id);
     }
     float[] current={model.rightArm.x,model.rightArm.y,model.rightArm.z,model.rightArm.xRot,model.rightArm.yRot,model.rightArm.zRot,model.head.y,model.body.y,model.leftLeg.y};
     if(first==null)first=current;else for(int i=0;i<current.length;i++)if(Math.abs(first[i]-current[i])>.00001)throw new AssertionError("Accumulating model drift: "+id);
     if(id.equals("wave")){
      PoseStack stack=new PoseStack();model.rightArm.translateAndRotate(stack);Vector3f hand=stack.last().pose().transformPosition(new Vector3f(0,10f/16,0));
      if(!(hand.x()<model.rightArm.x/16-0.25&&hand.y()<model.rightArm.y/16-0.25))throw new AssertionError("Native right hand goes inward/down");
      model.rightSleeve.translateAndRotate(stack);Vector3f sleeve=stack.last().pose().transformPosition(new Vector3f(0,10f/16,0));if(hand.distance(sleeve)>.00001)throw new AssertionError("Detached native sleeve");
     }
    }
   }
  }
  System.out.println("VOTEX_NATIVE_EMOTE_RIG_PASS classic+slim,9gestures,40resets,attachedOverlays,isolatedPreviews");
 }
}
