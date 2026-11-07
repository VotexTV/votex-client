package dev.votex.client;
import com.google.gson.*;
import dev.votex.social.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.server.MinecraftServer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Actual Fabric packets through the existing isolated integrated-server connection. No extra game process. */
public final class SocialRelayGameChecks {
 private SocialRelayGameChecks(){}
 public static Map<String,Object> run(ClientGameTestContext context){
  var serverRef=new AtomicReference<MinecraftServer>();var idRef=new AtomicReference<UUID>();var original=new AtomicReference<JsonObject>();
  context.waitFor(mc->VotexSocial.available(),1000);
  context.runOnClient(mc->{if(mc.getSingleplayerServer()==null||mc.player==null)throw new AssertionError("Social QA needs an isolated integrated world");serverRef.set(mc.getSingleplayerServer());idRef.set(mc.player.getUUID());original.set(LocalLooks.look.deepCopy());JsonObject look=new JsonObject(),equipment=new JsonObject();equipment.addProperty("wings","cloud-feathers");look.add("equipment",equipment);look.addProperty("scale",100);LocalLooks.look=look;LocalLooks.revision++;EmotePlayer.stop();});
  try{
   context.waitFor(mc->VotexSocial.appearanceAcknowledged(),1000);
   List<VotexRelay.Snapshot> initial=snapshot(context,serverRef.get());var self=initial.stream().filter(e->e.player().equals(idRef.get())).findFirst().orElseThrow(()->new AssertionError("Actual connection UUID missing in relay"));if(self.appearanceHash().length()!=64)throw new AssertionError("No validated appearance hash");
   UUID forged=UUID.randomUUID();context.runOnClient(mc->{var packet=SocialRules.message("emote");packet.addProperty("player",forged.toString());packet.addProperty("id","wave");ClientPlayNetworking.send(new SocialPayload(packet.toString()));});
   context.waitTicks(10);var active=snapshot(context,serverRef.get());if(active.stream().anyMatch(e->e.player().equals(forged)))throw new AssertionError("Client spoofed relay identity");if(active.stream().noneMatch(e->e.player().equals(idRef.get())&&e.emote().equals("wave")))throw new AssertionError("Real C2S emote receiver did not use connection UUID");
   context.runOnClient(mc->{var packet=SocialRules.message("emote");packet.addProperty("id","handshake");ClientPlayNetworking.send(new SocialPayload(packet.toString()));});context.waitTicks(5);if(snapshot(context,serverRef.get()).stream().anyMatch(e->e.emote().equals("handshake")))throw new AssertionError("Handshake bypassed recipient consent");
   context.runOnClient(mc->ClientPlayNetworking.send(new SocialPayload(SocialRules.message("stop").toString())));context.waitTicks(5);if(snapshot(context,serverRef.get()).stream().anyMatch(e->!e.emote().isEmpty()))throw new AssertionError("Real C2S stop did not clear server emote");
   return Map.of("kind","one-real-client-integrated-Fabric-network","helloWelcome",true,"chunkedAppearanceAck",true,"connectionUuidOverridesForgedJson",true,"handshakeRequiresConsent",true,"stopPacket",true,"twoClientRenderedSync","not-verified-by-this-check");
  }finally{context.runOnClient(mc->{LocalLooks.look=original.get();LocalLooks.revision++;EmotePlayer.stop();});}
 }
 private static List<VotexRelay.Snapshot> snapshot(ClientGameTestContext context,MinecraftServer server){var future=server.submit(()->VotexRelay.diagnostics(server));context.waitFor(mc->future.isDone(),1000);return future.join();}
}
