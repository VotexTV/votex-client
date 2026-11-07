package dev.votex.client;

import com.google.gson.JsonObject;
import dev.votex.config.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Prepared only: native getter, extracted shader state and rendered frames; no OS input. */
final class MenuBlurGameChecks {
 static Map<String,Object> run(ClientGameTestContext c,Path images)throws Exception{
  int vanilla=c.computeOnClient(mc->mc.options.menuBackgroundBlurriness().get());
  var results=new ArrayList<Map<String,Object>>();
  for(int[] size:new int[][]{{768,480},{1280,720}}){
   c.getInput().resizeWindow(size[0],size[1]);c.waitTicks(4);
   byte[] options=c.computeOnClient(mc->Files.readAllBytes(mc.gameDirectory.toPath().resolve("options.txt")));
   for(String mode:List.of("zero","light","strong","glass-off","module-off")){
    ThemeConfig[] exported=new ThemeConfig[1];int amount=mode.equals("light")?20:mode.equals("zero")?0:90;
    c.runOnClient(mc->{var core=VotexClient.INSTANCE;core.modules.config().blurMenus=!mode.equals("module-off");var theme=new JsonObject();theme.addProperty("schemaVersion",1);theme.addProperty("blur",amount);theme.addProperty("glassEffect",!mode.equals("glass-off"));Path path=core.directory.resolve("theme.json");AtomicJson.write(path,theme);exported[0]=ThemeConfig.read(path);mc.gui.setScreen(new VotexScreen(core,"MODULES"));});
    c.waitFor(mc->VotexClient.INSTANCE.theme.equals(exported[0]),1000);long ready=System.nanoTime()+800_000_000L;c.waitFor(mc->System.nanoTime()>ready,1000);c.waitTicks(3);
    int expected=mode.equals("light")?2:mode.equals("strong")?9:0;
    c.runOnClient(mc->{if(mc.options.getMenuBackgroundBlurriness()!=expected)throw new AssertionError("Native menu getter did not apply "+mode);Object frame=field(mc.gameRenderer,"gameRenderState");int extracted=((Number)field(field(frame,"optionsRenderState"),"menuBackgroundBlurriness")).intValue();if(extracted!=expected)throw new AssertionError("Shader state retained a different radius for "+mode);if(mc.options.menuBackgroundBlurriness().get()!=vanilla)throw new AssertionError("Theme changed Minecraft's stored option");if(!Arrays.equals(options,Files.readAllBytes(mc.gameDirectory.toPath().resolve("options.txt"))))throw new AssertionError("Theme rewrote options.txt");});
    c.takeScreenshot(TestScreenshotOptions.of("blur-"+size[0]+"-"+mode).withDestinationDir(images).disableCounterPrefix());results.add(Map.of("width",size[0],"height",size[1],"variant",mode,"actualGetterAndExtractedRadius",expected,"storedOptionPreserved",true));
   }
   c.runOnClient(mc->{mc.gui.setScreen(new Screen(Component.literal("Non-Votex scope check")){});if(mc.options.getMenuBackgroundBlurriness()!=vanilla)throw new AssertionError("Theme leaked into an unrelated screen");mc.gui.setScreen(null);if(mc.options.getMenuBackgroundBlurriness()!=vanilla)throw new AssertionError("Theme leaked into gameplay");});
  }
  c.runOnClient(mc->{VotexClient.INSTANCE.modules.config().blurMenus=true;mc.gui.setScreen(null);});
  return Map.of("cases",results,"vanillaOption",vanilla,"scope","private native menu/getter/extracted shader state; screenshot inspection still required; no minimum FPS or exact CSS kernel claim");
 }
 private static Object field(Object object,String name){try{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(Exception e){throw new AssertionError(e);}}
}
