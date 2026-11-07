package dev.votex.client;

import dev.votex.addons.IconImage;
import java.nio.file.Path;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Real logos/GUI textures plus a separate synthetic long-list eviction fixture. */
final class CatalogIconsGameChecks {
 private CatalogIconsGameChecks(){}
 static Map<String,Object> run(ClientGameTestContext context,Path images)throws Exception{
  context.getInput().resizeWindow(1280,720);context.waitTicks(3);
  var logos=new ArrayList<Map<String,Object>>();
  for(String[] expected:new String[][]{{"ImmediatelyFast","5ZwdcRci"},{"YetAnotherConfigLib","1eAoo2KR"},{"Fabric Language Kotlin","Ha28R6CL"}}){
   context.runOnClient(mc->mc.gui.setScreen(ModLibraryScreen.openSearch(VotexClient.INSTANCE,null,expected[0])));
   context.waitFor(mc->!((Boolean)field(mc.gui.screen(),"busy")),1000);
   String key="modrinth:"+expected[1];
   context.runOnClient(mc->{var projects=(List<ModLibraryService.Project>)field(mc.gui.screen(),"projects");if(projects.stream().noneMatch(p->p.id().equals(expected[1])))throw new AssertionError("Expected live project missing: "+expected[0]+" / "+field(mc.gui.screen(),"status"));});
   context.waitFor(mc->((Map<?,?>)field(mc.gui.screen(),"icons")).containsKey(key),1000);context.waitTicks(3);
   context.runOnClient(mc->{var icon=((Map<?,?>)field(mc.gui.screen(),"icons")).get(key);int width=(Integer)field(icon,"width"),height=(Integer)field(icon,"height");if(width>128||height>128||width<1||height<1)throw new AssertionError("Unbounded logo texture");var project=((List<ModLibraryService.Project>)field(mc.gui.screen(),"projects")).stream().filter(p->p.id().equals(expected[1])).findFirst().orElseThrow();logos.add(Map.of("project",expected[0],"id",expected[1],"sourceUrl",project.iconUrl(),"textureWidth",width,"textureHeight",height));if(!UiSampling.surfaceScopeClosed())throw new AssertionError("Logo sampling scope leaked");});
   shot(context,images,"catalog-logo-"+expected[1]);
  }
  context.runOnClient(mc->mc.gui.setScreen(new ModLibraryScreen(VotexClient.INSTANCE,null)));
  context.waitFor(mc->!((Boolean)field(mc.gui.screen(),"busy")),1000);
  context.runOnClient(mc->{byte[] png;try(var stream=CatalogIconsGameChecks.class.getResourceAsStream("/assets/votex_client_test/catalog-icon.png")){if(stream==null)throw new AssertionError("Long-list icon fixture absent");png=IconImage.thumbnail(stream.readAllBytes());}var fixture=new ArrayList<ModLibraryService.Installed>();for(int i=0;i<120;i++)fixture.add(new ModLibraryService.Installed("qa-icon-"+i,String.format(Locale.ROOT,"Icon fixture %03d",i),"test-only","","mods",true,false,png));Screen screen=mc.gui.screen();set(screen,"installed",List.copyOf(fixture));set(screen,"query","");set(screen,"page",0);set(screen,"status","QA · 120 synthetic read-only entries; no mod files installed");screen.resize(screen.width,screen.height);});
  context.waitTicks(4);
  var screen=context.computeOnClient(mc->mc.gui.screen());String firstKey="installed:mods:qa-icon-0";
  Object originalTexture=context.computeOnClient(mc->{var icon=((Map<?,?>)field(screen,"icons")).get(firstKey);if(icon==null)throw new AssertionError("First visible fixture icon missing");return field(icon,"texture");});
  int perPage=context.computeOnClient(mc->(Integer)field(screen,"rows")*2),last=(120-1)/perPage;
  for(int page=0;page<last;page++)click(context,"›");
  context.runOnClient(mc->{var cache=(Map<?,?>)field(screen,"icons");if(cache.size()>80||cache.size()<70||cache.containsKey(firstKey))throw new AssertionError("Synthetic list did not prove actual bounded icon eviction: "+cache.size());});
  shot(context,images,"catalog-cache-last-page");
  for(int page=0;page<last;page++)click(context,"‹");context.waitTicks(4);
  context.runOnClient(mc->{var cache=(Map<?,?>)field(screen,"icons");for(int i=0;i<perPage;i++)if(!cache.containsKey("installed:mods:qa-icon-"+i))throw new AssertionError("Visible first-page icon did not reload after eviction");if(originalTexture.equals(field(cache.get(firstKey),"texture")))throw new AssertionError("Evicted icon did not get a fresh texture");if(cache.size()>80)throw new AssertionError("Icon cache exceeded80");});
  shot(context,images,"catalog-cache-restored-first-page");
  context.runOnClient(mc->{mc.gui.setScreen(null);if(!((Map<?,?>)field(screen,"icons")).isEmpty())throw new AssertionError("Removed library retained texture residency");});
  return Map.of("realLogos",logos,"syntheticReadOnlyEntries",120,"pageSize",perPage,"nativePaginationClicks",last*2,"textureLimit",80,"evictedFirstPageRestored",true,"texturesReleasedOnClose",true,"microphone","not opened","modDownloads","none; icons only");
 }
 private static void shot(ClientGameTestContext c,Path destination,String name){c.takeScreenshot(TestScreenshotOptions.of("votex-goal-"+name).withDestinationDir(destination).disableCounterPrefix());}
 private static void click(ClientGameTestContext c,String title){c.runOnClient(mc->{Screen s=mc.gui.screen();var b=s.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(w->w.getMessage().getString().equals(title)).findFirst().orElseThrow();float scale=((Number)field(s,"scale")).floatValue();var frame=(dev.votex.ui.WorkspaceMotion.Frame)field(s,"displayedMotion");var e=new MouseButtonEvent(frame.screenX(b.getX()+b.getWidth()/2d)*scale,frame.screenY(b.getY()+b.getHeight()/2d)*scale,new MouseButtonInfo(0,0));if(!s.mouseClicked(e,false))throw new AssertionError("Pagination click not accepted");s.mouseReleased(e);});c.waitTicks(3);}
 private static Object field(Object target,String key){try{var f=target.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new AssertionError(e);}}
 private static void set(Object target,String key,Object value){try{var f=target.getClass().getDeclaredField(key);f.setAccessible(true);f.set(target,value);}catch(Exception e){throw new AssertionError(e);}}
}
