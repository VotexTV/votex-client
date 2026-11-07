package dev.votex.social;

import com.google.gson.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.concurrent.*;

/** Optional same-server relay. UUID/name always come from the authenticated game connection. */
public final class VotexRelay {
 private static final Map<MinecraftServer,Session> SERVERS=new IdentityHashMap<>();
 private static final ThreadPoolExecutor CONTENT=new ThreadPoolExecutor(1,1,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->{Thread t=new Thread(r,"Votex relay validation");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 private static boolean initialized;
 private VotexRelay(){}
 /** Immutable local server diagnostics; call on the server thread. No network diagnostics endpoint exists. */
 public record Snapshot(UUID player,String appearanceHash,String emote,int knownPeers){}
 public static List<Snapshot> diagnostics(MinecraftServer server){Session session=SERVERS.get(server);if(session==null)return List.of();return session.members.entrySet().stream().map(e->new Snapshot(e.getKey(),e.getValue().hash,e.getValue().emote,e.getValue().known.size())).toList();}
 public static synchronized void init(){if(initialized)return;initialized=true;SocialPayload.register();
  ServerPlayNetworking.registerGlobalReceiver(SocialPayload.TYPE,(payload,context)->context.server().execute(()->SERVERS.computeIfAbsent(context.server(),s->new Session()).receive(context.server(),context.player(),payload)));
  ServerTickEvents.END_SERVER_TICK.register(server->{var session=SERVERS.get(server);if(session!=null)session.tick(server);});
  ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{var session=SERVERS.get(server);if(session!=null)session.remove(server,handler.player.getUUID());});
  ServerLifecycleEvents.SERVER_STOPPED.register(SERVERS::remove);
 }
 private static final class Member {
  final SocialRules.Limiter rate;final SocialRules.Transfer transfer=new SocialRules.Transfer();final Map<UUID,String> known=new HashMap<>();final Map<String,JsonObject> custom=new LinkedHashMap<>();
  JsonObject appearance;List<JsonObject> chunks=List.of();String hash="";long lastAppearance=-10000,lastEmote=-10000;String emote="";long emoteStart,emoteEnd;double speed=1;boolean loop;UUID handshakePeer;
  Member(long now){rate=new SocialRules.Limiter(48,8,now);}
 }
 private static final class Session {
  final Map<UUID,Member> members=new LinkedHashMap<>();final HandshakeBook invitations=new HandshakeBook();
  void receive(MinecraftServer server,ServerPlayer player,SocialPayload payload){long now=server.getTickCount()*50L;try{
   JsonObject p=SocialRules.parse(payload.json());String type=SocialRules.text(p,"type",24);UUID id=player.getUUID();
   if(type.equals("hello")){if(!ServerPlayNetworking.canSend(player,SocialPayload.TYPE))return;if(!members.containsKey(id)){if(members.size()>=SocialRules.MAX_PEERS){error(player,"Der Votex-Relay ist ausgelastet.");return;}members.put(id,new Member(now));}if(!members.get(id).rate.take(now,4))return;JsonObject response=SocialRules.message("welcome");response.addProperty("authenticated",server.usesAuthentication());send(player,response);return;}
   Member member=members.get(id);if(member==null||!member.rate.take(now,type.equals("appearance-part")?1:2))return;
   switch(type){
    case "appearance-part"->{if(now-member.lastAppearance<1000)return;byte[] packed=member.transfer.acceptPacked(p,now);if(packed!=null){member.lastAppearance=now;CONTENT.execute(()->{try{JsonObject look=SocialRules.unpack(packed);List<JsonObject> chunks=SocialRules.chunks(look);server.execute(()->{if(SERVERS.get(server)!=this||members.get(id)!=member)return;member.appearance=look;member.chunks=chunks;member.hash=chunks.getFirst().get("hash").getAsString();JsonObject ack=SocialRules.message("appearance-ack");ack.addProperty("hash",SocialRules.hash(packed));send(player,ack);refresh(server);});}catch(RuntimeException invalid){server.execute(()->error(player,"Der geteilte Look überschreitet ein Format- oder Inhaltslimit."));}});}}
    case "emote"->{String emote=SocialRules.text(p,"id",64);if(!SocialRules.emote(emote)||emote.equals("handshake")||now-member.lastEmote<400||!still(player))return;if(emote.startsWith("custom-")){if(p.has("definition")){JsonObject definition=p.getAsJsonObject("definition");SocialRules.validateEmote(definition);if(!definition.get("id").getAsString().equals(emote))return;member.custom.put(emote,definition.deepCopy());while(member.custom.size()>8)member.custom.remove(member.custom.keySet().iterator().next());}if(!hasEmote(member,emote))return;}double speed=p.has("speed")?p.get("speed").getAsDouble():1;if(!Double.isFinite(speed)||speed<.5||speed>2)return;member.speed=speed;member.loop=p.has("loop")&&p.get("loop").getAsBoolean();member.emote=emote;member.emoteStart=player.level().getGameTime()+3;member.emoteEnd=member.emoteStart+(member.loop?600:(long)Math.ceil((duration(member,emote)/speed+2)*20));member.handshakePeer=null;member.lastEmote=now;event(server,player,member);}
    case "stop"->{stop(server,player,member);}
    case "invite"->{UUID targetId=SocialRules.uuid(p,"target");ServerPlayer target=server.getPlayerList().getPlayer(targetId);if(target==null||!members.containsKey(targetId)||!eligible(player,target)){error(player,"Für einen Handschlag müssen beide Spieler nah beieinander und in Ruhe sein.");return;}var invitation=invitations.invite(id,targetId,now);if(invitation==null){error(player,"Bitte warte kurz mit der nächsten Einladung.");return;}var notice=SocialRules.message("invitation");notice.addProperty("token",invitation.token().toString());notice.addProperty("from",id.toString());notice.addProperty("name",player.getGameProfile().name());notice.addProperty("expiresIn",10_000);send(target,notice);}
    case "accept"->{var accepted=invitations.accept(id,SocialRules.uuid(p,"token"),now);if(accepted==null)return;ServerPlayer other=server.getPlayerList().getPlayer(accepted.from());if(other==null||!members.containsKey(other.getUUID())||!eligible(player,other)){error(player,"Die Einladung ist nicht mehr gültig.");return;}Member a=members.get(other.getUUID());member.emote=a.emote="handshake";member.speed=a.speed=1;member.loop=a.loop=false;member.emoteStart=a.emoteStart=player.level().getGameTime()+5;member.emoteEnd=a.emoteEnd=member.emoteStart+86;member.handshakePeer=other.getUUID();a.handshakePeer=id;event(server,other,a);event(server,player,member);}
    case "leave"->remove(server,id);
    default->{}
   }
  }catch(RuntimeException rejected){/* Malformed optional content is dropped; it cannot execute or disconnect other players. */}}
  JsonObject definition(Member m,String id){if(m.custom.containsKey(id))return m.custom.get(id);if(m.appearance!=null)for(var e:m.appearance.getAsJsonArray("emotes"))if(e.getAsJsonObject().get("id").getAsString().equals(id))return e.getAsJsonObject();return null;}
  boolean hasEmote(Member m,String id){return definition(m,id)!=null;}
  double duration(Member m,String id){JsonObject definition=definition(m,id);return definition==null?6.4:definition.get("duration").getAsDouble();}
  void tick(MinecraftServer server){long now=server.getTickCount()*50L;invitations.prune(now);for(var e:members.entrySet()){ServerPlayer p=server.getPlayerList().getPlayer(e.getKey());if(p==null)continue;Member m=e.getValue();if(!m.emote.isEmpty()&&(!still(p)||p.level().getGameTime()>m.emoteEnd||m.handshakePeer!=null&&!eligible(p,server.getPlayerList().getPlayer(m.handshakePeer))))stop(server,p,m);}if(server.getTickCount()%20==0)refresh(server);}
  void refresh(MinecraftServer server){for(var viewerEntry:members.entrySet()){ServerPlayer viewer=server.getPlayerList().getPlayer(viewerEntry.getKey());if(viewer==null)continue;Member state=viewerEntry.getValue();Set<UUID> visible=new HashSet<>();for(var entry:members.entrySet()){ServerPlayer source=server.getPlayerList().getPlayer(entry.getKey());if(source==null||source==viewer||!near(viewer,source,128))continue;visible.add(source.getUUID());Member content=entry.getValue();String key=content.hash;String previous=state.known.get(source.getUUID());if(previous==null){var peer=SocialRules.message("peer");peer.addProperty("player",source.getUUID().toString());peer.addProperty("name",source.getGameProfile().name());send(viewer,peer);if(!content.emote.isEmpty())send(viewer,emoteEvent(source,content));}if(!Objects.equals(previous,key)){for(JsonObject raw:content.chunks){JsonObject chunk=raw.deepCopy();chunk.addProperty("player",source.getUUID().toString());send(viewer,chunk);}state.known.put(source.getUUID(),key);}}for(UUID departed:new ArrayList<>(state.known.keySet()))if(!visible.contains(departed)){state.known.remove(departed);var gone=SocialRules.message("remove");gone.addProperty("player",departed.toString());send(viewer,gone);}}}
  void event(MinecraftServer server,ServerPlayer source,Member member){var event=emoteEvent(source,member);for(var e:members.entrySet()){ServerPlayer viewer=server.getPlayerList().getPlayer(e.getKey());if(viewer!=null&&(viewer==source||near(viewer,source,128)))send(viewer,event);}}
  JsonObject emoteEvent(ServerPlayer source,Member m){var event=SocialRules.message("emote");event.addProperty("player",source.getUUID().toString());event.addProperty("id",m.emote);event.addProperty("startTick",m.emoteStart);event.addProperty("speed",m.speed);event.addProperty("loop",m.loop);JsonObject definition=definition(m,m.emote);if(definition!=null)event.add("definition",definition);if(m.handshakePeer!=null)event.addProperty("peer",m.handshakePeer.toString());return event;}
  void stop(MinecraftServer server,ServerPlayer player,Member member){if(member.emote.isEmpty())return;UUID partner=member.handshakePeer;member.emote="";member.handshakePeer=null;event(server,player,member);if(partner!=null){Member paired=members.get(partner);ServerPlayer other=server.getPlayerList().getPlayer(partner);if(paired!=null&&other!=null&&player.getUUID().equals(paired.handshakePeer)&&paired.emote.equals("handshake")){paired.emote="";paired.handshakePeer=null;event(server,other,paired);}}}
  void remove(MinecraftServer server,UUID id){Member departed=members.remove(id);if(departed==null)return;if(departed.handshakePeer!=null){Member paired=members.get(departed.handshakePeer);ServerPlayer other=server.getPlayerList().getPlayer(departed.handshakePeer);if(paired!=null&&other!=null&&id.equals(paired.handshakePeer))stop(server,other,paired);}invitations.remove(id);for(var e:members.entrySet()){e.getValue().known.remove(id);ServerPlayer viewer=server.getPlayerList().getPlayer(e.getKey());if(viewer!=null){var gone=SocialRules.message("remove");gone.addProperty("player",id.toString());send(viewer,gone);}}}
 }
 private static boolean still(ServerPlayer p){return p!=null&&p.isAlive()&&!p.isSpectator()&&!p.isPassenger()&&!p.isSwimming()&&!p.isFallFlying()&&!p.isCrouching()&&p.getDeltaMovement().horizontalDistanceSqr()<.003&&Math.abs(p.getDeltaMovement().y)<.15;}
 private static boolean near(ServerPlayer a,ServerPlayer b,double range){return a.level()==b.level()&&a.distanceToSqr(b)<=range*range;}
 private static boolean eligible(ServerPlayer a,ServerPlayer b){return still(a)&&still(b)&&a!=b&&near(a,b,1.8)&&a.hasLineOfSight(b)&&b.hasLineOfSight(a);}
 private static void send(ServerPlayer player,JsonObject data){if(ServerPlayNetworking.canSend(player,SocialPayload.TYPE))ServerPlayNetworking.send(player,new SocialPayload(data.toString()));}
 private static void error(ServerPlayer player,String text){var error=SocialRules.message("notice");error.addProperty("text",text);send(player,error);}
}

