package dev.votex.client;

import dev.votex.ui.*;
import java.nio.file.Path;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

/** Real native bounds and input while status is visible; no OS input, clipboard or downloaded mods. */
final class InstallationNoticeGameChecks {
 static Map<String,Object> run(ClientGameTestContext c,Path images)throws Exception {
  var actualStaging=InstallationGameChecks.run(c,images);var cases=new ArrayList<Map<String,Object>>();
  for(int[] size:new int[][]{{768,480,3},{1280,720,2}}){
   String prefix="notice-"+size[0]+"-gui"+size[2];c.getInput().resizeWindow(size[0],size[1]);c.waitTicks(3);
   c.runOnClient(mc->{mc.options.guiScale().set(size[2]);mc.getWindow().setGuiScale(size[2]);var core=VotexClient.INSTANCE;core.modules.config().menuAnimations=true;long id=InstallationFeedback.ACTIVITY.begin("Shader installieren",System.nanoTime());InstallationFeedback.ACTIVITY.progress(id,"Datei und Abhängigkeiten werden geprüft · Originale QA-Statusvorschau",37,100);mc.gui.setScreen(new ModLibraryScreen(core,null));});
   c.waitFor(mc->!(boolean)field(mc.gui.screen(),"busy"),300);settle(c);
   c.runOnClient(mc->{clearButtons(mc.gui.screen(),"Datei importieren","Neu einlesen","‹","›");click(mc.gui.screen(),"Neu einlesen");});
   c.waitFor(mc->!(boolean)field(mc.gui.screen(),"busy"),300);settle(c);shot(c,images,prefix+"-library-working");
   c.runOnClient(mc->{var screen=mc.gui.screen();set(screen,"selected",new ModLibraryService.Installed("votex_qa","Originales QA-Modell","1","qa.jar","mods",true,true,null));screen.resize(screen.width,screen.height);clearButtons(screen,"Für Neustart deaktivieren","Für Neustart entfernen");});
   settle(c);shot(c,images,prefix+"-library-detail");
   c.runOnClient(mc->{var history=new ChatHistoryScreen(VotexClient.INSTANCE,"Unversandter Entwurf");mc.gui.setScreen(history);clearButtons(history,"Duplikate bündeln");var message=new GuiMessage(0,Component.literal("<QACreator> Originale lokale QA-Nachricht"),null,null,null);mc.gui.setScreen(new ChatContextScreen(VotexClient.INSTANCE,message,"Unversandter Entwurf",history.width-1,history.height-1,history));});
   settle(c);c.runOnClient(mc->clearButtons(mc.gui.screen(),"Nachricht kopieren","Name kopieren","An QACreator schreiben","Verlauf durchsuchen","Zurück"));shot(c,images,prefix+"-chat-bottom-corner");
   c.runOnClient(mc->{click(mc.gui.screen(),"Name kopieren");if(!(mc.gui.screen() instanceof ChatHistoryScreen)||!mc.keyboardHandler.getClipboard().equals("QACreator"))throw new AssertionError("Visible native chat action was intercepted");});
   c.runOnClient(mc->{var core=VotexClient.INSTANCE;var saved=new WorkspaceWindow.Saved();saved.width=saved.height=1;saved.x=saved.y=1;core.modules.config().workspaceWindow=saved;mc.gui.setScreen(new VotexScreen(core,"MODULES"));});
   settle(c);c.runOnClient(mc->{clearButtons(mc.gui.screen(),"Zurück zum Spiel","Mods & Shader","HUD gestalten","Alle");var state=InstallationFeedback.ACTIVITY.snapshot();InstallationFeedback.ACTIVITY.finish(state.id(),true,"Geprüfte Dateien für den nächsten Start vorgemerkt",System.nanoTime());});settle(c);shot(c,images,prefix+"-maximized-ready");
   c.runOnClient(mc->{mc.gui.setScreen(new HudEditorScreen(VotexClient.INSTANCE));clearButtons(mc.gui.screen(),"Layout speichern","8 px Raster","Leiste oben");click(mc.gui.screen(),"Leiste oben");clearButtons(mc.gui.screen(),"Layout speichern","8 px Raster","Leiste unten");click(mc.gui.screen(),"Leiste unten");});
   settle(c);shot(c,images,prefix+"-hud-toolbar");
   if(size[0]==768){
    c.runOnClient(mc->{long id=InstallationFeedback.ACTIVITY.begin("Weitere Ansichten prüfen",System.nanoTime());InstallationFeedback.ACTIVITY.progress(id,"Originale lokale QA-Statusvorschau",-1,-1);});
    for(String surface:List.of("appearance","arcade","voice-setup","voice-consent","cape-crop")){
     c.runOnClient(mc->{var core=VotexClient.INSTANCE;Screen screen=switch(surface){case "appearance"->new AppearanceScreen(core,null);case "arcade"->new ArcadeScreen(core);case "voice-setup"->new VoiceSetupScreen(core,null);case "voice-consent"->new VoiceConsentScreen(core,null);default->{Path file=core.directory.resolve("notice-cape-fixture.png");var image=new java.awt.image.BufferedImage(16,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);for(int y=0;y<32;y++)for(int x=0;x<16;x++)image.setRGB(x,y,0xff377bd1);javax.imageio.ImageIO.write(image,"PNG",file.toFile());yield new CapeCropScreen(core,null,file);}};mc.gui.setScreen(screen);});
     settle(c);c.runOnClient(mc->clearButtons(mc.gui.screen()));shot(c,images,prefix+"-"+surface);
     if(surface.equals("voice-consent"))c.runOnClient(mc->{click(mc.gui.screen(),"Später");if(VoiceMicGate.allowed())throw new AssertionError("Dismissal granted microphone access");});
    }
   }
   c.runOnClient(mc->{var core=VotexClient.INSTANCE;core.modules.config().menuAnimations=false;long id=InstallationFeedback.ACTIVITY.begin("Beschädigte Datei prüfen",System.nanoTime());InstallationFeedback.ACTIVITY.finish(id,false,"Die ZIP-Datei ist beschädigt; die bestehende Installation wurde nicht verändert.",System.nanoTime());mc.gui.setScreen(new EmoteWheel(core));});
   c.waitTicks(4);c.runOnClient(mc->{var layout=(EmoteWheelLayout)field(mc.gui.screen(),"layout");var notice=InstallationFeedback.layout();for(int slot=0;slot<8;slot++)if(layout.y(slot,1)+layout.cardHeight()>=notice.y())throw new AssertionError("Status covers wheel card");if(layout.height()-33+11>=notice.y())throw new AssertionError("Status covers wheel hint");});shot(c,images,prefix+"-wheel-error-reduced");
   cases.add(Map.of("physicalSize",List.of(size[0],size[1]),"requestedGuiScale",size[2],"libraryFooterAndDetailClear",true,"chatBottomActionReachable",true,"maximizedWindowClear",true,"bothHudDocksClear",true,"wheelHintAndCardsClear",true));
  }
  c.runOnClient(mc->{mc.gui.setScreen(null);var state=InstallationFeedback.ACTIVITY.snapshot();if(InstallationFeedback.layout().ribbon())throw new AssertionError("Gameplay lost full status card");InstallationFeedback.ACTIVITY.expire(System.nanoTime()+20_000_000_000L);if(InstallationFeedback.ACTIVITY.snapshot()!=null)throw new AssertionError("Error status did not expire");var core=VotexClient.INSTANCE;core.modules.config().workspaceWindow=new WorkspaceWindow.Saved();core.modules.config().menuAnimations=true;});
  return Map.of("actualStaging",actualStaging,"nativeCases",cases,"clipboard","test-only memory backend","input","actual native widget hit testing, no OS automation","scope","one private integrated world, no accounts, microphone or real profiles");
 }
 private static void clearButtons(Screen screen,String... names){
  var notice=InstallationFeedback.layout();if(!notice.ribbon()||InstallationFeedback.ACTIVITY.snapshot()==null)throw new AssertionError("Expected visible workspace ribbon");
  Set<String> expected=new HashSet<>(Arrays.asList(names));float scale=scale(screen);WorkspaceMotion.Frame frame=motion(screen);
  for(var child:screen.children())if(child instanceof AbstractWidget widget&&widget.visible&&(names.length==0||expected.contains(widget.getMessage().getString()))){
   expected.remove(widget.getMessage().getString());double top=frame.screenY(widget.getY())*scale/notice.scale(),bottom=frame.screenY(widget.getY()+widget.getHeight())*scale/notice.scale();
   if(bottom>notice.y()&&top<notice.y()+notice.height())throw new AssertionError("Status covers native control: "+widget.getMessage().getString());
  }
  if(!expected.isEmpty())throw new AssertionError("Missing controls: "+expected);
 }
 private static void click(Screen screen,String label){var button=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(label)).findFirst().orElseThrow();var frame=motion(screen);float scale=scale(screen);var event=new MouseButtonEvent(frame.screenX(button.getX()+3)*scale,frame.screenY(button.getY()+button.getHeight()/2d)*scale,new MouseButtonInfo(0,0));if(!screen.mouseClicked(event,false))throw new AssertionError("Visible edge rejected: "+label);screen.mouseReleased(event);}
 private static float scale(Screen screen){return screen instanceof HudEditorScreen?((HudEditorLayout)field(screen,"layout")).scale():((Number)field(screen,screen instanceof VotexScreen||screen instanceof CapeCropScreen?"uiScale":"scale")).floatValue();}
 private static WorkspaceMotion.Frame motion(Screen screen){return screen instanceof HudEditorScreen||screen instanceof ArcadeScreen?new WorkspaceMotion.Frame(1,0,0):(WorkspaceMotion.Frame)field(screen,"displayedMotion");}
 private static void settle(ClientGameTestContext c){long end=System.nanoTime()+650_000_000L;c.waitFor(mc->System.nanoTime()>end,1000);c.waitTicks(2);}
 private static void shot(ClientGameTestContext c,Path p,String name){c.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(p).disableCounterPrefix());}
 private static Object field(Object o,String key){try{var f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
 private static void set(Object o,String key,Object value){try{var f=o.getClass().getDeclaredField(key);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new AssertionError(e);}}
}
