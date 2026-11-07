package dev.votex.client;

import dev.votex.ui.WorkspaceMotion;
import java.nio.file.Path;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Controlled first animation frame, rendered by Minecraft; no operating-system input automation. */
final class WorkspaceMotionGameChecks {
 static Map<String,Object> run(ClientGameTestContext context,Path images){
  context.runOnClient(mc->{var s=new VotexScreen(VotexClient.INSTANCE,"HOME");mc.gui.setScreen(s);set(s,"opened",System.nanoTime()+10_000_000_000L);});
  context.waitTicks(3);
  context.takeScreenshot(TestScreenshotOptions.of("votex-goal-workspace-opening-input-frame").withDestinationDir(images).disableCounterPrefix());
  var proof=context.computeOnClient(mc->{
   var screen=mc.gui.screen();var frame=(WorkspaceMotion.Frame)field(screen,"displayedMotion");
   if(frame.scale()>=.99f)throw new AssertionError("Opening-frame input fixture did not render");
   var button=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals("Mod Menu öffnen")).findFirst().orElseThrow();
   double scale=((Number)field(screen,"uiScale")).doubleValue(),x=0,y=0;boolean found=false;
   for(double bx:new double[]{button.getX()+.75,button.getRight()-.75})for(double by:new double[]{button.getY()+.75,button.getBottom()-.75}){
    double px=frame.screenX(bx),py=frame.screenY(by);
    if(!button.isMouseOver(px,py)){x=px*scale;y=py*scale;found=true;}
   }
   if(!found)throw new AssertionError("Click would also pass without animation inversion");
   var event=new MouseButtonEvent(x,y,new MouseButtonInfo(0,0));
   if(!screen.mouseClicked(event,false)||!(mc.gui.screen() instanceof VotexScreen)||!field(mc.gui.screen(),"page").equals("MODULES"))throw new AssertionError("Visible animated button edge did not open Mod Menu");
   return Map.<String,Object>of("scale",frame.scale(),"rawX",x,"rawY",y,"oldUntransformedHitWouldMiss",true,"visibleButtonEdgeOpenedModules",true,"clockFixture","first animation frame held for render/input; actual native widgets");
  });
  context.runOnClient(mc->mc.gui.setScreen(null));var result=new java.util.LinkedHashMap<String,Object>(proof);result.put("libraryFrames",libraryFrames(context,images));return result;
 }
 private static java.util.List<Map<String,Object>> libraryFrames(ClientGameTestContext c,Path images){
  var cases=new java.util.ArrayList<Map<String,Object>>();boolean prior=c.computeOnClient(mc->VotexClient.INSTANCE.modules.config().menuAnimations);
  try{for(int[] size:new int[][]{{768,480},{1280,720}})for(boolean motion:new boolean[]{true,false}){
   c.getInput().resizeWindow(size[0],size[1]);c.waitTicks(2);
   c.runOnClient(mc->{VotexClient.INSTANCE.modules.config().menuAnimations=motion;var s=new ModLibraryScreen(VotexClient.INSTANCE,new VotexScreen(VotexClient.INSTANCE,"MODULES"));mc.gui.setScreen(s);if(motion)set(s,"opened",System.nanoTime()+10_000_000_000L);});
   c.waitFor(mc->!((Boolean)field(mc.gui.screen(),"busy")),1000);c.waitTicks(3);
   c.takeScreenshot(TestScreenshotOptions.of("votex-goal-library-opening-"+size[0]+"-"+(motion?"motion":"static")).withDestinationDir(images).disableCounterPrefix());
   cases.add(c.computeOnClient(mc->{var s=mc.gui.screen();var f=(WorkspaceMotion.Frame)field(s,"displayedMotion");if(motion?f.scale()>=.99:f.scale()!=1||f.x()!=0||f.y()!=0)throw new AssertionError("Library motion did not match setting");var b=s.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(button->button.getMessage().getString().equals("‹ Zurück")).findFirst().orElseThrow();double x=b.getX()+b.getWidth()/2d,y=b.getY()+b.getHeight()/2d;
    if(motion){boolean missed=false;for(double xx:new double[]{b.getX()+.5,b.getRight()-.5})for(double yy:new double[]{b.getY()+.5,b.getBottom()-.5})if(!b.isMouseOver(f.screenX(xx),f.screenY(yy))){x=xx;y=yy;missed=true;}if(!missed)throw new AssertionError("Library edge fixture would pass without inverse");set(s,"opened",0L);}
    double scale=((Number)field(s,"scale")).doubleValue();if(!s.mouseClicked(new MouseButtonEvent(f.screenX(x)*scale,f.screenY(y)*scale,new MouseButtonInfo(0,0)),false)||!(mc.gui.screen() instanceof VotexScreen)||!field(mc.gui.screen(),"page").equals("MODULES"))throw new AssertionError("Displayed library edge did not return to parent");return Map.<String,Object>of("width",size[0],"height",size[1],"motion",motion,"visibleEdgeReturn",true,"clockAdvancedBeforeInput",motion);
   }));
  }return cases;}finally{c.runOnClient(mc->{VotexClient.INSTANCE.modules.config().menuAnimations=prior;mc.gui.setScreen(null);});}
 }
 private static Object field(Object o,String name){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
 private static void set(Object o,String name,Object value){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new AssertionError(e);}}
}
