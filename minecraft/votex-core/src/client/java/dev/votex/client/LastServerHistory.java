package dev.votex.client;

import dev.votex.config.RecentServerRecord;
import java.time.Instant;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Records only a completed Minecraft play connection, never a failed connect attempt. */
final class LastServerHistory {
 private static boolean initialized;
 private LastServerHistory(){}
 static void init(){if(initialized)return;initialized=true;ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->{
  var core=VotexClient.INSTANCE;var server=client.getCurrentServer();String profile=System.getProperty("votex.profileId","");
  if(core==null||server==null||client.getSingleplayerServer()!=null||profile.isEmpty())return;
  try{core.engine.saveLastServer(RecentServerRecord.create(profile,server.name,server.ip,Instant.now()));}
  catch(IllegalArgumentException rejected){VotexClient.LOG.warning("Letzter Server konnte nicht lokal gespeichert werden: "+rejected.getMessage());}
 });}
}
