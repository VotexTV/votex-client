package dev.votex.client;

import com.google.gson.JsonParser;
import de.maxhenkel.voicechat.voice.client.ClientManager;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Test-only interoperability checks using the exact starter downloads, with no hardware capture. */
final class ModpackGameChecks {
 private ModpackGameChecks(){}
 static Map<String,String> restoreStartupBinding(Minecraft mc)throws Exception{
  // Fabric's runner restores the options snapshot captured in the Options
  // constructor, before CLIENT_STARTED runs our production first-start adapter.
  // Read its real saved result first, then undo only that test-only reset.
  String stored=Files.readString(mc.gameDirectory.toPath().resolve("options.txt")).lines().filter(line->line.startsWith("key_gui.xaero_new_waypoint:")).map(line->line.substring(line.indexOf(':')+1)).findFirst().orElseThrow();
  if(!stored.equals("key.keyboard.j"))throw new AssertionError("The production CLIENT_STARTED adapter did not save its conflict-free startup key: "+stored);
  var mappings=Arrays.stream(mc.options.keyMappings).filter(mapping->mapping.getName().equals("gui.xaero_new_waypoint")).toList();
  String before=mappings.stream().map(net.minecraft.client.KeyMapping::saveString).toList().toString();
  var saved=com.mojang.blaze3d.platform.InputConstants.getKey(stored);mappings.forEach(mapping->mapping.setKey(saved));net.minecraft.client.KeyMapping.resetMapping();
  return Map.of("productionSavedKey",stored,"afterFabricFixtureReset",before,"testRestoredSavedKey",saved.getName(),"reason","Fabric restoreDefaultGameOptions uses the pre-CLIENT_STARTED constructor snapshot; production initializer was not rerun or bypassed");
 }
 static Map<String,String> loaded(Minecraft mc)throws Exception{
  var source=mc.gameDirectory.toPath().resolve("modpack-manifest.json");
  var expected=JsonParser.parseString(Files.readString(source)).getAsJsonObject();
  Map<String,String> actual=new TreeMap<>();
  for(var entry:expected.entrySet()){
   var container=FabricLoader.getInstance().getModContainer(entry.getKey()).orElseThrow(()->new AssertionError("Starter mod not loaded: "+entry.getKey()));
   String version=container.getMetadata().getVersion().getFriendlyString();
   if(!version.equals(entry.getValue().getAsString()))throw new AssertionError("Wrong loaded starter version: "+entry.getKey()+" "+version);
   actual.put(entry.getKey(),version);
  }
  if(actual.size()!=18)throw new AssertionError("Expected all18 downloaded starter/dependency roots");
  var waypoints=Arrays.stream(mc.options.keyMappings).filter(mapping->mapping.getName().equals("gui.xaero_new_waypoint")).toList();
  if(waypoints.isEmpty())throw new AssertionError("Xaero waypoint mapping missing");
  var j=com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_J);
  System.out.println("VOTEX_MODPACK_WAYPOINT_BINDINGS "+waypoints.stream().map(mapping->mapping.getClass().getName()+":"+mapping.saveString()+":matchesJ="+mapping.matches(j)).toList());
  if(waypoints.stream().anyMatch(mapping->!mapping.saveString().equals(j.getName())))throw new AssertionError("Fresh starter bindings did not resolve the Xaero/Votex B conflict while preserving Voice's N");
  Path preserved=mc.gameDirectory.toPath().resolve("saved-binding-fixture.txt");Files.writeString(preserved,"key_gui.xaero_new_waypoint:key.keyboard.f8\n");
  var f8=com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_F8);
  waypoints.forEach(mapping->mapping.setKey(f8));net.minecraft.client.KeyMapping.resetMapping();
  try{if(new StarterKeyBindings(preserved).apply(mc)||waypoints.stream().anyMatch(mapping->!mapping.saveString().equals(f8.getName())))throw new AssertionError("A saved custom waypoint key was overwritten");}
  finally{waypoints.forEach(mapping->mapping.setKey(j));net.minecraft.client.KeyMapping.resetMapping();mc.options.save();}
  noMicrophone();return actual;
 }
 static Map<String,Object> appleSkin(ClientGameTestContext context,Path destination){
  context.runOnClient(mc->{try{
   Class<?> menu=Class.forName("com.terraformersmc.modmenu.ModMenu");
   if(!Boolean.TRUE.equals(menu.getMethod("hasConfigScreen",String.class).invoke(null,"appleskin")))throw new AssertionError("AppleSkin settings factory is unavailable");
   var screen=(Screen)menu.getMethod("getConfigScreen",String.class,Screen.class).invoke(null,"appleskin",new VotexScreen(VotexClient.INSTANCE,"MODULES"));
   if(screen==null)throw new AssertionError("AppleSkin settings did not construct");mc.gui.setScreen(screen);
  }catch(ReflectiveOperationException error){throw new AssertionError("AppleSkin/Cloth Config could not open",error);}});
  context.waitTicks(5);shot(context,destination,"modpack-appleskin-settings");context.runOnClient(mc->mc.gui.setScreen(null));
  return Map.of("actualModMenuFactory",true,"clothConfigScreenOpened",true,"configurationEdited",false);
 }
 static List<Map<String,Object>> shaders(ClientGameTestContext context,Path destination){
  List<Map<String,Object>> result=new ArrayList<>();
  try{for(String filename:new String[]{"ComplementaryReimagined_r5.9.3.zip","BSL_v10.1.8.zip"}){
   context.runOnClient(mc->{mc.gui.setScreen(null);mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);EmotePlayer.stopLocal();shader(filename,true);});
   context.waitTicks(35);String name=filename.startsWith("BSL")?"bsl":"complementary";
   context.runOnClient(mc->verifyShader(filename));shot(context,destination,"modpack-shader-"+name+"-world");
   context.runOnClient(mc->mc.gui.setScreen(new VotexScreen(VotexClient.INSTANCE,"MODULES")));context.waitTicks(12);shot(context,destination,"modpack-shader-"+name+"-modules");
   long frames=context.computeOnClient(mc->FirstPersonEmotes.submittedFrames()),start=System.nanoTime();
   context.runOnClient(mc->{mc.gui.setScreen(null);mc.options.setCameraType(CameraType.FIRST_PERSON);EmotePlayer.play("wave");});
   context.waitFor(mc->System.nanoTime()-start>1_000_000_000L,1200);shot(context,destination,"modpack-shader-"+name+"-first-person");
   context.runOnClient(mc->{verifyShader(filename);if(FirstPersonEmotes.submittedFrames()<=frames+2)throw new AssertionError("No first-person emote frames with "+filename);if(!mc.options.getCameraType().isFirstPerson())throw new AssertionError("Shader/emote changed camera");if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("voicechat"))noMicrophone();});
   result.add(Map.of("pack",filename,"realWorldFrames",true,"pipelineActiveWithoutFallback",true,"glassMenuRendered",true,"firstPersonSubmitted",true));
  }}finally{context.runOnClient(mc->{EmotePlayer.stopLocal();shader(null,false);mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.gui.setScreen(null);});}
  context.waitTicks(8);return result;
 }
 private static void shader(String filename,boolean enabled){try{
  var iris=Class.forName("net.irisshaders.iris.Iris");var config=iris.getMethod("getIrisConfig").invoke(null);
  if(filename!=null)config.getClass().getMethod("setShaderPackName",String.class).invoke(config,filename);
  config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,enabled);config.getClass().getMethod("save").invoke(config);iris.getMethod("reload").invoke(null);
 }catch(ReflectiveOperationException error){throw new AssertionError("Native Iris pipeline could not change",error);}}
 private static void verifyShader(String filename){try{
  var iris=Class.forName("net.irisshaders.iris.Iris");
  if(!filename.equals(iris.getMethod("getCurrentPackName").invoke(null))||!((Optional<?>)iris.getMethod("getCurrentPack").invoke(null)).isPresent()||Boolean.TRUE.equals(iris.getMethod("isFallback").invoke(null))||((Optional<?>)iris.getMethod("getStoredError").invoke(null)).isPresent())throw new AssertionError("Shader pack failed or silently fell back: "+filename);
 }catch(ReflectiveOperationException error){throw new AssertionError(error);}}
 static void noMicrophone(){var client=ClientManager.getClient();if(VoiceMicGate.allowed()||client!=null&&(client.getMicThread()!=null||client.getRecorder()!=null))throw new AssertionError("Hardware microphone/recorder unexpectedly active");}
 private static void shot(ClientGameTestContext context,Path destination,String name){context.takeScreenshot(TestScreenshotOptions.of("votex-goal-"+name).withDestinationDir(destination).disableCounterPrefix());}
}
