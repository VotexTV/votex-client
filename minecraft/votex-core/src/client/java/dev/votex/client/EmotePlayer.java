package dev.votex.client;

import com.google.gson.JsonArray;
import dev.votex.motion.EmoteRig;
import dev.votex.motion.EmoteTimeline;
import dev.votex.motion.HandshakeRig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;

/** Visual poses only. Multiplayer events are tied to real player UUIDs, never entity IDs from packets. */
public final class EmotePlayer {
 public static final String[] IDS=EmoteTimeline.IDS;
 public static final String[] NAMES={"Winken","Sitzen","Groove","Da entlang","Applaus","Salut","Geschafft!","Verbeugen"};
 private static final Map<UUID,Playback> REMOTE=new HashMap<>();
 private static final Map<UUID,EmoteTimeline.Library> REMOTE_CUSTOM=new HashMap<>();
 private static final double[] SCRATCH=new double[3];
 private static Playback local=new Playback();
 private static Consumer<String> publish=id->{};
 private static Runnable publishStop=()->{};
 private static boolean playerPresent;
 private static double playbackSpeed=1;
 private static boolean looping;
 private static final ThreadLocal<double[]> PREVIEW=new ThreadLocal<>();
 /** Frozen native angles belong to this extraction, including deferred GUI submissions. */
 public record ContactPose(double torso,double pitch,double yaw,double roll,double amount){}
 private static final class Playback {
  final EmoteTimeline timeline;
  long sampled=System.nanoTime(),expires;double phase,speed=1;boolean loop;
  String id;UUID partner;
  HandshakeRig.Plan contact;double contactDistance=Double.NaN,contactDelta,contactScaleA,contactScaleB;
  boolean contactSlimA,contactSlimB;double stopPhase=Double.NaN,stopWeight;
  Playback(){timeline=new EmoteTimeline();}
  Playback(EmoteTimeline.Library library){timeline=new EmoteTimeline(library);}
  void start(String next,double age,double speed,boolean loop,boolean remote){id=next;partner=null;contact=null;contactDistance=Double.NaN;stopPhase=Double.NaN;this.speed=speed;this.loop=loop;phase=0;timeline.request(next);sampled=System.nanoTime();expires=loop?(remote?sampled+32_000_000_000L:Long.MAX_VALUE):sampled+(long)((timeline.clipDuration(next)+2-age*speed)/speed*1e9);advance(age);}
  void advance(double seconds){
   // Catch up once after being culled/hidden; bounded by the validated 10 s maximum clip.
   double remaining=Math.clamp(seconds,0,32)*speed;
   while(remaining>0){double step=Math.min(.025,remaining);timeline.stepInto(step);phase+=step;remaining-=step;if(loop&&id!=null&&phase>=timeline.clipDuration(id)+.85){phase=0;timeline.request(id);}}
  }
  double[] sample(long now){advance((now-sampled)/1e9);sampled=now;return timeline.stepInto(0);}
  void stop(){if(partner!=null&&!Double.isFinite(stopPhase)){stopPhase=phase;stopWeight=HandshakeRig.blend(phase);}timeline.request(null);id=null;loop=false;}
  double contactAmount(){return Double.isFinite(stopPhase)?stopWeight*EmoteTimeline.smooth(1-(phase-stopPhase)/.3):HandshakeRig.blend(phase);}
  HandshakeRig.Plan contact(double distance,double delta,double a,double b,boolean slimA,boolean slimB){
   if(!Double.isFinite(contactDistance)||Math.abs(contactDistance-distance)>.0001||Math.abs(contactDelta-delta)>.0001||contactScaleA!=a||contactScaleB!=b||contactSlimA!=slimA||contactSlimB!=slimB){
    contactDistance=distance;contactDelta=delta;contactScaleA=a;contactScaleB=b;contactSlimA=slimA;contactSlimB=slimB;contact=HandshakeRig.plan(distance,delta,a,b,slimA,slimB);
   }return contact;
  }
 }
 private EmotePlayer(){}
 public static boolean valid(String id){return EmoteTimeline.valid(id)||EmoteTimeline.customValid(id);}
 public static void setCustom(JsonArray values){EmoteTimeline.setCustom(values);}
 /** Native GUI extraction is deferred; the resulting render state owns this frozen pose. */
 public static void withPreview(String id,double seconds,Runnable extractAvatar){double[] previous=PREVIEW.get();PREVIEW.set(EmoteTimeline.preview(id,seconds));try{extractAvatar.run();}finally{if(previous==null)PREVIEW.remove();else PREVIEW.set(previous);}}
 public static void capturePreview(AvatarRenderState state){((EmotePreviewState)state).votex$previewPose(PREVIEW.get());}
 public static JsonArray customDefinitions(){return EmoteTimeline.customDefinitions();}
 public static void setRemoteCustom(UUID player,JsonArray values){if(player==null||values==null||values.size()>8||REMOTE_CUSTOM.size()>=128&&!REMOTE_CUSTOM.containsKey(player))return;REMOTE_CUSTOM.put(player,EmoteTimeline.parseLibrary(values,8));}
 /** Called by the client network layer on the Minecraft thread. */
 public static void setPublisher(Consumer<String> start,Runnable stop){publish=start==null?id->{}:start;publishStop=stop==null?()->{}:stop;}
 public static double speed(){return playbackSpeed;}
 public static String localEmoteId(){return local.id;}
 public static double playbackSpeed(){return speed();}
 public static void setSpeed(double speed){if(Double.isFinite(speed))playbackSpeed=Math.clamp(speed,.5,2);}
 public static boolean loop(){return looping;}
 public static boolean looping(){return loop();}
 public static void setLoop(boolean loop){looping=loop;}
 public static void play(String id){if(valid(id)){local.start(id,0,playbackSpeed,looping,false);publish.accept(id);}}
 public static void stop(){if(local.id!=null){local.stop();publishStop.run();}}
 public static void stopLocal(){local.stop();}
 public static String activeId(){return local.id;}
 /** Sample once per first-person frame so both hands share one clock and fade. */
 public static double[] firstPersonPose(){if(!local.timeline.running())return null;double[] pose=local.sample(System.nanoTime());return local.timeline.settled()?null:pose.clone();}
 public static String remoteActiveId(UUID player){Playback clip=REMOTE.get(player);return clip==null?null:clip.id;}
 public static String label(String id){if("handshake".equals(id))return "Handschlag";for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return NAMES[i];String custom=EmoteTimeline.customName(id);return custom==null?"Unbekannte Geste":custom;}
 public static void playRemote(UUID player,String id,double ageSeconds){
  playRemote(player,id,ageSeconds,1,false);
 }
 public static void playRemote(UUID player,String id,double ageSeconds,double speed,boolean loop){
  if(player==null||!Double.isFinite(ageSeconds)||ageSeconds<0||!Double.isFinite(speed)||speed<.5||speed>2||ageSeconds>32)return;
  var mc=Minecraft.getInstance();if(mc.level==null||mc.level.getPlayerByUUID(player)==null)return;
  if(mc.player!=null&&player.equals(mc.player.getUUID())){if(valid(id)&&(loop||ageSeconds*speed<=EmoteTimeline.duration(id)+1))local.start(id,ageSeconds,speed,loop,false);return;}
  var library=REMOTE_CUSTOM.get(player);if(!EmoteTimeline.valid(id)&&(library==null||!library.contains(id)))return;
  double duration=EmoteTimeline.valid(id)?EmoteTimeline.duration(id):library.duration(id);if(!loop&&ageSeconds*speed>duration+1)return;
  if(REMOTE.size()>=128&&!REMOTE.containsKey(player))return;
  Playback next=library==null?new Playback():new Playback(library);next.start(id,ageSeconds,speed,loop,true);REMOTE.put(player,next);
 }
 public static void stopRemote(UUID player){var mc=Minecraft.getInstance();if(mc.player!=null&&mc.player.getUUID().equals(player))local.stop();else {Playback remote=REMOTE.get(player);if(remote!=null)remote.stop();}}
 public static void removeRemote(UUID player){REMOTE.remove(player);REMOTE_CUSTOM.remove(player);}
 public static void clearRemote(){REMOTE.clear();REMOTE_CUSTOM.clear();}
 public static void playHandshake(UUID peer,double ageSeconds){
  var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||peer==null||mc.level.getPlayerByUUID(peer)==null||!Double.isFinite(ageSeconds)||ageSeconds<0||ageSeconds>4.2)return;
  // Both participants receive the same server-approved clock; no movement or camera is forced.
  local.start("handshake",ageSeconds,1,false,false);local.partner=peer;playRemoteHandshake(peer,mc.player.getUUID(),ageSeconds);
 }
 public static void playRemoteHandshake(UUID player,UUID partner,double age){playRemote(player,"handshake",age);var mc=Minecraft.getInstance();Playback clip=mc.player!=null&&mc.player.getUUID().equals(player)?local:REMOTE.get(player);if(clip!=null)clip.partner=partner;}
 /** Consenting models face each other; entity rotation, hitboxes, input and camera are untouched. */
 public static void orientHandshake(net.minecraft.world.entity.Avatar player,AvatarRenderState state){
  var frozen=(EmotePreviewState)state;frozen.votex$contactPose(null);if(PREVIEW.get()!=null)return;
  var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
  Playback clip=player.getUUID().equals(mc.player.getUUID())?local:REMOTE.get(player.getUUID());if(clip==null||clip.partner==null||!clip.timeline.running())return;
  clip.sample(System.nanoTime());var partner=mc.level.getPlayerByUUID(clip.partner);if(partner==null)return;
  double dx=partner.getX()-player.getX(),dz=partner.getZ()-player.getZ(),distance=Math.hypot(dx,dz),dy=partner.getY()-player.getY();
  boolean first=player.getUUID().compareTo(partner.getUUID())<0,slim=state.skin.model()==net.minecraft.world.entity.player.PlayerModelType.SLIM;
  boolean peerSlim=partner instanceof net.minecraft.client.entity.ClientAvatarEntity avatar&&avatar.getSkin().model()==net.minecraft.world.entity.player.PlayerModelType.SLIM;
  var plan=clip.contact(distance,first?dy:-dy,first?player.getScale():partner.getScale(),first?partner.getScale():player.getScale(),first?slim:peerSlim,first?peerSlim:slim);
  if(plan==null){if(clip==local&&local.id!=null){stop();VotexSocial.feedback("Für den Handschlag etwas näher und auf gleicher Höhe stehen.");}return;}
  double amount=clip.contactAmount();if(amount<=0)return;double[] angles=new double[5];plan.pose(first,clip.phase,angles);
  frozen.votex$contactPose(new ContactPose(angles[0],angles[1],angles[2],angles[3],amount));
  float yaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90);state.bodyRot=net.minecraft.util.Mth.rotLerp((float)amount,state.bodyRot,yaw);state.yRot*=1-amount;state.xRot*=1-amount;
 }
 private static boolean interrupted(Player player){return player==null||!player.isAlive()||player.isPassenger()||player.isFallFlying()||player.isSwimming()||player.isCrouching()||player.getDeltaMovement().horizontalDistanceSqr()>.003||Math.abs(player.getDeltaMovement().y)>.15;}
 public static void tick(){
  var mc=Minecraft.getInstance();var player=mc.player;
  if(player==null||mc.level==null){if(playerPresent){local=new Playback();clearRemote();playerPresent=false;}return;}
  playerPresent=true;if(interrupted(player))stop();
  long now=System.nanoTime();if(local.id!=null&&now>local.expires)stop();
  REMOTE.entrySet().removeIf(entry->{var target=mc.level.getPlayerByUUID(entry.getKey());Playback clip=entry.getValue();if(interrupted(target))clip.stop();return target==null||now>clip.expires;});
 }
 public static void apply(PlayerModel model,AvatarRenderState state){
  double[] preview=((EmotePreviewState)state).votex$previewPose();if(preview!=null){applyPose(model,preview,1);return;}
  var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||state.isSpectator)return;
  Playback clip;if(state.id==mc.player.getId())clip=local;
  else {var entity=mc.level.getEntity(state.id);if(!(entity instanceof Player player))return;clip=REMOTE.get(player.getUUID());}
  if(clip==null||!clip.timeline.running())return;
  // Partner contact is solved once during extraction; never use the old fixed reach pose.
  if(clip.partner!=null){ContactPose contact=((EmotePreviewState)state).votex$contactPose();if(contact!=null)applyContact(model,contact);return;}
  long timer=VotexPerformanceEngine.INSTANCE.timer();double[] pose=clip.sample(System.nanoTime());if(!VotexPerformanceEngine.INSTANCE.config.reusableMotion)pose=pose.clone();
  if(!clip.timeline.settled())applyPose(model,pose,clip.timeline.amount());
  VotexPerformanceEngine.INSTANCE.emote(timer);
 }
 static void applyContact(PlayerModel model,ContactPose contact){
  double amount=contact.amount();double[] pose=new double[19];pose[3]=contact.torso()*amount;pose[0]=-contact.torso()*.35*amount;pose[6]=-.12*amount;
  applyPose(model,pose,amount);
  // These are native Ry*Rx angles from the actual palm solve, already in Minecraft's basis.
  model.rightArm.xRot+=(float)(contact.pitch()*amount);model.rightArm.yRot+=(float)(contact.yaw()*amount);model.rightArm.zRot+=(float)(contact.roll()*amount);
 }
 /** Called at setupAnim TAIL, after vanilla has reset all pivots for this render. */
 static void applyPose(PlayerModel model,double[] pose,double amount){
  float original=(float)(1-Math.clamp(amount,0,1));
  for(int joint=0;joint<6;joint++){
   ModelPart part=switch(joint){case 0->model.head;case 1->model.body;case 2->model.leftArm;case 3->model.rightArm;case 4->model.leftLeg;default->model.rightLeg;};
   EmoteRig.rotation(joint,pose,SCRATCH);
   part.xRot=part.xRot*original+(float)SCRATCH[0];part.yRot=part.yRot*original+(float)SCRATCH[1];part.zRot=part.zRot*original+(float)SCRATCH[2];
   EmoteRig.offset(joint,pose,SCRATCH);part.x+=(float)SCRATCH[0];part.y+=(float)SCRATCH[1];part.z+=(float)SCRATCH[2];
  }
  // 26.2 hat/sleeves/jacket/pants are CHILDREN, already inheriting these transforms.
  // Copying a parent's pivot/rotation to its child doubles both and tears the skin apart.
 }
}
