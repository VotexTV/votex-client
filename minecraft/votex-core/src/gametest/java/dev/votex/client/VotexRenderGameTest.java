package dev.votex.client;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;

/** Actual client/mixin/render smoke in Fabric's isolated development test world. */
public final class VotexRenderGameTest implements FabricClientGameTest {
 @Override public void runTest(ClientGameTestContext context) {
  if("1".equals(System.getenv("VOTEX_COSMETICS2_TEST"))){Cosmetics2GameTest.run(context);return;}
  if("1".equals(System.getenv("VOTEX_LIQUID_GLASS_TEST"))){new LiquidGlassGameTest().run(context);return;}
  if(System.getenv("VOTEX_PAIRED_ROLE")!=null){new PairedClientGameTest().run(context);return;}
  if("1".equals(System.getenv("VOTEX_CATALOG_GOAL_TEST"))){CatalogGameChecks.run(context);return;}
  if("1".equals(System.getenv("VOTEX_INGAME_GOAL_TEST"))){new VotexInGameGoalTest().run(context);return;}
  if("1".equals(System.getenv("VOTEX_PERFORMANCE_TEST"))){new VotexPerformanceGameTest().run(context);return;}
  try(var world=context.worldBuilder().create()) {
   world.getConnection().waitForChunksRender();
   context.runOnClient(mc->{
    if(VotexClient.INSTANCE==null||mc.player==null)throw new AssertionError("Core/player did not initialize");
    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
    mc.options.fov().set(60);
    mc.options.gamma().set(1d);
    mc.player.setXRot(0);
    VotexClient.INSTANCE.theme=new dev.votex.config.ThemeConfig(0xff72cfff,0xfff7faff,0xffa5bad3,0xc0121d30,12,35);
   });
   for(String page:new String[]{"HOME","MODULES","PERFORMANCE"}){context.runOnClient(mc->mc.gui.setScreen(new VotexScreen(VotexClient.INSTANCE,page)));context.waitTicks(12);context.takeScreenshot("votex-041-final-"+page.toLowerCase());}
   context.runOnClient(mc->{mc.options.setCameraType(CameraType.FIRST_PERSON);mc.gui.setScreen(new EmoteWheel(VotexClient.INSTANCE));});context.waitTicks(12);context.takeScreenshot("votex-041-final-wheel");
   context.runOnClient(mc->{
    String[] labels={"Module und HUD gestalten","Deine aktuelle Blockposition","Rüstung · Füße · Übergänge","日本語 · Votex Client"};var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();long thread=Thread.currentThread().threadId();java.util.List<java.util.Map<String,Object>> results=new java.util.ArrayList<>();int sink=0;
    for(int repetition=0;repetition<3;repetition++)for(boolean cached:new boolean[]{false,true}){VotexTextCache.enabled=cached;VotexTextCache.clear();for(int i=0;i<10000;i++)sink+=VotexTextCache.width(mc.font,labels[i%labels.length]);long bytes=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();for(int i=0;i<100000;i++)sink+=VotexTextCache.width(mc.font,labels[i%labels.length]);long elapsed=System.nanoTime()-start;results.add(java.util.Map.of("cached",cached,"repetition",repetition+1,"nsPerLookup",elapsed/100000d,"bytesPerLookup",(bean.getThreadAllocatedBytes(thread)-bytes)/100000d,"hits",VotexTextCache.hits,"misses",VotexTextCache.misses));}
    try{dev.votex.config.AtomicJson.write(VotexClient.INSTANCE.directory.resolve("text-cache-probe.json"),java.util.Map.of("kind","font-width-microbenchmark-not-FPS","sink",sink,"results",results));}catch(Exception e){throw new AssertionError(e);}VotexTextCache.enabled=true;for(int i=0;i<400;i++)VotexTextCache.width(mc.font,"bounded-cache-"+i);if(VotexTextCache.size()!=256)throw new AssertionError("Unbounded text cache");VotexTextCache.clear();if(VotexTextCache.size()!=0)throw new AssertionError("Cache invalidation failed");mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.gui.setScreen(null);
   });
   if("1".equals(System.getenv("VOTEX_UI_SMOKE"))){System.out.println("VOTEX_UI_CACHE_PASS 0.4.1");return;}
   JsonArray catalog;
   try(var stream=getClass().getResourceAsStream("/assets/votex_client/catalog/index.json")) {
    if(stream==null)throw new AssertionError("Missing catalog");
    catalog=JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonArray();
   } catch(java.io.IOException e){throw new AssertionError(e);}
   if(catalog.size()<110)throw new AssertionError("Missing reviewed licensed catalog expansion");
   long creators=java.util.stream.StreamSupport.stream(catalog.spliterator(),false).filter(e->e.getAsJsonObject().has("creator")).count();
   if(creators<74)throw new AssertionError("Missing licensed Creator Collection expansion");
   for(JsonElement element:catalog) {
    JsonObject item=element.getAsJsonObject();String model=item.get("id").getAsString(),slot=item.get("slot").getAsString();
    context.runOnClient(mc->{
     JsonObject definition=WearableLayer.model(model);
     if(definition==null||definition.getAsJsonArray("parts").isEmpty())throw new AssertionError("Missing real geometry: "+model);
     for(JsonElement part:definition.getAsJsonArray("parts"))if(part.getAsJsonObject().has("texture")) {
      String texture=part.getAsJsonObject().get("texture").getAsString();
      try(var image=getClass().getResourceAsStream("/assets/votex_client/textures/cosmetics/"+texture)) {
       if(image==null||!java.util.Arrays.equals(image.readNBytes(8),new byte[]{(byte)137,80,78,71,13,10,26,10}))throw new AssertionError("Missing PNG: "+texture);
      } catch(java.io.IOException e){throw new AssertionError(e);}
     }
     JsonObject look=new JsonObject(),equipment=new JsonObject();
     equipment.addProperty(slot,model);
     look.add("equipment",equipment);look.addProperty("scale",100);look.addProperty("cape",slot.equals("cape"));
     LocalLooks.look=look;LocalLooks.revision++;
    });
    context.waitTicks(10);
    if(slot.equals("back")||slot.equals("cape")) {
     context.runOnClient(mc->mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
     context.waitTicks(3);
    }
    context.takeScreenshot("votex-03-model-"+model);
    context.runOnClient(mc->mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
   }
   context.runOnClient(mc->{
    JsonObject custom=WearableLayer.model("cloud-feathers").deepCopy();custom.addProperty("id","custom-04aa");
    JsonObject motion=JsonParser.parseString("{\"type\":\"oscillate\",\"axis\":\"z\",\"amplitude\":0.3,\"speed\":2,\"phase\":0,\"duration\":2,\"pivot\":[0,0,0],\"keyframes\":[{\"time\":0,\"angle\":0},{\"time\":0.5,\"angle\":0.7},{\"time\":1,\"angle\":0}]}").getAsJsonObject();
    for(var element:custom.getAsJsonArray("parts"))element.getAsJsonObject().add("motion",motion.deepCopy());
    JsonArray models=new JsonArray();models.add(custom);WearableLayer.setCustomModels(models);
    LocalLooks.look=JsonParser.parseString("{\"equipment\":{\"wings\":\"custom-04aa\"},\"scale\":100,\"cape\":false}").getAsJsonObject();LocalLooks.revision++;VotexClient.INSTANCE.lastError="";
   });
   context.waitTicks(20);context.takeScreenshot("votex-04-custom-keyframe");
   context.runOnClient(mc->{if(!VotexClient.INSTANCE.lastError.isEmpty())throw new AssertionError(VotexClient.INSTANCE.lastError);LocalLooks.look=new JsonObject();LocalLooks.revision++;});
   for(String gesture:EmotePlayer.IDS) {
    context.runOnClient(mc->EmotePlayer.play(gesture));
    context.waitTicks(30);
    context.takeScreenshot("votex-03-gesture-"+gesture);
   }
   context.waitTicks(150);
   context.takeScreenshot("votex-03-neutral");
   System.out.println("VOTEX_RENDER_SMOKE_PASS 0.4.1");
  }
 }
}
