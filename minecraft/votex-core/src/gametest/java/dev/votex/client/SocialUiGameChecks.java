package dev.votex.client;

import dev.votex.social.SocialPayload;
import dev.votex.social.SocialRules;
import dev.votex.ui.WorkspaceMotion;
import java.nio.file.Path;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Synthetic peer snapshots and native dialog cancellation only. Never grants device access. */
final class SocialUiGameChecks {
 static Map<String,Object> run(ClientGameTestContext c,Path images){
  var cases=new ArrayList<Map<String,Object>>();var peers=new ArrayList<UUID>();
  boolean oldMotion=c.computeOnClient(mc->VotexClient.INSTANCE.modules.config().menuAnimations);
  var original=c.computeOnClient(mc->new LinkedHashMap<>(peerMap()));
  boolean permission=c.computeOnClient(mc->VoiceMicGate.allowed());
  try{
   c.getInput().resizeWindow(768,480);c.waitTicks(3);c.runOnClient(mc->{mc.options.guiScale().set(2);mc.getWindow().setGuiScale(2);peerMap().clear();VotexClient.INSTANCE.modules.config().menuAnimations=false;mc.gui.setScreen(new VotexScreen(VotexClient.INSTANCE,"FRIENDS"));});
   c.waitTicks(2);
   c.runOnClient(mc->{var screen=mc.gui.screen();for(int i=0;i<24;i++){UUID id=UUID.randomUUID();peers.add(id);var p=SocialRules.message("peer");p.addProperty("player",id.toString());p.addProperty("name",String.format(Locale.ROOT,"Peer%02d",i));deliver(p);}screen.tick();if(mc.gui.screen()!=screen||((List<?>)field(screen,"socialPeers")).size()!=24||buttons(screen).stream().noneMatch(b->b.getMessage().getString().startsWith("Peer00")))throw new AssertionError("Open friends screen did not rebuild on peer arrival");});
   c.waitTicks(2);shot(c,images,"friends-live-arrival");
   var visited=new HashSet<String>();
   for(int step=0;step<100;step++){
    boolean done=c.computeOnClient(mc->{var screen=mc.gui.screen();int start=(Integer)field(screen,"scrollViewportTop"),end=(Integer)field(screen,"scrollViewportBottom");for(var b:buttons(screen))if(b.visible&&b.getY()>=start&&b.getBottom()<=end&&b.getMessage().getString().startsWith("Peer"))visited.add(b.getMessage().getString().substring(0,6));return ((Number)field(screen,"pageScroll")).floatValue()>=(Integer)field(screen,"h")-(Integer)field(screen,"panelHeight")-.05f;});
    if(done)break;scroll(c,-.5);
   }
   if(visited.size()!=24)throw new AssertionError("Friends scrolling left peers unreachable: "+visited);
   shot(c,images,"friends-scroll-end");
   c.runOnClient(mc->{var screen=mc.gui.screen();if(button(screen,"Voice-Chat öffnen").getY()!=(Integer)field(screen,"top")+(Integer)field(screen,"panelHeight")-154)throw new AssertionError("Friends footer moved with peer list");set(screen,"windowEdge",1);var p=SocialRules.message("remove");p.addProperty("player",peers.getFirst().toString());deliver(p);screen.tick();if(((List<?>)field(screen,"socialPeers")).size()!=24)throw new AssertionError("Peer change rebuilt widgets mid-window drag");set(screen,"windowEdge",0);screen.tick();if(((List<?>)field(screen,"socialPeers")).size()!=23)throw new AssertionError("Deferred peer change was lost");for(UUID peer:peers){p=SocialRules.message("remove");p.addProperty("player",peer.toString());deliver(p);}screen.tick();if(((Number)field(screen,"scrollTarget")).floatValue()!=0||buttons(screen).stream().anyMatch(b->b.getMessage().getString().startsWith("Peer")))throw new AssertionError("Departed peers or invalid scroll target retained");if(!button(screen,"Welt im LAN öffnen").active)throw new AssertionError("Integrated world lost its LAN action");});
   c.waitTicks(2);shot(c,images,"friends-live-departure");
   for(int[] size:new int[][]{{768,480},{1280,720}})for(boolean motion:new boolean[]{true,false})for(boolean consent:new boolean[]{true,false}){
    c.getInput().resizeWindow(size[0],size[1]);c.waitTicks(2);
    c.runOnClient(mc->{VotexClient.INSTANCE.modules.config().menuAnimations=motion;var parent=new VotexScreen(VotexClient.INSTANCE,"FRIENDS");Screen s=consent?new VoiceConsentScreen(VotexClient.INSTANCE,parent):new VoiceSetupScreen(VotexClient.INSTANCE,parent);mc.gui.setScreen(s);if(motion)set(s,"opened",System.nanoTime()+10_000_000_000L);});
    c.waitTicks(3);shot(c,images,(consent?"voice-consent":"voice-setup")+"-"+size[0]+"-"+(motion?"opening":"no-motion"));
    cases.add(c.computeOnClient(mc->{Screen screen=mc.gui.screen();var frame=(WorkspaceMotion.Frame)field(screen,"displayedMotion");float scale=((Number)field(screen,"scale")).floatValue();var back=button(screen,consent?"Später":"Zurück");double px=back.getX()+back.getWidth()/2d,py=back.getY()+back.getHeight()/2d;boolean oldMiss=!motion;
     if(motion){if(frame.scale()>=.99)throw new AssertionError("Opening frame did not render");for(double x:new double[]{back.getX()+.5,back.getRight()-.5})for(double y:new double[]{back.getY()+.5,back.getBottom()-.5})if(!back.isMouseOver(frame.screenX(x),frame.screenY(y))){px=x;py=y;oldMiss=true;}if(!oldMiss)throw new AssertionError("Fixture click would not catch old untransformed hit");set(screen,"opened",0L);}else if(frame.scale()!=1||frame.x()!=0||frame.y()!=0)throw new AssertionError("Disabled motion still transformed input");
     Screen parent=(Screen)field(screen,"parent");if(!screen.mouseClicked(new MouseButtonEvent(frame.screenX(px)*scale,frame.screenY(py)*scale,new MouseButtonInfo(0,0)),false)||mc.gui.screen()!=parent)throw new AssertionError("Displayed dialog edge did not cancel to parent");if(VoiceMicGate.allowed()!=permission)throw new AssertionError("Cancel changed microphone permission");return Map.<String,Object>of("width",size[0],"height",size[1],"consent",consent,"motion",motion,"cancelAtDisplayedFrame",true,"clockAdvancedBeforeInput",motion,"permissionUnchanged",true);
    }));
   }
   return Map.of("liveArrivalAndDeparture",true,"all24PeersFullyReachableByScroll",true,"rebuildDeferredUntilWindowDragEnds",true,"departureClampsScroll",true,"footerPinned",true,"integratedWorldLanEnabled",true,"dialogCases",cases,"scope","synthetic peer packets into native receive/tick and actual rendered buttons; no external server, account, OS automation or microphone capture");
  }finally{c.runOnClient(mc->{peerMap().clear();peerMap().putAll(original);VotexClient.INSTANCE.modules.config().menuAnimations=oldMotion;mc.gui.setScreen(null);});}
 }
 private static List<Button> buttons(Screen s){return s.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();}
 private static Button button(Screen s,String name){return buttons(s).stream().filter(b->b.getMessage().getString().equals(name)).findFirst().orElseThrow(()->new AssertionError("Missing native button "+name));}
 private static void scroll(ClientGameTestContext c,double amount){c.runOnClient(mc->{var screen=mc.gui.screen();var frame=(WorkspaceMotion.Frame)field(screen,"displayedMotion");double scale=((Number)field(screen,"uiScale")).doubleValue(),x=(Integer)field(screen,"content")+(Integer)field(screen,"cw")/2d,y=((Integer)field(screen,"scrollViewportTop")+(Integer)field(screen,"scrollViewportBottom"))/2d;if(!screen.mouseScrolled(frame.screenX(x)*scale,frame.screenY(y)*scale,0,amount))throw new AssertionError("Friends scroll input was rejected");});c.waitTicks(2);}
 @SuppressWarnings("unchecked") private static Map<UUID,VotexSocial.Peer> peerMap(){return (Map<UUID,VotexSocial.Peer>)field(VotexSocial.class,"PEERS");}
 private static void deliver(com.google.gson.JsonObject packet){try{var m=VotexSocial.class.getDeclaredMethod("receive",SocialPayload.class);m.setAccessible(true);m.invoke(null,new SocialPayload(packet.toString()));}catch(Exception e){throw new AssertionError(e);}}
 private static Object field(Object o,String name){try{var f=(o instanceof Class<?> type?type:o.getClass()).getDeclaredField(name);f.setAccessible(true);return f.get(o instanceof Class<?>?null:o);}catch(Exception e){throw new AssertionError(e);}}
 private static void set(Object o,String name,Object value){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new AssertionError(e);}}
 private static void shot(ClientGameTestContext c,Path images,String name){c.takeScreenshot(TestScreenshotOptions.of("votex-goal-"+name).withDestinationDir(images).disableCounterPrefix());}
}
