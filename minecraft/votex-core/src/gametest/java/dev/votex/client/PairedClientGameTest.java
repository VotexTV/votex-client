package dev.votex.client;

import com.google.gson.*;
import dev.votex.config.AtomicJson;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Two separate native clients against a loopback Dedicated Server. Never included in Core releases. */
public final class PairedClientGameTest {
 private final Map<String,Object> evidence=new LinkedHashMap<>();
 private String role,otherRole,name,otherName;
 private UUID peer;
 private Path coordination,destination;
 private PlayerModel boneModel;
 public void run(ClientGameTestContext context){
  role=System.getenv("VOTEX_PAIRED_ROLE");if(!Set.of("A","B").contains(role))throw new AssertionError("Explicit paired QA role required");
  otherRole=role.equals("A")?"B":"A";name="VotexPeer"+role;otherName="VotexPeer"+otherRole;peer=UUID.nameUUIDFromBytes(("OfflinePlayer:"+otherName).getBytes(StandardCharsets.UTF_8));
  String directory=System.getenv("VOTEX_PAIRED_COORDINATION");if(directory==null)throw new AssertionError("Explicit private coordination directory required");
  coordination=Path.of(directory).toAbsolutePath().normalize();if(!coordination.toString().replace('\\','/').contains("/work/ingame-goal/"))throw new AssertionError("Refusing non-QA coordination directory");
  try{
   Files.createDirectories(coordination);destination=coordination.resolve("evidence-"+role);Files.createDirectories(destination);
   context.getInput().resizeWindow(1280,800);
   context.runOnClient(mc->{String game=mc.gameDirectory.toPath().toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);if(!game.contains("paired-client-"))throw new AssertionError("Refusing to seed a non-paired game directory");if(!mc.getUser().getName().equals(name))throw new AssertionError("Paired username was not applied: "+mc.getUser().getName());mc.options.guiScale().set(2);mc.getWindow().setGuiScale(2);mc.options.renderDistance().set(4);mc.options.simulationDistance().set(4);mc.options.gamma().set(1d);mc.options.pauseOnLostFocus=false;seed();var address="127.0.0.1:25566";ConnectScreen.startConnecting(mc.gui.screen(),mc,ServerAddress.parseString(address),new ServerData("Votex isolated two-client QA",address,ServerData.Type.OTHER),false,null);});
   until(context,mc->mc.player!=null&&mc.level!=null&&mc.player.getGameProfile().name().equals(name),"world connection");
   until(context,mc->VotexSocial.available()&&VotexSocial.appearanceAcknowledged(),"relay hello + appearance ACK");
   until(context,mc->PairedVoiceNoCapture.connected(),"real Voice UDP initialization");context.runOnClient(mc->evidence.put("voiceTransportWithoutCapture",PairedVoiceNoCapture.proof()));
   until(context,mc->Files.isRegularFile(VotexClient.INSTANCE.directory.resolve("last-server.json")),"successful JOIN history written off-thread");context.runOnClient(mc->{JsonObject recent=JsonParser.parseString(Files.readString(VotexClient.INSTANCE.directory.resolve("last-server.json"))).getAsJsonObject();if(!recent.get("address").getAsString().equals("127.0.0.1:25566")||!recent.get("profileId").getAsString().equals("paired-"+role.toLowerCase(Locale.ROOT)))throw new AssertionError("Successful JOIN history differs from actual QA server");evidence.put("successfulJoinHistory",recent);});
   barrier(context,"joined");
   if(role.equals("A"))context.runOnClient(mc->{command(mc,"gamemode creative @a");command(mc,"time set day");command(mc,"weather clear");command(mc,"fill -8 -61 -8 8 -61 8 minecraft:stone");command(mc,"tp VotexPeerA 0.5 -60.0 0.0 0 0");command(mc,"tp VotexPeerB 0 -60 1.3 180 0");});
   until(context,mc->mc.level.players().size()==2&&Math.abs(mc.player.getY()+60)<.1&&Math.abs(mc.player.getZ()-(role.equals("A")?0:1.3))<.1,"two stationary QA players");
   until(context,mc->VotexSocial.peers().stream().anyMatch(p->p.id().equals(peer)&&p.name().equals(otherName)&&p.appearance()),"remote appearance decode");
   context.runOnClient(mc->{AvatarRenderState state=state(mc,peer);var look=LocalLooks.remote(state);if(look==null||look.skin()==null||look.cape()==null||!look.visible())throw new AssertionError("Remote skin/cape/model missing");String expected=role.equals("B")?"custom-a1":"creator-poly-d26f03e65b0a",slot=role.equals("B")?"wings":"head";var equipment=look.look().getAsJsonObject("equipment");if(!equipment.get(slot).getAsString().equals(expected))throw new AssertionError("Wrong remote model");String addedSlot=role.equals("B")?"pet":"face",addedModel=role.equals("B")?"creator-poly-9ea8db73a359":"creator-poly-2cbc13940cb1";if(!equipment.get(addedSlot).getAsString().equals(addedModel)||WearableLayer.model(addedModel)==null)throw new AssertionError("New catalog model did not reach remote renderer");evidence.put("remoteLook",Map.of("uuid",peer.toString(),"actualIdentity",otherName,"selectedModel",expected,"newCatalogModel",addedModel,"skinTexture",look.skin().toString(),"capeTexture",look.cape().toString()));});
   evidence.put("relayStatus",context.computeOnClient(mc->VotexSocial.status()));barrier(context,"looks");shot(context,"two-real-looks",true);
   if(role.equals("B"))context.runOnClient(mc->{LocalLooks.look.getAsJsonObject("equipment").addProperty("head","creator-frog-hat");LocalLooks.revision++;});
   if(role.equals("A"))until(context,mc->{var look=LocalLooks.remote(state(mc,peer));return look!=null&&look.look().getAsJsonObject("equipment").get("head").getAsString().equals("creator-frog-hat");},"remote appearance revision");
   barrier(context,"appearance-reload");evidence.put("remoteAppearanceReload",true);
   if(role.equals("A"))context.runOnClient(mc->{mc.gui.setScreen(null);EmotePlayer.setLoop(true);EmotePlayer.play("wave");});
   until(context,mc->Math.abs(bone(mc,role.equals("A")?mc.player.getUUID():peer)[2])>1,"actual received wave joint");
   barrier(context,"wave-live");shot(context,"synchronized-wave",true);
   if(role.equals("A"))context.runOnClient(mc->{EmotePlayer.stop();EmotePlayer.setLoop(false);});
   until(context,mc->Math.abs(bone(mc,role.equals("A")?mc.player.getUUID():peer)[2]-.1)<.08,"remote wave release to vanilla idle arm",5);
   barrier(context,"wave-stopped");evidence.put("builtInEmoteThroughRealConnection",true);
   if(role.equals("B"))context.runOnClient(mc->{mc.gui.setScreen(null);EmotePlayer.setLoop(true);EmotePlayer.play("custom-emote-b1");});
   until(context,mc->Math.abs(bone(mc,role.equals("B")?mc.player.getUUID():peer)[2])>.5,"received custom emote definition");
   barrier(context,"custom-live");shot(context,"received-custom-emote",true);
   if(role.equals("B"))context.runOnClient(mc->{EmotePlayer.stop();EmotePlayer.setLoop(false);});
   until(context,mc->Math.abs(bone(mc,role.equals("B")?mc.player.getUUID():peer)[2]-.1)<.08,"custom emote release to vanilla idle arm",5);
   barrier(context,"custom-stopped");evidence.put("customEmoteThroughRealConnection",true);
   context.runOnClient(mc->mc.gui.setScreen(null));
   if(role.equals("A"))context.runOnClient(mc->VotexSocial.requestHandshake(peer));
   if(role.equals("B")){until(context,mc->!VotexSocial.pendingInvitation().isEmpty(),"recipient-bound invitation");shot(context,"invitation-near-hotbar",false);}
   barrier(context,"invite-shown");
   if(role.equals("B"))context.getInput().pressKey(GLFW.GLFW_KEY_G);
   until(context,mc->"handshake".equals(EmotePlayer.activeId()),"G consent starts both players");
   until(context,mc->Math.abs(bone(mc,mc.player.getUUID())[0])>.6&&Math.abs(bone(mc,peer)[0])>.6,"both handshake native arm matrices");
   until(context,mc->{var a=((EmotePreviewState)state(mc,mc.player.getUUID())).votex$contactPose();var b=((EmotePreviewState)state(mc,peer)).votex$contactPose();return a!=null&&b!=null&&a.amount()>.999&&b.amount()>.999;},"settled native palm contact");
   context.runOnClient(mc->{evidence.put("handshakeBones",Map.of("local",bone(mc,mc.player.getUUID()),"remote",bone(mc,peer),"worldTick",mc.level.getGameTime()));evidence.put("actualNativeWorldPalmContact",HandshakeGameChecks.connectedPair(mc,peer));});
   barrier(context,"handshake-live");shot(context,"consented-handshake",true);
   if(role.equals("A"))worldContactShot(context);
   if(role.equals("A"))context.runOnClient(mc->EmotePlayer.stop());
   until(context,mc->!"handshake".equals(EmotePlayer.activeId()),"pair stop propagated",1.5);
   barrier(context,"handshake-stopped");evidence.put("recipientGConsentAndMutualStop",true);
   seconds(context,6);
   if(role.equals("A"))context.runOnClient(mc->VotexSocial.requestHandshake(peer));
   if(role.equals("B")){until(context,mc->!VotexSocial.pendingInvitation().isEmpty(),"movement-case invitation");context.getInput().pressKey(GLFW.GLFW_KEY_G);}
   until(context,mc->"handshake".equals(EmotePlayer.activeId()),"movement-case paired start");barrier(context,"movement-handshake-live");
   if(role.equals("B"))context.getInput().holdKeyFor(GLFW.GLFW_KEY_D,8);
   until(context,mc->!"handshake".equals(EmotePlayer.activeId()),"movement cancels both partners",1.5);barrier(context,"movement-handshake-stopped");evidence.put("actualMovementCancelsBothPartners",true);
   if(role.equals("A"))context.runOnClient(mc->command(mc,"tp VotexPeerB 0 -60 1.3 180 0"));
   until(context,mc->Math.abs(mc.player.getZ()-(role.equals("A")?0:1.3))<.1,"movement case reset");seconds(context,6);
   context.runOnClient(mc->mc.gui.setScreen(null));
   if(role.equals("A"))context.runOnClient(mc->VotexSocial.requestHandshake(peer));
   if(role.equals("B"))until(context,mc->!VotexSocial.pendingInvitation().isEmpty(),"distance-case invitation");
   barrier(context,"distance-invited");
   if(role.equals("A"))context.runOnClient(mc->command(mc,"tp VotexPeerB 0 -60 8 180 0"));
   until(context,mc->mc.level.getPlayerByUUID(peer)!=null&&mc.player.distanceToSqr(mc.level.getPlayerByUUID(peer))>30,"recipient moved beyond handshake reach");
   barrier(context,"distance-moved");if(role.equals("B"))context.getInput().pressKey(GLFW.GLFW_KEY_G);seconds(context,1);assertNoHandshake(context);barrier(context,"distance-rejected");evidence.put("distanceRecheckedOnAccept",true);
   if(role.equals("A"))context.runOnClient(mc->command(mc,"tp VotexPeerB 0 -60 1.3 180 0"));
   until(context,mc->mc.level.getPlayerByUUID(peer)!=null&&mc.player.distanceToSqr(mc.level.getPlayerByUUID(peer))<2,"players returned near");seconds(context,6);barrier(context,"expiry-ready");
   if(role.equals("A"))context.runOnClient(mc->VotexSocial.requestHandshake(peer));
   if(role.equals("B"))until(context,mc->!VotexSocial.pendingInvitation().isEmpty(),"expiry-case invitation");barrier(context,"expiry-invited");seconds(context,11);
   if(role.equals("B")){context.runOnClient(mc->{if(!VotexSocial.pendingInvitation().isEmpty())throw new AssertionError("Invitation did not expire");});context.getInput().pressKey(GLFW.GLFW_KEY_G);}seconds(context,.5);assertNoHandshake(context);barrier(context,"expiry-rejected");evidence.put("expiredGDoesNotStartHandshake",true);
   if(role.equals("A"))context.runOnClient(mc->VotexSocial.requestHandshake(peer));
   if(role.equals("B")){until(context,mc->!VotexSocial.pendingInvitation().isEmpty(),"decline-case invitation");context.runOnClient(mc->VotexSocial.declineHandshake());}barrier(context,"declined");seconds(context,.5);assertNoHandshake(context);evidence.put("declineStartsNoAnimation",true);
   checkWallConsent(context);
   if(role.equals("A"))context.runOnClient(mc->command(mc,"tp VotexPeerB 150 -60 0 180 0"));
   until(context,mc->VotexSocial.peers().isEmpty(),"out-of-range peer removal");barrier(context,"out-of-range");
   if(role.equals("A"))context.runOnClient(mc->command(mc,"tp VotexPeerB 0 -60 1.3 180 0"));
   until(context,mc->VotexSocial.peers().stream().anyMatch(p->p.id().equals(peer)&&p.appearance()),"peer reappears after range return");barrier(context,"returned");evidence.put("rangeRemovalAndAppearanceRejoin",true);
   seconds(context,11);
   if(role.equals("A"))context.runOnClient(mc->VotexSocial.requestHandshake(peer));
   if(role.equals("B")){until(context,mc->!VotexSocial.pendingInvitation().isEmpty(),"disconnect-case invitation");context.getInput().pressKey(GLFW.GLFW_KEY_G);}
   until(context,mc->"handshake".equals(EmotePlayer.activeId()),"disconnect-case paired start");barrier(context,"disconnect-handshake-live");
   if(role.equals("B")){disconnect(context);mark("disconnected");}
   else {until(context,mc->VotexSocial.peers().isEmpty()&&!"handshake".equals(EmotePlayer.activeId()),"disconnected peer cleanup and partner stop",2);context.runOnClient(mc->{if(remoteContains(peer))throw new AssertionError("Remote texture state survived disconnect");});evidence.put("disconnectClearsRemoteLooksAndStopsPartner",true);disconnect(context);}
   evidence.put("role",role);evidence.put("name",name);evidence.put("status","passed");evidence.put("limitations",List.of("Loopback QA identities, not authenticated Microsoft accounts","No public two-network reachability or microphone audio proof","New catalog combinations cover selected actual looks, not every combination or multiplayer load"));AtomicJson.write(destination.resolve("paired-proof.json"),evidence);mark("complete");System.out.println("VOTEX_PAIRED_PASS "+role+" realTCP+remoteNewCatalog+wave+customEmote+GConsent+mutualStop+distance+wallInvite+wallAccept+expiry+decline+range+disconnect");
  }catch(Exception|AssertionError failure){try{AtomicJson.write(coordination.resolve(role+"-failed.json"),Map.of("error",failure.toString()));}catch(Exception ignored){}throw new AssertionError("Paired native verification failed for "+role,failure);}
 }
 private void seed()throws Exception{
  PairedVoiceNoCapture.install();
  var core=VotexClient.INSTANCE;core.modules.config().selectedSkin="";core.theme=new dev.votex.config.ThemeConfig(0xff83baff,0xfff6faff,0xffa9bdd6,0xca152236,14,35);core.modules.config().enabled.clear();LocalLooks.visible=LocalLooks.capeVisible=true;
  Path assets=core.directory.resolve("assets");Files.createDirectories(assets);String skinId="paired-"+role.toLowerCase(Locale.ROOT),source=role.equals("A")?"votex":"studio";
  try(var in=getClass().getResourceAsStream("/assets/votex_client_test/skins/"+source+".png")){if(in==null)throw new AssertionError("Missing test skin");Files.copy(in,assets.resolve("skin-"+skinId+".png"),StandardCopyOption.REPLACE_EXISTING);}
  try(var in=getClass().getResourceAsStream("/assets/votex_client/textures/cape.png")){if(in==null)throw new AssertionError("Missing test cape");Files.copy(in,assets.resolve("cape-"+skinId+".png"),StandardCopyOption.REPLACE_EXISTING);}
  JsonObject data=new JsonObject(),look=new JsonObject(),equipment=new JsonObject();data.addProperty("schemaVersion",1);data.addProperty("selectedSkin",skinId);data.addProperty("selectedCosmetic",skinId);data.add("skins",new JsonArray());look.addProperty("id",skinId);look.addProperty("name","Paired QA "+role);look.addProperty("scale",100);look.addProperty("cape",true);look.addProperty("customCape",true);look.addProperty("nametag","Votex Client");equipment.addProperty(role.equals("A")?"wings":"head",role.equals("A")?"custom-a1":"creator-poly-d26f03e65b0a");equipment.addProperty(role.equals("A")?"pet":"face",role.equals("A")?"creator-poly-9ea8db73a359":"creator-poly-2cbc13940cb1");look.add("equipment",equipment);JsonArray looks=new JsonArray();looks.add(look);data.add("cosmetics",looks);
  JsonArray models=new JsonArray();if(role.equals("A")){JsonObject model=WearableLayer.model("creator-bat-wings").deepCopy();model.addProperty("id","custom-a1");models.add(model);}data.add("customModels",models);
  JsonObject clip=new JsonObject();clip.addProperty("id","custom-emote-"+role.toLowerCase(Locale.ROOT)+"1");clip.addProperty("name","Paired custom wave "+role);clip.addProperty("duration",3);JsonArray frames=new JsonArray();for(int frame=0;frame<3;frame++){JsonObject key=new JsonObject();key.addProperty("time",frame*.5);JsonArray pose=new JsonArray();for(int i=0;i<19;i++)pose.add(i==11&&frame==1?-2.1:0);key.add("pose",pose);frames.add(key);}clip.add("keyframes",frames);JsonArray clips=new JsonArray();clips.add(clip);data.add("customEmotes",clips);String[] wheel=EmotePlayer.IDS.clone();wheel[0]=clip.get("id").getAsString();data.add("emoteWheel",AtomicJson.GSON.toJsonTree(wheel));AtomicJson.write(core.directory.resolve("presets.json"),data);LocalLooks.reload(core.directory);if(LocalLooks.assetSkinId().isEmpty()||LocalLooks.assetCapeId().isEmpty())throw new AssertionError("Own skin/cape were not seeded");
 }
 private void checkWallConsent(ClientGameTestContext context)throws Exception{
  seconds(context,6);barrier(context,"wall-ready");
  if(role.equals("A"))context.runOnClient(mc->{command(mc,"tp VotexPeerA 0.5 -60 -0.35 0 0");command(mc,"tp VotexPeerB 0.5 -60 1.35 180 0");command(mc,"fill 0 -60 0 0 -58 0 minecraft:stone");});
  until(context,mc->{var other=mc.level.getPlayerByUUID(peer);return other!=null&&mc.player.distanceToSqr(other)<3.24&&!mc.player.hasLineOfSight(other);},"opaque wall obstructs two nearby real players");barrier(context,"wall-present");
  if(role.equals("A")){context.runOnClient(mc->VotexSocial.requestHandshake(peer));until(context,mc->noticeValue().contains("nah beieinander"),"server rejected invite through opaque wall");}
  barrier(context,"wall-invite-rejected");context.runOnClient(mc->{if(!VotexSocial.pendingInvitation().isEmpty())throw new AssertionError("Wall-blocked invite was delivered");});assertNoHandshake(context);evidence.put("opaqueWallRejectsInviteWithinReach",true);
  if(role.equals("A"))context.runOnClient(mc->command(mc,"fill 0 -60 0 0 -58 0 minecraft:air"));
  until(context,mc->mc.player.hasLineOfSight(mc.level.getPlayerByUUID(peer)),"wall removed before valid invitation");seconds(context,6);barrier(context,"wall-accept-ready");
  if(role.equals("A"))context.runOnClient(mc->VotexSocial.requestHandshake(peer));
  if(role.equals("B"))until(context,mc->!VotexSocial.pendingInvitation().isEmpty(),"clear line-of-sight invitation delivered");barrier(context,"wall-valid-invitation");
  if(role.equals("A"))context.runOnClient(mc->command(mc,"fill 0 -60 0 0 -58 0 minecraft:stone"));
  until(context,mc->!mc.player.hasLineOfSight(mc.level.getPlayerByUUID(peer)),"wall introduced after valid invitation");barrier(context,"wall-before-accept");
  if(role.equals("B")){context.getInput().pressKey(GLFW.GLFW_KEY_G);until(context,mc->noticeValue().contains("Einladung ist nicht mehr gültig"),"server rechecked wall on G acceptance");}
  barrier(context,"wall-accept-rejected");assertNoHandshake(context);evidence.put("opaqueWallRecheckedAfterInviteOnG",true);
  if(role.equals("A"))context.runOnClient(mc->command(mc,"fill 0 -60 0 0 -58 0 minecraft:air"));
  // Teleports arrive before end-of-tick block updates. Await both client collision views
  // before moving either player back into the just-removed wall column.
  until(context,mc->mc.level.getBlockState(new net.minecraft.core.BlockPos(0,-60,0)).isAir()&&mc.level.getBlockState(new net.minecraft.core.BlockPos(0,-59,0)).isAir()&&mc.level.getBlockState(new net.minecraft.core.BlockPos(0,-58,0)).isAir()&&mc.player.hasLineOfSight(mc.level.getPlayerByUUID(peer)),"both client views received removed wall");barrier(context,"wall-removal-received");
  if(role.equals("A"))context.runOnClient(mc->{command(mc,"tp VotexPeerA 0.5 -60.0 0.0 0 0");command(mc,"tp VotexPeerB 0 -60 1.3 180 0");});
  until(context,mc->mc.player.hasLineOfSight(mc.level.getPlayerByUUID(peer))&&Math.abs(mc.player.getZ()-(role.equals("A")?0:1.3))<.1,"wall test fixture restored");barrier(context,"wall-restored");
 }
 private String noticeValue(){try{var field=VotexSocial.class.getDeclaredField("notice");field.setAccessible(true);return (String)field.get(null);}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}}
 private AvatarRenderState state(Minecraft mc,UUID player){var entity=mc.level.getPlayerByUUID(player);if(entity==null)throw new AssertionError("Real player entity missing: "+player);return (AvatarRenderState)mc.getEntityRenderDispatcher().getRenderer(entity).createRenderState(entity,1);}
 private double[] bone(Minecraft mc,UUID player){var state=state(mc,player);var model=(PlayerModel)((net.minecraft.client.renderer.entity.player.AvatarRenderer)(Object)mc.getEntityRenderDispatcher().getRenderer(state)).getModel();model.setupAnim(state);return new double[]{model.rightArm.xRot,model.rightArm.yRot,model.rightArm.zRot};}
 private boolean remoteContains(UUID player){try{var field=LocalLooks.class.getDeclaredField("REMOTE");field.setAccessible(true);return ((Map<?,?>)field.get(null)).containsKey(player);}catch(ReflectiveOperationException error){throw new AssertionError(error);}}
 private void command(Minecraft mc,String command){mc.player.connection.sendCommand(command);}
 private void assertNoHandshake(ClientGameTestContext context){context.runOnClient(mc->{if("handshake".equals(EmotePlayer.activeId()))throw new AssertionError("Unconsented/invalid handshake started");});}
 private void until(ClientGameTestContext context,Predicate<Minecraft> condition,String stage){until(context,condition,stage,90);}
 private void until(ClientGameTestContext context,Predicate<Minecraft> condition,String stage,double timeoutSeconds){long deadline=System.nanoTime()+(long)(timeoutSeconds*1e9);context.waitFor(mc->{if(Files.exists(coordination.resolve(otherRole+"-failed.json")))throw new AssertionError("Other paired client failed before "+stage);if(System.nanoTime()>deadline)throw new AssertionError("Timed out at "+stage);return condition.test(mc);},12000);}
 private void seconds(ClientGameTestContext context,double value){long end=System.nanoTime()+(long)(value*1e9);until(context,mc->System.nanoTime()>=end,"wall-clock interval "+value);}
 private void mark(String stage)throws Exception{AtomicJson.write(coordination.resolve(role+"-"+stage+".json"),Map.of("role",role,"stage",stage,"utc",java.time.Instant.now().toString()));var progress=new LinkedHashMap<String,Object>(evidence);progress.put("lastStage",stage);progress.put("status",stage.equals("complete")?"passed":"in-progress");AtomicJson.write(destination.resolve("paired-progress.json"),progress);}
 private void barrier(ClientGameTestContext context,String stage)throws Exception{mark(stage);until(context,mc->Files.exists(coordination.resolve(otherRole+"-"+stage+".json")),"barrier "+stage);}
 private void shot(ClientGameTestContext context,String name,boolean comparison){if(comparison){context.runOnClient(mc->mc.gui.setScreen(new PairedProofScreen(peer,name)));context.waitTicks(3);}context.takeScreenshot(TestScreenshotOptions.of("paired-"+role+"-"+name).withDestinationDir(destination).disableCounterPrefix());context.runOnClient(mc->mc.gui.setScreen(null));}
 private void worldContactShot(ClientGameTestContext context){
  var old=context.computeOnClient(mc->new float[]{mc.player.getYRot(),mc.player.getXRot()});var camera=context.computeOnClient(mc->mc.options.getCameraType());
  try{
   // QA camera only: the two world models keep their solved facing and real positions.
   context.runOnClient(mc->{mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);mc.player.setYRot(90);mc.player.setXRot(8);});context.waitTicks(3);
   shot(context,"handshake-actual-world-side",false);
  }finally{context.runOnClient(mc->{mc.player.setYRot(old[0]);mc.player.setXRot(old[1]);mc.options.setCameraType(camera);});}
 }
 private void disconnect(ClientGameTestContext context){context.runOnClient(mc->{if(mc.level!=null){mc.level.disconnect(Component.literal("Paired QA complete"));mc.disconnectWithSavingScreen();}});until(context,mc->mc.level==null,"controlled disconnect");context.waitTicks(2);context.setScreen(TitleScreen::new);}
}
