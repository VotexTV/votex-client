package dev.votex.client;
import dev.votex.config.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.resources.Identifier;

import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;


import java.nio.file.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.logging.Logger;
public final class VotexClient implements ClientModInitializer {
 public static final Logger LOG=Logger.getLogger("Votex");
 public static VotexClient INSTANCE;
 public ModuleRegistry modules;
 public ThemeConfig theme=ThemeConfig.defaults();
 public VotexPerformanceEngine engine; VotexHud hud; private boolean hadWorld;
 public Path directory;
 public KeyMapping menuKey,emoteKey;
 private WatchService watch;
 private String clock="";private long clockSecond=-1;
 public String lastError="";
 @Override public void onInitializeClient(){
  INSTANCE=this;directory=FabricLoader.getInstance().getConfigDir().resolve("votex");modules=new ModuleRegistry(directory.resolve("modules.json"));
  var starterKeys=new StarterKeyBindings(FabricLoader.getInstance().getGameDir().resolve("options.txt"));
  ClientLifecycleEvents.CLIENT_STARTED.register(starterKeys::apply);
  try{Files.createDirectories(directory);modules.load();theme=ThemeConfig.read(directory.resolve("theme.json"));}catch(Exception e){error("Could not load Votex settings; original files preserved",e);}
  LocalLooks.visible=modules.config().cosmeticsVisible;LocalLooks.capeVisible=modules.config().capeVisible;engine=new VotexPerformanceEngine(directory);hud=new VotexHud(this);
  var category=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("votex_client","main"));
  menuKey=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.votex_client.menu",GLFW.GLFW_KEY_RIGHT_SHIFT,category));
  emoteKey=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.votex_client.emotes",GLFW.GLFW_KEY_B,category));
  var lightOverlay=new LightLevelOverlay();
  net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(lightOverlay::render);
  ClientTickEvents.END_CLIENT_TICK.register(client->{
   lightOverlay.tick(client,modules.enabled("light"),Math.clamp(modules.config().lightOverlayRadius,4,12),modules.config().lightRisksOnly);
   EmotePlayer.tick();VotexSocial.tick();hud.tick(client);engine.tick(client.gui.screen() instanceof VotexScreen);
   if(hadWorld&&client.player==null)engine.clearWorld();hadWorld=client.player!=null;
   while(menuKey.consumeClick()){if(client.gui.screen() instanceof VotexScreen screen)screen.onClose();else if(client.gui.screen()==null)client.gui.setScreen(new VotexScreen(this,"HOME"));}
   while(emoteKey.consumeClick()){if(client.player!=null&&client.gui.screen()==null)client.gui.setScreen(new EmoteWheel(this));}
  });
  HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("votex_client","hud"),(graphics,delta)->{if(Minecraft.getInstance().gui.screen()==null){hud.render(graphics,false);VotexSocial.drawInvitation(graphics);InstallationFeedback.render(graphics);}});
  ScreenEvents.AFTER_INIT.register((client,screen,w,h)->{ScreenEvents.afterExtract(screen).register((s,g,x,y,d)->InstallationFeedback.render(g,x,y));if(screen instanceof PauseScreen)Screens.getWidgets(screen).add(new GlassButton(this,8,8,105,24,"Votex Client","",()->false,b->client.gui.setScreen(new VotexScreen(this,"HOME"))));if(screen instanceof TitleScreen){String nonce=System.getProperty("votex.launchNonce","");if(nonce.matches("[a-f0-9-]{36}")){try{AtomicJson.write(directory.resolve("startup.json"),Map.of("schemaVersion",1,"nonce",nonce,"stage","title-screen"));}catch(Exception e){error("Startup confirmation could not be written",e);}}}});
  LocalLooks.reload(directory);Wardrobe.refresh(this);VotexSocial.init();LastServerHistory.init();
  net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->saveProfileCover(client));
  startWatcher();ClientLifecycleEvents.CLIENT_STOPPING.register(client->{saveProfileCover(client);engine.close();try{if(watch!=null)watch.close();}catch(Exception e){LOG.warning("Theme watcher could not close");}});
  LOG.info("Votex Client Core initialized; multiplayer appearance sync requires a compatible Votex Relay");
 }
 private void startWatcher(){try{watch=FileSystems.getDefault().newWatchService();directory.register(watch,StandardWatchEventKinds.ENTRY_CREATE,StandardWatchEventKinds.ENTRY_MODIFY);Thread thread=new Thread(()->{try{while(true){WatchKey key=watch.take();boolean changed=false,looks=false,performance=false;for(var event:key.pollEvents()){String name=event.context().toString();changed|=name.equals("theme.json");looks|=name.equals("presets.json");performance|=name.equals("performance.json");}if(!key.reset())return;if(performance)engine.reloadConfig();if(looks)Minecraft.getInstance().execute(()->{LocalLooks.reload(directory);Wardrobe.refresh(this);});if(changed)Minecraft.getInstance().execute(()->{try{theme=ThemeConfig.read(directory.resolve("theme.json"));}catch(Exception e){error("Rejected invalid shared theme; keeping the previous design",e);}});}}catch(ClosedWatchServiceException ignored){}catch(InterruptedException e){Thread.currentThread().interrupt();}},"Votex theme watcher");thread.setDaemon(true);thread.start();}catch(Exception e){error("Live theme synchronization unavailable",e);}}
 private void saveProfileCover(Minecraft client){
  if(client.player==null)return;
  String profile=System.getProperty("votex.profileId","");
  if(!profile.matches("[A-Za-z0-9_-]{1,80}"))return;
  try{
   net.minecraft.client.Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(),image->{try(image){
    int width=image.getWidth(),height=image.getHeight();double factor=Math.min(1,Math.min(960./width,540./height));int ow=Math.max(1,(int)Math.round(width*factor)),oh=Math.max(1,(int)Math.round(height*factor));
    BufferedImage cover=new BufferedImage(ow,oh,BufferedImage.TYPE_INT_RGB);for(int y=0;y<oh;y++)for(int x=0;x<ow;x++)cover.setRGB(x,y,image.getPixel(Math.min(width-1,(int)(x/factor)),Math.min(height-1,(int)(y/factor))));
    Path output=directory.resolve("profile-cover.png"),temporary=directory.resolve("profile-cover.tmp.png");Files.createDirectories(directory);if(!ImageIO.write(cover,"png",temporary.toFile()))throw new IOException("PNG encoder unavailable");cover.flush();try{Files.move(temporary,output,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ignored){Files.move(temporary,output,StandardCopyOption.REPLACE_EXISTING);}
   }catch(Exception e){LOG.warning("Could not save the local profile cover: "+e.getClass().getSimpleName());}});
  }catch(Exception e){LOG.warning("Could not save the local profile cover: "+e.getClass().getSimpleName());}
 }
 public void toggle(String id){if(ModuleRegistry.MODULES.stream().noneMatch(m->m.id().equals(id)))return;modules.config().enabled.put(id,!modules.enabled(id));engine.saveModules(modules);hud.tick(Minecraft.getInstance());}
 public void error(String text,Exception e){lastError=text;LOG.warning(text+": "+e.getClass().getSimpleName());}
}


