package dev.votex.client;

import com.google.gson.*;
import dev.votex.config.AtomicJson;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** One isolated native GPU sweep; no model illustrations or browser/WebGL fallback. */
final class CatalogGameChecks {
 private CatalogGameChecks(){}
 static void run(ClientGameTestContext context){
  if(!"1".equals(System.getenv("VOTEX_CATALOG_GOAL_TEST")))throw new AssertionError("Explicit isolated catalog QA required");
  context.getInput().resizeWindow(1000,900);List<Map<String,Object>> checked=new ArrayList<>();Map<String,Object> proof=new LinkedHashMap<>();
  try(var world=context.worldBuilder().create()){
   world.getConnection().waitForChunksRender();
   Path destination=context.computeOnClient(mc->{
    if(!mc.gameDirectory.toString().toLowerCase(Locale.ROOT).contains("clientgametest"))throw new AssertionError("Refusing non-QA data directory");
    var core=VotexClient.INSTANCE;core.lastError="";mc.options.guiScale().set(2);mc.getWindow().setGuiScale(2);core.theme=new dev.votex.config.ThemeConfig(0xff83baff,0xfff6faff,0xffa9bdd6,0xca152236,14,35);core.modules.config().menuAnimations=false;
    Path skin=core.directory.resolve("assets/skin-catalog-reference.png");Files.createDirectories(skin.getParent());try(var stream=CatalogGameChecks.class.getResourceAsStream("/assets/votex_client_test/skins/studio.png")){Files.copy(stream,skin,StandardCopyOption.REPLACE_EXISTING);}LocalLooks.selectSkin(core.directory,"catalog-reference");LocalLooks.visible=true;LocalLooks.capeVisible=true;EmotePlayer.stopLocal();
    Path out=core.directory.resolve("catalog-evidence");Files.createDirectories(out);return out;
   });
   JsonArray catalog;try(var stream=CatalogGameChecks.class.getResourceAsStream("/assets/votex_client/catalog/index.json")){catalog=JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonArray();}
   long nonCapes=java.util.stream.StreamSupport.stream(catalog.spliterator(),false).filter(item->!item.getAsJsonObject().get("slot").getAsString().equals("cape")).count();if(nonCapes<=300)throw new AssertionError("Expected over300 installed non-cape cosmetics");
   proof.put("status","partial");proof.put("catalogItems",catalog.size());proof.put("nonCapeItems",nonCapes);proof.put("kind","actual native AvatarRenderer and WearableLayer using the in-game orbit preview");proof.put("checked",checked);
   for(var entry:catalog){
    var item=entry.getAsJsonObject();String id=item.get("id").getAsString(),slot=item.get("slot").getAsString(),name=item.get("name").getAsString();
    context.runOnClient(mc->{
     var model=WearableLayer.model(id);if(model==null||model.getAsJsonArray("parts").isEmpty())throw new AssertionError("Missing runtime model "+id);
     JsonObject look=new JsonObject(),equipment=new JsonObject();equipment.addProperty(slot,id);look.add("equipment",equipment);look.addProperty("scale",100);look.addProperty("cape",slot.equals("cape"));LocalLooks.look=look;LocalLooks.revision++;
     mc.gui.setScreen(new CatalogView(name,id,item.has("creator")?item.getAsJsonObject("creator").get("name").getAsString():"Votex Original",slot.equals("back")||slot.equals("cape")));
    });
    context.waitTicks(3);context.takeScreenshot(TestScreenshotOptions.of("catalog-"+id).withDestinationDir(destination).disableCounterPrefix());
    context.runOnClient(mc->{
     if(!VotexClient.INSTANCE.lastError.isEmpty())throw new AssertionError(id+": "+VotexClient.INSTANCE.lastError);
     var field=WearableLayer.class.getDeclaredField("MODELS");field.setAccessible(true);Map<?,?> cache=(Map<?,?>)field.get(null);if(cache.size()>16)throw new AssertionError("Parsed cosmetic cache grew beyond16");
     var instances=WearableLayer.class.getDeclaredField("INSTANCES");instances.setAccessible(true);boolean built=false;int groups=0;
     for(Object layer:(Set<?>)instances.get(null)){var baked=WearableLayer.class.getDeclaredField("baked");baked.setAccessible(true);Object value=((Map<?,?>)baked.get(layer)).get(mc.player.getUUID());if(value==null)continue;var revision=value.getClass().getDeclaredMethod("revision");revision.setAccessible(true);if((int)revision.invoke(value)!=LocalLooks.revision)continue;var pieces=value.getClass().getDeclaredMethod("pieces");pieces.setAccessible(true);groups=((List<?>)pieces.invoke(value)).size();built=true;break;}
     if(!built||!slot.equals("cape")&&groups==0)throw new AssertionError("Native renderer did not build equipped geometry "+id);
     checked.add(Map.of("id",id,"slot",slot,"nativeMaterialGroups",groups,"parsedCacheSize",cache.size(),"nativePNG","catalog-"+id+".png"));
    });
    if(checked.size()%16==0){AtomicJson.write(destination.resolve("native-catalog-proof.json"),proof);System.out.println("VOTEX_NATIVE_CATALOG_STAGE "+checked.size()+"/"+catalog.size());}
   }
   context.runOnClient(mc->{
    var field=WearableLayer.class.getDeclaredField("MODELS");field.setAccessible(true);Map<?,?> cache=(Map<?,?>)field.get(null);String first=catalog.get(0).getAsJsonObject().get("id").getAsString();if(cache.containsKey(first))throw new AssertionError("Old parsed model was not evicted");if(WearableLayer.model(first)==null||cache.size()>16)throw new AssertionError("Evicted model could not be reloaded");mc.gui.setScreen(null);
   });
   proof.put("status","passed");proof.put("evictedModelReloaded",true);proof.put("cacheLimit",16);proof.put("limitations",List.of("Native preview sweep, not a multi-player FPS benchmark","Static neutral attachment view per item; arbitrary simultaneous outfit/emote/scale combinations are not exhaustively proven"));AtomicJson.write(destination.resolve("native-catalog-proof.json"),proof);System.out.println("VOTEX_NATIVE_CATALOG_PASS "+catalog.size()+" items / "+nonCapes+" non-capes");
  }catch(Exception failure){throw new AssertionError("Native catalog sweep failed",failure);}
 }
 private static final class CatalogView extends Screen {
  private final String name,id,author;private final AvatarPreview preview=new AvatarPreview();
  CatalogView(String name,String id,String author,boolean back)throws Exception{super(Component.literal("Votex native catalog QA"));this.name=name;this.id=id;this.author=author;var yaw=AvatarPreview.class.getDeclaredField("yaw");yaw.setAccessible(true);yaw.setFloat(preview,back?180:0);}
  @Override public boolean isPauseScreen(){return false;}
  @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
   g.fill(0,0,width,height,0xff101b29);UiText.title(g,name,24,24,0xffedf7ff,1.5f);UiText.draw(g,id,24,49,0xff92b3ce);UiText.draw(g,author,24,height-25,0xffb6cce0);preview.render(g,24,75,width-24,height-45);
  }
 }
}
