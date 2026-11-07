package dev.votex.client;
import com.google.gson.*;
import dev.votex.config.*;
import dev.votex.ui.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

/** Scripted render coordinates only: no OS pointer/key input, accounts or device access. */
final class HoverEffectsGameChecks {
 static Map<String,Object> run(ClientGameTestContext c,Path images)throws Exception{
  var rows=new ArrayList<Map<String,Object>>();
  for(int[] size:new int[][]{{768,480},{1280,720}}){
   c.getInput().resizeWindow(size[0],size[1]);c.waitTicks(3);c.runOnClient(mc->{mc.options.guiScale().set(2);mc.getWindow().setGuiScale(2);mc.gui.setScreen(new ProofScreen(VotexClient.INSTANCE));});c.waitTicks(3);
   for(String variant:new String[]{"ultra","hover-off","light-off","reduced","flat"}){
    var theme=new JsonObject();theme.addProperty("schemaVersion",1);theme.addProperty("accent","#6e9fff");theme.addProperty("secondary","#74e5ff");theme.addProperty("hover",!variant.equals("hover-off"));theme.addProperty("proximityEffect",!variant.equals("light-off")&&!variant.equals("flat"));theme.addProperty("proximity",90);theme.addProperty("glow",86);theme.addProperty("reflection",90);theme.addProperty("lift",84);theme.addProperty("tilt",55);theme.addProperty("speed",45);theme.addProperty("stiffness",42);theme.addProperty("damping",55);theme.addProperty("reducedMotion",variant.equals("reduced"));theme.addProperty("glassEffect",!variant.equals("flat"));theme.addProperty("glowEffect",!variant.equals("flat"));theme.addProperty("reflectionEffect",!variant.equals("flat"));theme.addProperty("wobbleEffect",!variant.equals("flat"));theme.addProperty("shadow",variant.equals("flat")?0:80);
    ThemeConfig[] expected=new ThemeConfig[1];c.runOnClient(mc->{var path=VotexClient.INSTANCE.directory.resolve("theme.json");AtomicJson.write(path,theme);expected[0]=readTheme(path);((ProofScreen)mc.gui.screen()).onCard=false;});
    c.waitFor(mc->VotexClient.INSTANCE.theme.equals(expected[0]),1200);long ready=System.nanoTime()+700_000_000L;c.waitFor(mc->System.nanoTime()>ready,1200);
    rows.add(c.computeOnClient(mc->{var screen=(ProofScreen)mc.gui.screen();HoverMotion state=screen.primaryState();if(variant.equals("hover-off")){if(state.hover()!=0||state.near()!=0)throw new AssertionError("Disabled hover retained a live effect");}else if(state.hover()<.9)throw new AssertionError("Rendered native button did not enter hover: "+variant);if((variant.equals("light-off")||variant.equals("reduced")||variant.equals("flat"))&&state.near()!=0)throw new AssertionError("Disabled mouse light survived theme reload");if(variant.equals("ultra")&&state.near()<.9)throw new AssertionError("Live mouse light did not enter");if(screen.primary.getX()!=28||screen.primary.getWidth()!=280)throw new AssertionError("Hover moved the input plane");return Map.<String,Object>of("size",List.of(size[0],size[1]),"variant",variant,"hover",state.hover(),"near",state.near(),"fileWatcherApplied",true,"stableInputBounds",true);}));
    c.takeScreenshot(TestScreenshotOptions.of("hover-"+size[0]+"-"+variant).withDestinationDir(images).disableCounterPrefix());
   }
   c.runOnClient(mc->{var screen=(ProofScreen)mc.gui.screen();screen.setFocused(screen.primary);var event=new MouseButtonEvent((screen.primary.getX()+3)*screen.scale,(screen.primary.getY()+screen.primary.getHeight()/2d)*screen.scale,new MouseButtonInfo(0,0));if(!screen.mouseClicked(event,false)||screen.actions!=1)throw new AssertionError("Native button rejected its stable visible edge");screen.mouseReleased(event);screen.onCard=true;});
   ThemeConfig[] lit=new ThemeConfig[1];c.runOnClient(mc->{var path=VotexClient.INSTANCE.directory.resolve("theme.json");var theme=JsonParser.parseString(Files.readString(path)).getAsJsonObject();for(String flag:new String[]{"hover","proximityEffect","glassEffect","glowEffect","reflectionEffect"})theme.addProperty(flag,true);theme.addProperty("reducedMotion",false);theme.addProperty("shadow",80);AtomicJson.write(path,theme);lit[0]=readTheme(path);});c.waitFor(mc->VotexClient.INSTANCE.theme.equals(lit[0]),1000);
   long ready=System.nanoTime()+700_000_000L;c.waitFor(mc->System.nanoTime()>ready,1200);c.takeScreenshot(TestScreenshotOptions.of("hover-"+size[0]+"-card-and-focus").withDestinationDir(images).disableCounterPrefix());
  }
  c.runOnClient(mc->mc.gui.setScreen(null));return Map.of("cases",rows,"nativeVisibleEdgeActions",2,"scope","actual native widgets/sprite/font and theme-file watcher; scripted render pointer coordinates, no OS input or microphone");
 }
 private static ThemeConfig readTheme(Path path){try{return ThemeConfig.read(path);}catch(Exception error){throw new AssertionError("Invalid isolated hover theme",error);}}
 private static final class ProofScreen extends Screen {
  final VotexClient core;final HoverMotion panel=new HoverMotion();long sampled=System.nanoTime();float scale;int logicalW,logicalH,actions;boolean onCard;GlassButton primary;
  ProofScreen(VotexClient core){super(Component.literal("Votex hover render check"));this.core=core;}
  @Override protected void init(){var layout=WorkspaceLayout.measure(width,height,minecraft.getWindow().getGuiScale());scale=layout.scale();logicalW=layout.logicalWidth();logicalH=layout.logicalHeight();primary=addRenderableWidget(new GlassButton(core,28,76,280,44,"Aktion · Liquid Glass","",()->false,b->actions++).primary());addRenderableWidget(new GlassButton(core,28,134,280,58,"Mod Menu","Hover und Licht aus dem Launcher",()->true,b->{}).toggle());addRenderableWidget(new GlassButton(core,28,210,280,34,"Navigation","",()->false,b->{}).navigation().icon("modules"));addRenderableWidget(new WardrobeCard(core,330,76,180,135,Wardrobe.cosmetics().getFirst(),()->true,b->{}));}
  HoverMotion primaryState(){try{var f=GlassButton.class.getDeclaredField("interaction");f.setAccessible(true);return (HoverMotion)f.get(primary);}catch(Exception e){throw new AssertionError(e);}}
  @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){if(core.theme.blur())extractBlurredBackground(g);g.fill(0,0,width,height,0x58060b16);}
  @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){int px=onCard?420:250,py=onCard?138:98;long now=System.nanoTime();panel.step((now-sampled)/1e9,false,true,px,py,12,12,logicalW-24,logicalH-24,core.theme,true);sampled=now;g.pose().pushMatrix();g.pose().scale(scale);GlassRender.panel(g,12,12,logicalW-24,logicalH-24,core.theme);GlassRender.pointerLight(g,12,12,logicalW-24,logicalH-24,core.theme.radius(),core.theme,panel);UiText.title(g,"Votex · gemeinsames Design",28,30,core.theme.text(),1.35f);super.extractRenderState(g,px,py,d);UiText.draw(g,"Ausgelöste Aktionen: "+actions,28,logicalH-40,core.theme.muted());GlassRender.icon(g,"plus",px-4,py-4,8,0xfff7faff);g.pose().popMatrix();}
  @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){return super.mouseClicked(new MouseButtonEvent(event.x()/scale,event.y()/scale,event.buttonInfo()),twice);}
 }
}
