package dev.votex.client;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
/** Optional adapter for the inspected LootBrain 3.8 preview API. No copied LootBrain implementation. */
final class LootBrainBridge {
 static boolean installed(){return FabricLoader.getInstance().isModLoaded("lootbrain");}
 static void open(VotexClient core){if(!installed())return;try{
  Class<?> api=Class.forName("dev.lootbrain.client.LootBrainClient");Object instance=api.getMethod("instance").invoke(null);
  Class<?> screen=Class.forName("dev.lootbrain.client.LootBrainSettingsScreen");
  Minecraft.getInstance().gui.setScreen((Screen)screen.getConstructor(api).newInstance(instance));
 }catch(Exception e){core.error("Installed LootBrain version does not expose the expected settings bridge",e);}}
}
