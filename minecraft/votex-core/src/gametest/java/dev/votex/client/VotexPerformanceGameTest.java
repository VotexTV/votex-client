package dev.votex.client;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import dev.votex.performance.OptimizationCoordinator;
import com.google.gson.JsonObject;

/** Opt-in, isolated static/HUD A/B. Same world/settings, three interleaved repetitions; no production saves. */
public final class VotexPerformanceGameTest {
 public void run(ClientGameTestContext context){
  try(var world=context.worldBuilder().create()){
   world.getConnection().waitForChunksRender();
   context.runOnClient(mc->{mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.options.framerateLimit().set(260);mc.options.enableVsync().set(false);mc.options.pauseOnLostFocus=false;VotexClient.INSTANCE.theme=new dev.votex.config.ThemeConfig(0xff72cfff,0xfff7faff,0xffa5bad3,0xc0121d30,12,35);for(var m:dev.votex.config.ModuleRegistry.MODULES)VotexClient.INSTANCE.modules.config().enabled.put(m.id(),true);VotexClient.INSTANCE.engine.config.adaptive=OptimizationCoordinator.Mode.DISABLED;});
   context.waitTicks(200);
   context.runOnClient(mc->mc.gui.setScreen(new VotexScreen(VotexClient.INSTANCE,"HOME")));context.waitTicks(12);context.takeScreenshot("votex-041-native-home");
   context.runOnClient(mc->mc.gui.setScreen(new VotexScreen(VotexClient.INSTANCE,"MODULES")));context.waitTicks(12);context.takeScreenshot("votex-041-native-modules");
   context.runOnClient(mc->mc.gui.setScreen(new VotexScreen(VotexClient.INSTANCE,"PERFORMANCE")));context.waitTicks(12);context.takeScreenshot("votex-041-performance-center");
   context.runOnClient(mc->mc.gui.setScreen(new EmoteWheel(VotexClient.INSTANCE)));context.waitTicks(12);context.takeScreenshot("votex-041-native-emotes");
   context.runOnClient(mc->mc.gui.setScreen(null));
   for(int repetition=0;repetition<3;repetition++)for(boolean optimized:new boolean[]{false,true}){
    final String name="E-static-hud-"+(optimized?"buffers-cache-on":"buffers-cache-off")+"-"+(repetition+1);
    context.runOnClient(mc->{var engine=VotexClient.INSTANCE.engine;engine.config.textCache=optimized;engine.config.reusableMotion=optimized;VotexTextCache.enabled=optimized;VotexTextCache.clear();engine.start(name,30);});
    for(int second=0;second<41;second++){context.runOnClient(mc->EmotePlayer.play("dance"));context.waitTicks(20);}
    context.runOnClient(mc->VotexClient.INSTANCE.engine.finish());context.waitTicks(5);
   }
   context.runOnClient(mc->{VotexClient.INSTANCE.engine.config.textCache=true;VotexClient.INSTANCE.engine.config.reusableMotion=true;});
   System.out.println("VOTEX_PERFORMANCE_AB_PASS 0.4.1 six interleaved static HUD captures");
  }
 }
}
