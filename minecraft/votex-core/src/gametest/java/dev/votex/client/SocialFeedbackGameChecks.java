package dev.votex.client;

import com.google.gson.JsonObject;
import dev.votex.social.SocialFeedback;
import dev.votex.social.SocialPayload;
import dev.votex.social.SocialRules;
import dev.votex.ui.SocialToastLayout;
import java.nio.file.Path;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

/** Native glass/text rendering; synthetic packet delivery is explicitly not a network proof. */
final class SocialFeedbackGameChecks {
 static Map<String,Object> run(ClientGameTestContext c,Path images){
  UUID peer=UUID.fromString("a5b05624-0547-4c00-85f2-1ecac88b5a6a");var sizes=new ArrayList<Map<String,Object>>();
  boolean[] oldMotion=new boolean[1];c.runOnClient(mc->{oldMotion[0]=VotexClient.INSTANCE.modules.config().menuAnimations;mc.gui.setScreen(null);});
  try{
   for(int[] size:new int[][]{{768,480,1},{768,480,3},{1280,720,2},{1920,1080,3}}){
    c.getInput().resizeWindow(size[0],size[1]);c.waitTicks(3);
    c.runOnClient(mc->{mc.options.guiScale().set(size[2]);mc.getWindow().setGuiScale(size[2]);VotexClient.INSTANCE.modules.config().menuAnimations=true;
     var packet=SocialRules.message("invitation");packet.addProperty("token",UUID.randomUUID().toString());packet.addProperty("from",peer.toString());packet.addProperty("name","QACreator");packet.addProperty("expiresIn",10_000);deliver(packet);
     var box=SocialToastLayout.measure(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),mc.getWindow().getGuiScale(),2);
     sizes.add(Map.of("physicalWidth",size[0],"physicalHeight",size[1],"guiScale",size[2],"bodyPixels",11*box.scale()*size[2],"height",box.height()));
    });
    c.waitTicks(8);shot(c,images,"social-invitation-"+size[0]+"x"+size[1]+"-gui"+size[2]);
    c.runOnClient(mc->{if(!VotexSocial.pendingInvitation().contains("G zum Annehmen"))throw new AssertionError("Consent instruction absent");});
   }
   c.runOnClient(mc->{reset();feedback().invitation(peer,"Einladung verschickt · dein Gegenüber bestätigt mit G",System.currentTimeMillis());});c.waitTicks(8);shot(c,images,"social-outgoing-before-accept");
   c.runOnClient(mc->{var packet=SocialRules.message("emote");packet.addProperty("player",mc.player.getUUID().toString());packet.addProperty("id","handshake");packet.addProperty("peer",peer.toString());packet.addProperty("startTick",mc.level.getGameTime());deliver(packet);VotexSocial.tick();if(!feedback().current(System.currentTimeMillis()).isEmpty())throw new AssertionError("Accepted scheduled handshake left outgoing invitation behind");});
   c.waitTicks(2);shot(c,images,"social-outgoing-cleared");
   c.runOnClient(mc->{VotexClient.INSTANCE.modules.config().menuAnimations=false;VotexSocial.feedback("QA · Kein freier Installationsplatz. Bitte Speicherplatz prüfen und erneut versuchen.");});
   c.waitTicks(2);shot(c,images,"social-feedback-reduced-motion");
   c.runOnClient(mc->{feedback().accepted(peer);if(feedback().current(System.currentTimeMillis()).isEmpty())throw new AssertionError("A handshake erased unrelated feedback");reset();if(!feedback().current(System.currentTimeMillis()).isEmpty()||!VotexSocial.pendingInvitation().isEmpty())throw new AssertionError("Disconnect left transient social feedback behind");});
   return Map.of("nativeSizeCases",sizes,"acceptedScheduledPacketClearsOnlyInvitation",true,"unrelatedFeedbackRetained",true,"connectionResetClearsFeedback",true,"reducedMotionRendered",true,"scope","synthetic packets injected into actual client receive/tick path; no second player, network handshake or microphone assertion");
  }finally{c.runOnClient(mc->{reset();VotexClient.INSTANCE.modules.config().menuAnimations=oldMotion[0];});}
 }
 private static SocialFeedback feedback(){try{var f=VotexSocial.class.getDeclaredField("FEEDBACK");f.setAccessible(true);return (SocialFeedback)f.get(null);}catch(Exception e){throw new AssertionError(e);}}
 private static void deliver(JsonObject packet){try{var m=VotexSocial.class.getDeclaredMethod("receive",SocialPayload.class);m.setAccessible(true);m.invoke(null,new SocialPayload(packet.toString()));}catch(Exception e){throw new AssertionError(e);}}
 private static void reset(){try{var m=VotexSocial.class.getDeclaredMethod("clear");m.setAccessible(true);m.invoke(null);}catch(Exception e){throw new AssertionError(e);}}
 private static void shot(ClientGameTestContext c,Path images,String name){c.takeScreenshot(TestScreenshotOptions.of("votex-goal-"+name).withDestinationDir(images).disableCounterPrefix());}
}
