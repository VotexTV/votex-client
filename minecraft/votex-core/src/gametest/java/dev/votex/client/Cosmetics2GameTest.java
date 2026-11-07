package dev.votex.client;
import com.google.gson.*;
import dev.votex.config.AtomicJson;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.*;import java.util.*;
/** Private real-GPU before/after review. No user profiles or authentication. */
final class Cosmetics2GameTest {
 static void run(ClientGameTestContext c){
  c.getInput().resizeWindow(1200,900);var proof=new LinkedHashMap<String,Object>();var cases=new ArrayList<Map<String,Object>>();
  try(var world=c.worldBuilder().create()){
   world.getConnection().waitForChunksRender();Path images=c.computeOnClient(mc->{var core=VotexClient.INSTANCE;if(!mc.gameDirectory.toString().toLowerCase(Locale.ROOT).contains("clientgametest"))throw new AssertionError("Private test directory required");mc.options.guiScale().set(2);mc.getWindow().setGuiScale(2);core.lastError="";LocalLooks.visible=LocalLooks.capeVisible=true;EmotePlayer.stopLocal();Path skin=core.directory.resolve("assets/skin-cosmetics-qa.png");Files.createDirectories(skin.getParent());try(var in=Cosmetics2GameTest.class.getResourceAsStream("/assets/votex_client_test/skins/studio.png")){Files.copy(in,skin,StandardCopyOption.REPLACE_EXISTING);}LocalLooks.selectSkin(core.directory,"cosmetics-qa");Path out=core.directory.resolve("cosmetics2-evidence");Files.createDirectories(out);return out;});
   for(String id:new String[]{"creator-bat-wings","midnight-raven","cloud-feathers","creator-swan-wings","creator-headphones","orbit-headphones","pocket-fox","vanilla-feather-wings","crimson-dread-wings","autumn-canopy-wings","ivory-seraph-wings","obsidian-seraph-wings"})for(int yaw:new int[]{0,90,180}){
    c.runOnClient(mc->{var model=WearableLayer.model(id);if(model==null)throw new AssertionError("Missing model "+id);var look=new JsonObject();var equipment=new JsonObject();equipment.addProperty(model.get("slot").getAsString(),id);look.add("equipment",equipment);look.addProperty("scale",100);LocalLooks.look=look;LocalLooks.revision++;mc.gui.setScreen(new View(id,yaw));});c.waitTicks(4);c.takeScreenshot(TestScreenshotOptions.of(id+"-"+yaw).withDestinationDir(images).disableCounterPrefix());
    c.runOnClient(mc->{if(!VotexClient.INSTANCE.lastError.isEmpty())throw new AssertionError(VotexClient.INSTANCE.lastError);});
   }
   for(String wing:new String[]{"cloud-feathers","vanilla-feather-wings","crimson-dread-wings","autumn-canopy-wings","ivory-seraph-wings","obsidian-seraph-wings"})for(String mode:new String[]{"idle","walk","sprint","jump","fall","glide","land"}){
    c.runOnClient(mc->{double horizontal=mode.equals("walk")?.075:mode.equals("sprint")?.22:0,vertical=mode.equals("jump")?.32:mode.equals("fall")?-.65:0;boolean grounded=!mode.equals("jump")&&!mode.equals("fall")&&!mode.equals("glide"),sprinting=mode.equals("sprint"),gliding=mode.equals("glide");if(mode.equals("land"))WearableLayer.testKinematics(0,0,true,false,false);else WearableLayer.testKinematics(horizontal,vertical,grounded,sprinting,gliding);var look=new JsonObject();var equipment=new JsonObject();equipment.addProperty("wings",wing);look.add("equipment",equipment);look.addProperty("scale",100);LocalLooks.look=look;LocalLooks.revision++;mc.gui.setScreen(new View(wing+" · "+mode,180));});c.waitTicks(mode.equals("land")?2:8);c.takeScreenshot(TestScreenshotOptions.of(wing+"-motion-"+mode).withDestinationDir(images).disableCounterPrefix());
   }
   c.runOnClient(mc->WearableLayer.clearTestKinematics());
   c.runOnClient(mc->EmoteRigGameChecks.run());
   for(String emote:new String[]{"wave","sit","dance","point","clap","salute","celebrate","bow","handshake"})for(float seconds:new float[]{.6f,1.2f,2.1f}){
    c.runOnClient(mc->{var look=new JsonObject();look.add("equipment",new JsonObject());look.addProperty("scale",100);LocalLooks.look=look;LocalLooks.revision++;var view=new View(emote+" · "+seconds,0);view.emote=emote;view.emoteSeconds=seconds;mc.gui.setScreen(view);});c.waitTicks(3);c.takeScreenshot(TestScreenshotOptions.of("emote-"+emote+"-"+Math.round(seconds*10)).withDestinationDir(images).disableCounterPrefix());
   }
   proof.put("nativeEmoteRigChecks","classic+slim; 9 gestures; 40 reset iterations; attached overlays; isolated preview extraction");
   proof.put("emoteScreenshots",27);
   Path skinFixture=c.computeOnClient(mc->SkinModelGameChecks.prepare(VotexClient.INSTANCE.directory));
   for(String kind:new String[]{"wide","slim"})for(int yaw:new int[]{0,90,180}){
    c.runOnClient(mc->{SkinModelGameChecks.selectAndCheck(skinFixture,kind);var look=new JsonObject();var equipment=new JsonObject();equipment.addProperty("pet","pocket-fox");equipment.addProperty("wings","cloud-feathers");equipment.addProperty("head","creator-headphones");look.add("equipment",equipment);look.addProperty("scale",100);LocalLooks.look=look;LocalLooks.revision++;mc.gui.setScreen(new View(kind+" skin + cosmetics",yaw));});c.waitTicks(3);c.takeScreenshot(TestScreenshotOptions.of("skin-model-"+kind+"-"+yaw).withDestinationDir(images).disableCounterPrefix());
   }
   c.runOnClient(mc->LocalLooks.selectSkin(VotexClient.INSTANCE.directory,"cosmetics-qa"));proof.put("skinModelChecks","Wide/slim metadata, shared first/third-person texture, result caching and appearance validation; six native screenshots");
   long firstPersonBefore=c.computeOnClient(mc->FirstPersonEmotes.submittedFrames());
   for(String emote:new String[]{"wave","clap","salute","dance","sit"}){
    c.runOnClient(mc->{mc.gui.setScreen(null);mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);mc.player.setXRot(0);mc.player.setYRot(0);EmotePlayer.play(emote);});c.waitTicks(12);c.takeScreenshot(TestScreenshotOptions.of("first-person-"+emote).withDestinationDir(images).disableCounterPrefix());
   }
   int previousFov=c.computeOnClient(mc->mc.options.fov().get());
   for(int fov:new int[]{30,70,110})for(String emote:new String[]{"wave","handshake"}){
    c.runOnClient(mc->{mc.options.fov().set(fov);mc.gui.setScreen(null);mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);mc.player.setXRot(0);mc.player.setYRot(0);EmotePlayer.stopLocal();EmotePlayer.play(emote);});c.waitTicks(12);c.takeScreenshot(TestScreenshotOptions.of("first-person-fov-"+fov+"-"+emote).withDestinationDir(images).disableCounterPrefix());
   }
   c.runOnClient(mc->{mc.options.fov().set(previousFov);EmotePlayer.stopLocal();});proof.put("firstPersonFovSweep",List.of(30,70,110));
   c.runOnClient(mc->{if(FirstPersonEmotes.submittedFrames()<=firstPersonBefore)throw new AssertionError("No first-person emote submissions");EmotePlayer.stopLocal();mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);});
   proof.put("firstPersonSnapshots",11);
   int petCount=0;
   try(var source=Cosmetics2GameTest.class.getResourceAsStream("/assets/votex_client/catalog/index.json")){
    var catalog=JsonParser.parseString(new String(source.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
    for(var item:catalog){if("1".equals(System.getenv("VOTEX_COSMETICS2_FOCUSED")))break;var entry=item.getAsJsonObject();if(!entry.get("slot").getAsString().equals("pet"))continue;String id=entry.get("id").getAsString();
     c.runOnClient(mc->{if(!dev.votex.cosmetics.CompanionMotion.supports(id))throw new AssertionError("No companion motion profile: "+id);var look=new JsonObject();var equipment=new JsonObject();equipment.addProperty("pet",id);look.add("equipment",equipment);look.addProperty("scale",100);LocalLooks.look=look;LocalLooks.revision++;mc.gui.setScreen(new View(id,0));});c.waitTicks(3);c.takeScreenshot(TestScreenshotOptions.of("all-pets-"+id).withDestinationDir(images).disableCounterPrefix());c.runOnClient(mc->{if(!VotexClient.INSTANCE.lastError.isEmpty())throw new AssertionError(VotexClient.INSTANCE.lastError);});petCount++;
    }
   }
   proof.put("allPetScreenshots",petCount);
   for(String gesture:new String[]{"idle","shake","leap","returned"}) {
    c.runOnClient(mc->{var look=new JsonObject();var equipment=new JsonObject();equipment.addProperty("pet","pocket-fox");look.add("equipment",equipment);look.addProperty("scale",100);LocalLooks.look=look;LocalLooks.revision++;var view=new View("Pocket Fox · "+gesture,0);float phase=(mc.player.getUUID().hashCode()&255)*.023f;float seconds=switch(gesture){case "shake"->22.55f;case "leap"->29.5f;case "returned"->30.1f;default->17f;};view.preview.animationAge=(seconds-phase)*20;mc.gui.setScreen(view);});c.waitTicks(3);c.takeScreenshot(TestScreenshotOptions.of("companion-gesture-"+gesture).withDestinationDir(images).disableCounterPrefix());
   }
   for(String id:new String[]{"sky-whale","night-owl","cloud-sprite","creator-fox","kenney-cube-pets-animal-cat","kenney-cube-pets-animal-bee","kenney-cube-pets-animal-bunny","cape-qa"})for(int yaw:new int[]{0,90,180}){
    c.runOnClient(mc->{var look=new JsonObject();var equipment=new JsonObject();if(id.equals("cape-qa")){look.addProperty("cape",true);}else{var model=WearableLayer.model(id);if(model==null)throw new AssertionError(id);equipment.addProperty("pet",id);}look.add("equipment",equipment);look.addProperty("scale",100);LocalLooks.look=look;LocalLooks.revision++;mc.gui.setScreen(new View(id,yaw));});c.waitTicks(5);c.takeScreenshot(TestScreenshotOptions.of(id+"-"+yaw).withDestinationDir(images).disableCounterPrefix());c.runOnClient(mc->{if(!VotexClient.INSTANCE.lastError.isEmpty())throw new AssertionError(VotexClient.INSTANCE.lastError);});
   }
   for(int frame=0;frame<2;frame++){int frameIndex=frame;c.runOnClient(mc->{var look=new JsonObject();var equipment=new JsonObject();equipment.addProperty("pet","pocket-fox");look.add("equipment",equipment);look.addProperty("scale",100);LocalLooks.look=look;LocalLooks.revision++;mc.gui.setScreen(new View("Pocket Fox idle · frame "+frameIndex,0));});c.waitTicks(frameIndex==0?2:24);c.takeScreenshot(TestScreenshotOptions.of("pocket-fox-idle-"+frameIndex).withDestinationDir(images).disableCounterPrefix());}
   for(String id:new String[]{"creator-bat-wings","midnight-raven","cloud-feathers","creator-swan-wings","creator-headphones","orbit-headphones","pocket-fox","vanilla-feather-wings","crimson-dread-wings","autumn-canopy-wings","ivory-seraph-wings","obsidian-seraph-wings"})c.runOnClient(mc->{var model=WearableLayer.model(id);int quads=0;for(var p:model.getAsJsonArray("parts"))quads+=p.getAsJsonObject().getAsJsonArray("quads").size()/4;cases.add(Map.of("id",id,"quads",quads,"parts",model.getAsJsonArray("parts").size()));});
   if("1".equals(System.getenv("VOTEX_COSMETICS2_BENCHMARK")))benchmark(c,proof,images);
   proof.put("models",cases);proof.put("status","passed");proof.put("scope","Actual native AvatarRenderer and WearableLayer, front/side/back; isolated reference skin; synthetic input states for idle, walk, sprint, jump, fall, glide, and landing. Not a multi-player FPS benchmark.");c.runOnClient(mc->{AtomicJson.write(images.resolve("native-cosmetics2-proof.json"),proof);mc.gui.setScreen(null);});System.out.println("VOTEX_COSMETICS2_PASS");
  }catch(Exception e){throw new AssertionError(e);}
 }
 private static void benchmark(ClientGameTestContext c,Map<String,Object> proof,Path images){
  long sharedBefore=c.computeOnClient(mc->WearableLayer.sharedShapeHits());
  var reports=new ArrayList<String>();
  var crowd=new ArrayList<UUID>();
  if("1".equals(System.getenv("VOTEX_COSMETICS2_CROWD")))c.runOnClient(mc->{
   for(int i=0;i<10;i++){
    UUID id=UUID.nameUUIDFromBytes(("votex-render-fixture-"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var avatar=new net.minecraft.client.player.RemotePlayer(mc.level,new com.mojang.authlib.GameProfile(id,"CosmeticQA"+i));
    avatar.setId(-2000-i);avatar.setPos(mc.player.getX()+(i%5-2)*1.7,mc.player.getY(),mc.player.getZ()-5-(i/5)*3);avatar.setOnGround(true);mc.level.addEntity(avatar);crowd.add(id);
   }
  });
  for(boolean equipped:new boolean[]{false,true}){
   String previous=c.computeOnClient(mc->{mc.gui.setScreen(null);mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);mc.options.framerateLimit().set(260);mc.options.enableVsync().set(false);mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);EmotePlayer.stopLocal();var look=new JsonObject();var equipment=new JsonObject();if(equipped){equipment.addProperty("wings","vanilla-feather-wings");equipment.addProperty("head","creator-headphones");equipment.addProperty("pet","pocket-fox");}look.add("equipment",equipment);look.addProperty("cape",equipped);look.addProperty("scale",100);LocalLooks.look=look;LocalLooks.revision++;var engine=VotexPerformanceEngine.INSTANCE;String before=engine.lastFile();if(!engine.start("cosmetics2-flat-world-"+(equipped?"equipped-vanilla-feather-wings":"baseline"),30))throw new AssertionError("Benchmark already running");return before;});
   c.runOnClient(mc->{for(UUID id:crowd){var appearance=new JsonObject();appearance.add("look",LocalLooks.look.deepCopy());appearance.addProperty("visible",true);appearance.addProperty("capeVisible",true);appearance.add("models",new JsonArray());appearance.add("emotes",new JsonArray());LocalLooks.remote(id,appearance);}});
   long deadline=System.nanoTime()+65_000_000_000L;
   c.waitFor(mc->{var engine=VotexPerformanceEngine.INSTANCE;if(engine.status().contains("fehlgeschlagen")||System.nanoTime()>deadline)throw new AssertionError("Benchmark failed: "+engine.status());return !engine.lastFile().isEmpty()&&!engine.lastFile().equals(previous);},12000);
   reports.add(c.computeOnClient(mc->VotexPerformanceEngine.INSTANCE.lastFile()));
   c.takeScreenshot(TestScreenshotOptions.of("benchmark-"+(equipped?"equipped":"baseline")).withDestinationDir(images).disableCounterPrefix());
  }
  long sharedHits=c.computeOnClient(mc->WearableLayer.sharedShapeHits())-sharedBefore;
  if(!crowd.isEmpty()&&sharedHits<crowd.size())throw new AssertionError("Identical catalog looks were not shared across crowd fixtures");
  proof.put("sharedGeometryCacheHits",sharedHits);proof.put("singlePlayerBenchmarkReports",reports);proof.put("clientOnlyCrowdEntities",crowd.size());proof.put("benchmarkScope","Two 30 s captures after 10 s warmup each, same isolated flat world, baseline vs cloud wings/headphones/fox/cape. Optional crowd consists of client-only render fixtures, not connected players. No multiplayer/network or before-source FPS claim.");
 }
 private static final class View extends Screen {
  final AvatarPreview preview=new AvatarPreview();final String label;
  String emote;float emoteSeconds;
  View(String label,int yaw)throws Exception{super(Component.literal("Cosmetics 2 QA"));this.label=label;var f=AvatarPreview.class.getDeclaredField("yaw");f.setAccessible(true);f.setFloat(preview,yaw);}
  public boolean isPauseScreen(){return false;}
  public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){g.fill(0,0,width,height,0xff172332);UiText.title(g,label,22,20,0xffedf7ff,1.4f);if(emote==null)preview.render(g,35,55,width-35,height-25);else EmotePlayer.withPreview(emote,emoteSeconds,()->preview.render(g,35,55,width-35,height-25));}
 }
}
