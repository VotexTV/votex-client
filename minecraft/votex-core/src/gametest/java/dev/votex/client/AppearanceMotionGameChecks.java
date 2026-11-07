package dev.votex.client;

import dev.votex.appearance.CapeAtlas;
import dev.votex.ui.WorkspaceMotion;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Actual appearance/crop rendering and events; no OS file picker or user's assets. */
final class AppearanceMotionGameChecks {
 static Map<String,Object> run(ClientGameTestContext c,Path images)throws Exception{
  var cases=new ArrayList<Map<String,Object>>();boolean oldMotion=c.computeOnClient(mc->VotexClient.INSTANCE.modules.config().menuAnimations);
  Path source=c.computeOnClient(mc->VotexClient.INSTANCE.directory.resolve("assets/skin-qa-votex.png"));byte[] original=Files.readAllBytes(source);
  try{
   for(int[] size:new int[][]{{768,480},{1280,720}})for(boolean motion:new boolean[]{true,false}){
    String label=size[0]+"-"+(motion?"opening":"no-motion");c.getInput().resizeWindow(size[0],size[1]);c.waitTicks(3);
    c.runOnClient(mc->{VotexClient.INSTANCE.modules.config().menuAnimations=motion;mc.gui.setScreen(new AppearanceScreen(VotexClient.INSTANCE,new VotexScreen(VotexClient.INSTANCE,"COSMETICS")));if(motion)set(mc.gui.screen(),"opened",System.nanoTime()+10_000_000_000L);edit(mc.gui.screen()).setValue("QA äöü · "+label);});
    c.waitTicks(3);shot(c,images,"look-motion-"+label);
    c.runOnClient(mc->{var s=mc.gui.screen();var frame=frame(s);checkFrame(frame,motion);var save=button(s,"Nametag speichern");var event=edge(s,save,motion);if(motion)set(s,"opened",0L);if(!s.mouseClicked(event,false)||!LocalLooks.text("nametag","").equals("QA äöü · "+label))throw new AssertionError("Visible animated save edge missed or saved wrong name");s.mouseReleased(event);
     var preview=(AvatarPreview)field(s,"preview");int x1=number(preview,"x1"),y1=number(preview,"y1"),x2=number(preview,"x2"),y2=number(preview,"y2");if(x1!=Math.round(frame.screenX(number(s,"x")+number(s,"formWidth")+56)*scale(s))||x2!=Math.round(frame.screenX(number(s,"x")+number(s,"w")-32)*scale(s)))throw new AssertionError("Avatar viewport did not follow displayed panel");
     float worldYaw=mc.player.getYRot(),worldPitch=mc.player.getXRot(),yaw=((Number)field(preview,"yaw")).floatValue(),pitch=((Number)field(preview,"pitch")).floatValue();double dx=18*scale(s)*frame.scale(),dy=8*scale(s)*frame.scale();var start=new MouseButtonEvent((x1+x2)/2d,(y1+y2)/2d,new MouseButtonInfo(0,0));var end=new MouseButtonEvent(start.x()+dx,start.y()+dy,start.buttonInfo());if(!s.mouseClicked(start,false)||!s.mouseDragged(end,dx,dy)||!s.mouseReleased(end))throw new AssertionError("Displayed avatar viewport rejected orbit input");if(Math.abs(((Number)field(preview,"yaw")).floatValue()-yaw-18*.7)>.001||Math.abs(((Number)field(preview,"pitch")).floatValue()-pitch-8*.35)>.001)throw new AssertionError("Avatar drag did not normalize displayed scale");
     if(!s.mouseScrolled(start.x(),start.y(),0,2)||mc.player.getYRot()!=worldYaw||mc.player.getXRot()!=worldPitch)throw new AssertionError("Preview zoom failed or changed the world camera");edit(s).setValue("Nicht gespeicherter Entwurf ✓");});
    c.waitTicks(2);click(c,"+");c.runOnClient(mc->{if(!edit(mc.gui.screen()).getValue().equals("Nicht gespeicherter Entwurf ✓"))throw new AssertionError("Scale rebuild lost draft");mc.gui.setScreen(new CapeCropScreen(VotexClient.INSTANCE,mc.gui.screen(),source));if(motion)set(mc.gui.screen(),"opened",System.nanoTime()+10_000_000_000L);});
    c.waitTicks(3);shot(c,images,"cape-motion-"+label);
    c.runOnClient(mc->{var s=mc.gui.screen();var f=frame(s);checkFrame(f,motion);double before=((Number)field(s,"zoom")).doubleValue();var outside=point(s,number(s,"x")+20,number(s,"y")+20);if(s.mouseScrolled(outside.x(),outside.y(),0,2)||((Number)field(s,"zoom")).doubleValue()!=before)throw new AssertionError("Wheel outside crop changed zoom");
     var start=point(s,number(s,"fx")+number(s,"fw")*.5,number(s,"fy")+number(s,"fh")*.5);if(!s.mouseScrolled(start.x(),start.y(),0,2))throw new AssertionError("Visible crop rejected wheel");double zoom=((Number)field(s,"zoom")).doubleValue();if(Math.abs(zoom-Math.pow(1.15,2))>.00001)throw new AssertionError("Wrong crop zoom");double width=CapeAtlas.crop(64,64,10d/16,zoom,.5,.5).width();double dx=12*scale(s)*f.scale(),dy=6*scale(s)*f.scale();if(motion)set(s,"opened",0L);var end=new MouseButtonEvent(start.x()+dx,start.y()+dy,start.buttonInfo());if(!s.mouseClicked(start,false)||!s.mouseDragged(end,dx,dy)||!s.mouseReleased(end))throw new AssertionError("Visible animated crop rejected drag");if(Math.abs(((Number)field(s,"cx")).doubleValue()-(.5-12*width/number(s,"fw")/64))>.00001||Math.abs(((Number)field(s,"cy")).doubleValue()-(.5-6*width/number(s,"fw")/64))>.00001)throw new AssertionError("Crop movement ignored motion/density scale");
    });
    c.waitTicks(2);shot(c,images,"cape-motion-panned-"+label);click(c,"Cape übernehmen");
    c.runOnClient(mc->{if(!(mc.gui.screen() instanceof AppearanceScreen)||!edit(mc.gui.screen()).getValue().equals("Nicht gespeicherter Entwurf ✓"))throw new AssertionError("Cape return lost appearance draft");var path=VotexClient.INSTANCE.directory.resolve("assets/cape-"+LocalLooks.assetCapeId()+".png");var image=javax.imageio.ImageIO.read(path.toFile());if(image.getWidth()!=64||image.getHeight()!=32)throw new AssertionError("Cape export dimensions changed");image.flush();});
    if(!Arrays.equals(original,Files.readAllBytes(source)))throw new AssertionError("Source PNG modified");
    cases.add(Map.of("width",size[0],"height",size[1],"motion",motion,"displayedEdgeSavesName",true,"avatarFollowsPanelAndPreservesCamera",true,"densityNormalizedOrbitAndCropDrag",true,"outsideWheelIgnored",true,"draftSurvivesRebuildAndCapeReturn",true,"realLocal64x32Export",true));
   }
   return Map.of("cases",cases,"sourceUnchanged",true,"scope","private QA PNGs and local-only appearance saves; actual native renderer/events, no OS picker, network publication, account or microphone");
  }finally{c.runOnClient(mc->{VotexClient.INSTANCE.modules.config().menuAnimations=oldMotion;mc.gui.setScreen(null);});}
 }
 private static void checkFrame(WorkspaceMotion.Frame f,boolean motion){if(motion?f.scale()>=.99:f.scale()!=1||f.x()!=0||f.y()!=0)throw new AssertionError("Unexpected displayed motion frame");}
 private static MouseButtonEvent edge(Screen s,Button b,boolean requireOldMiss){if(requireOldMiss){for(double x:new double[]{b.getX()+.5,b.getRight()-.5})for(double y:new double[]{b.getY()+.5,b.getBottom()-.5})if(!b.isMouseOver(frame(s).screenX(x),frame(s).screenY(y)))return point(s,x,y);throw new AssertionError("Opening-edge fixture would not catch missing inverse");}return point(s,b.getX()+b.getWidth()/2d,b.getY()+b.getHeight()/2d);}
 private static MouseButtonEvent point(Screen s,double x,double y){var f=frame(s);return new MouseButtonEvent(f.screenX(x)*scale(s),f.screenY(y)*scale(s),new MouseButtonInfo(0,0));}
 private static double scale(Screen s){return ((Number)field(s,s instanceof CapeCropScreen?"uiScale":"scale")).doubleValue();}
 private static WorkspaceMotion.Frame frame(Screen s){return (WorkspaceMotion.Frame)field(s,"displayedMotion");}
 private static EditBox edit(Screen s){return s.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();}
 private static Button button(Screen s,String title){return s.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(title)).findFirst().orElseThrow();}
 private static void click(ClientGameTestContext c,String title){c.runOnClient(mc->{var s=mc.gui.screen();var b=button(s,title);var e=point(s,b.getX()+b.getWidth()/2d,b.getY()+b.getHeight()/2d);if(!s.mouseClicked(e,false))throw new AssertionError("Native button missed "+title);s.mouseReleased(e);});c.waitTicks(2);}
 private static int number(Object o,String name){return ((Number)field(o,name)).intValue();}
 private static Object field(Object o,String name){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
 private static void set(Object o,String name,Object value){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new AssertionError(e);}}
 private static void shot(ClientGameTestContext c,Path images,String name){c.takeScreenshot(TestScreenshotOptions.of("votex-goal-"+name).withDestinationDir(images).disableCounterPrefix());}
}
