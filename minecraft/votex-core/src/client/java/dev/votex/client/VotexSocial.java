package dev.votex.client;

import com.google.gson.*;
import dev.votex.social.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.MultiplayerOptionsScreen;
import net.minecraft.client.gui.screens.friends.FriendsOverlayScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Real server-scoped social state. Arbitrary servers remain supported without sending unsupported payloads. */
public final class VotexSocial {
 public record Peer(UUID id,String name,boolean appearance){}
 private record Pending(UUID token,UUID from,String name,long until){}
 private record Scheduled(UUID player,String emote,UUID peer,long tick,double speed,boolean loop){}
 private static final Map<UUID,Peer> PEERS=new LinkedHashMap<>();
 private static final Map<UUID,SocialRules.Transfer> TRANSFERS=new HashMap<>();
 private static final Map<UUID,Scheduled> SCHEDULED=new HashMap<>();
 private static final Map<UUID,Map<String,JsonObject>> DEFINITIONS=new HashMap<>();
 private static final Map<UUID,Long> PEER_EPOCH=new HashMap<>();private static long peerSerial;
 private static final ArrayDeque<JsonObject> OUTBOX=new ArrayDeque<>();
 private static final ThreadPoolExecutor WORKER=new ThreadPoolExecutor(1,1,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),r->{var t=new Thread(r,"Votex appearance transport");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 private static KeyMapping accept;private static Pending pending;private static boolean initialized,connected,helloSent,authenticated;
 private static int sentRevision=-1;private static boolean sentVisible,sentCape;private static long generation,nextAppearance,nextRetry;
 private static final SocialFeedback FEEDBACK=new SocialFeedback();
 private static String displayedFeedback="";private static long feedbackOpened;
 private static String expectedAppearance="";private static boolean awaitingAppearance;
 private VotexSocial(){}
 public static void init(){if(initialized)return;initialized=true;SocialPayload.register();VotexRelay.init();
  accept=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.votex_client.handshake",GLFW.GLFW_KEY_G,KeyMapping.Category.register(Identifier.fromNamespaceAndPath("votex_client","social"))));
  ClientPlayNetworking.registerGlobalReceiver(SocialPayload.TYPE,(payload,context)->context.client().execute(()->receive(payload)));
  ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->clear());ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->clear());
  ClientLifecycleEvents.CLIENT_STOPPING.register(client->{clear();WORKER.shutdownNow();});
  EmotePlayer.setPublisher(VotexSocial::publishEmote,VotexSocial::publishStop);
 }
 public static void tick(){var c=Minecraft.getInstance();if(c.player==null||c.level==null)return;
  if(!helloSent&&ClientPlayNetworking.canSend(SocialPayload.TYPE)){helloSent=true;send(SocialRules.message("hello"));}
  long now=System.currentTimeMillis();if(pending!=null&&pending.until<now)pending=null;
  while(accept!=null&&accept.consumeClick())if(c.gui.screen()==null)acceptHandshake();
  if(connected&&(now>=nextAppearance&&(sentRevision!=LocalLooks.revision||sentVisible!=LocalLooks.visible||sentCape!=LocalLooks.capeVisible)||awaitingAppearance&&now>=nextRetry))publishAppearance();
  for(int n=0;n<2&&!OUTBOX.isEmpty();n++)send(OUTBOX.removeFirst());
  for(var e:new ArrayList<>(SCHEDULED.entrySet())){Scheduled s=e.getValue();if(c.level.getGameTime()<s.tick)continue;SCHEDULED.remove(e.getKey());double age=Math.clamp((c.level.getGameTime()-s.tick)/20d,0,32);boolean local=s.player.equals(c.player.getUUID());if(s.emote.equals("handshake")){if(local){FEEDBACK.accepted(s.peer);EmotePlayer.playHandshake(s.peer,age);}else EmotePlayer.playRemoteHandshake(s.player,s.peer,age);}else if(!local)EmotePlayer.playRemote(s.player,s.emote,age,s.speed,s.loop);}
 }
 public static String status(){if(Minecraft.getInstance().player==null)return "Keine Welt verbunden";if(!helloSent)return "Dieser Server hat keinen Votex-Relay · Emotes bleiben lokal";if(!connected)return "Votex-Relay wird verbunden …";return "Synchronisiert · "+PEERS.size()+" Votex-Spieler in deiner Nähe"+(authenticated?"":" · Server ohne Microsoft-Identitätsprüfung");}
 public static String voiceStatus(){return VoiceMicGate.status();}
 /** The native Voice surface is reached only after Votex device consent. */
 public static boolean openVoice(){return VoiceMicGate.open(Minecraft.getInstance().gui.screen());}
 public static String hostingStatus(){var server=Minecraft.getInstance().getSingleplayerServer();if(server==null)return "Du spielst auf einem Server · eine eigene Welt kann nur im Einzelspieler geöffnet werden";return server.isPublished()?"Deine Welt ist im LAN geöffnet · Port "+server.getPort()+" · Fernzugriff braucht eine erreichbare Adresse oder einen Tunnel":"Deine Welt ist privat · öffne die Multiplayer-Optionen für LAN-Zugriff";}
 public static boolean openLanWorld(Screen parent){var c=Minecraft.getInstance();if(c.getSingleplayerServer()==null)return false;c.gui.setScreen(new MultiplayerOptionsScreen(parent));return true;}
 public static boolean openFriends(Screen parent){var c=Minecraft.getInstance();if(c.player==null)return false;c.gui.setScreen(new FriendsOverlayScreen(parent));return true;}
 public static List<Peer> peers(){return List.copyOf(PEERS.values());}
 public static boolean available(){return connected;}
 public static boolean appearanceAcknowledged(){return connected&&!awaitingAppearance&&!expectedAppearance.isEmpty()&&sentRevision==LocalLooks.revision;}
 public static String pendingInvitation(){return pending==null?"":pending.name+" möchte dir die Hand geben · G zum Annehmen";}
 public static void requestHandshake(UUID target){if(!connected||!PEERS.containsKey(target)){notice("Dieser Spieler hat keinen verbundenen Votex-Relay.");return;}var packet=SocialRules.message("invite");packet.addProperty("target",target.toString());send(packet);FEEDBACK.invitation(target,"Einladung verschickt · dein Gegenüber bestätigt mit G",System.currentTimeMillis());}
 public static void acceptHandshake(){if(pending==null||System.currentTimeMillis()>pending.until)return;var packet=SocialRules.message("accept");packet.addProperty("token",pending.token.toString());pending=null;send(packet);}
 public static void declineHandshake(){pending=null;}
 public static void publishEmote(String id){if(!connected||!SocialRules.emote(id)||id.equals("handshake"))return;var packet=SocialRules.message("emote");packet.addProperty("id",id);packet.addProperty("speed",EmotePlayer.playbackSpeed());packet.addProperty("loop",EmotePlayer.looping());if(id.startsWith("custom-"))for(var definition:EmotePlayer.customDefinitions())if(definition.getAsJsonObject().get("id").getAsString().equals(id)){packet.add("definition",definition);break;}send(packet);}
 public static void publishStop(){if(connected)send(SocialRules.message("stop"));}
 public static void publishAppearance(){if(!connected||VotexClient.INSTANCE==null)return;long epoch=generation;int revision=LocalLooks.revision;sentRevision=revision;sentVisible=LocalLooks.visible;sentCape=LocalLooks.capeVisible;nextAppearance=System.currentTimeMillis()+1_000;nextRetry=System.currentTimeMillis()+12_000;
  JsonObject snapshot=LocalLooks.networkAppearance();Path directory=VotexClient.INSTANCE.directory;String skin=LocalLooks.assetSkinId(),cape=LocalLooks.assetCapeId();WORKER.execute(()->{try{readImage(snapshot,directory,"skin",skin);readImage(snapshot,directory,"cape",cape);List<JsonObject> chunks=SocialRules.chunks(snapshot);Minecraft.getInstance().execute(()->{if(connected&&generation==epoch){OUTBOX.clear();OUTBOX.addAll(chunks);expectedAppearance=chunks.getFirst().get("hash").getAsString();awaitingAppearance=true;}});}catch(Exception invalid){Minecraft.getInstance().execute(()->{awaitingAppearance=false;notice("Dieser Look kann nicht synchronisiert werden: "+invalid.getMessage());});}});
 }
 private static void readImage(JsonObject snapshot,Path directory,String type,String id)throws Exception{if(id==null||id.isEmpty())return;if(!id.matches("[a-zA-Z0-9_-]{1,80}"))throw new IllegalArgumentException("Ungültige Bild-ID");Path file=directory.resolve("assets").resolve(type+"-"+id+".png");if(!Files.exists(file)||Files.isSymbolicLink(file)||Files.size(file)>32_768)return;byte[] data=Files.readAllBytes(file);SocialRules.validatePng(data,type.equals("cape")?32:64);snapshot.addProperty(type,Base64.getEncoder().encodeToString(data));}
 private static void receive(SocialPayload payload){try{JsonObject p=SocialRules.parse(payload.json());String type=SocialRules.text(p,"type",24);switch(type){
  case "welcome"->{connected=true;authenticated=p.get("authenticated").getAsBoolean();sentRevision=-1;nextAppearance=0;}
  case "appearance-ack"->{if(expectedAppearance.equals(SocialRules.text(p,"hash",64)))awaitingAppearance=false;}
  case "peer"->{UUID id=SocialRules.uuid(p,"player");if(PEERS.size()>=SocialRules.MAX_PEERS&&!PEERS.containsKey(id))return;PEERS.put(id,new Peer(id,SocialRules.text(p,"name",16),false));PEER_EPOCH.put(id,++peerSerial);}
  case "appearance-part"->{UUID id=SocialRules.uuid(p,"player");if(!PEERS.containsKey(id))return;long epoch=generation,peerEpoch=PEER_EPOCH.getOrDefault(id,0L);var transfer=TRANSFERS.computeIfAbsent(id,unused->new SocialRules.Transfer());byte[] packed=transfer.acceptPacked(p,System.currentTimeMillis());if(packed!=null)WORKER.execute(()->{try{JsonObject appearance=SocialRules.unpack(packed);Minecraft.getInstance().execute(()->{if(connected&&generation==epoch&&PEERS.containsKey(id)&&PEER_EPOCH.getOrDefault(id,0L)==peerEpoch){LocalLooks.remote(id,appearance);Peer old=PEERS.get(id);PEERS.put(id,new Peer(id,old.name,true));}});}catch(Exception invalid){/* Keep the last validated appearance. */}});}
  case "remove"->{UUID id=SocialRules.uuid(p,"player");PEERS.remove(id);PEER_EPOCH.remove(id);TRANSFERS.remove(id);SCHEDULED.remove(id);DEFINITIONS.remove(id);EmotePlayer.stopRemote(id);LocalLooks.removeRemote(id);}
  case "emote"->{UUID id=SocialRules.uuid(p,"player");if(!id.equals(Minecraft.getInstance().player.getUUID())&&!PEERS.containsKey(id))return;String emote=SocialRules.text(p,"id",64);if(emote.isEmpty()){SCHEDULED.remove(id);if(id.equals(Minecraft.getInstance().player.getUUID()))EmotePlayer.stopLocal();else EmotePlayer.stopRemote(id);return;}if(!SocialRules.emote(emote))return;if(p.has("definition")){JsonObject definition=p.getAsJsonObject("definition");SocialRules.validateEmote(definition);if(!definition.get("id").getAsString().equals(emote))return;Map<String,JsonObject> library=DEFINITIONS.computeIfAbsent(id,ignored->new LinkedHashMap<>());library.put(emote,definition);while(library.size()>8)library.remove(library.keySet().iterator().next());JsonArray values=new JsonArray();library.values().forEach(values::add);EmotePlayer.setRemoteCustom(id,values);}UUID peer=p.has("peer")?SocialRules.uuid(p,"peer"):null;long tick=p.get("startTick").getAsLong();if(Math.abs(tick-Minecraft.getInstance().level.getGameTime())>650)return;double speed=p.has("speed")?p.get("speed").getAsDouble():1;if(!Double.isFinite(speed)||speed<.5||speed>2)return;boolean loop=p.has("loop")&&p.get("loop").getAsBoolean();SCHEDULED.put(id,new Scheduled(id,emote,peer,tick,speed,loop));}
  case "invitation"->{pending=new Pending(SocialRules.uuid(p,"token"),SocialRules.uuid(p,"from"),SocialRules.text(p,"name",16),System.currentTimeMillis()+Math.clamp(p.get("expiresIn").getAsLong(),0,10_000));}
  case "notice"->notice(SocialRules.text(p,"text",180));default->{}
 }}catch(Exception ignored){}}
 public static void drawInvitation(GuiGraphicsExtractor g){
  String text=pendingInvitation();if(text.isEmpty())text=FEEDBACK.current(System.currentTimeMillis());if(text.isEmpty()){displayedFeedback="";return;}
  long now=System.nanoTime();if(!text.equals(displayedFeedback)){displayedFeedback=text;feedbackOpened=now;}
  var mc=Minecraft.getInstance();var core=VotexClient.INSTANCE;if(core==null)return;
  var measure=dev.votex.ui.SocialToastLayout.measure(g.guiWidth(),g.guiHeight(),mc.getWindow().getGuiScale(),1);
  var lines=UiText.font().split(UiText.component(text),measure.width()-60);
  var box=dev.votex.ui.SocialToastLayout.measure(g.guiWidth(),g.guiHeight(),mc.getWindow().getGuiScale(),lines.size());
  boolean motion=VotexGlassDesignSystem.motion(core);
  float enter=motion?1-(float)Math.pow(1-Math.clamp((now-feedbackOpened)/300_000_000d,0,1),4):1;
  g.nextStratum();g.pose().pushMatrix();g.pose().scale(box.scale());g.pose().translate(0,(1-enter)*16);
  GlassRender.popover(g,box.x(),box.y(),box.width(),box.height(),core.theme);
  GlassRender.round(g,box.x()+12,box.y()+13,27,27,9,GlassRender.blend(0x403e536c,core.theme.accent(),.15f));
  GlassRender.icon(g,"friends",box.x()+17,box.y()+18,17,core.theme.accent());
  for(int i=0;i<Math.min(3,lines.size());i++)g.text(UiText.font(),lines.get(i),box.x()+48,box.y()+16+i*14,VotexGlassDesignSystem.ink(core.theme.text()),false);
  g.pose().popMatrix();
 }
 private static void notice(String value){FEEDBACK.show(value,System.currentTimeMillis());}
 public static void feedback(String text){if(text!=null&&!text.isBlank())notice(text.codePoints().filter(c->!Character.isISOControl(c)).limit(180).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString());}
 private static void send(JsonObject p){if(Minecraft.getInstance().player!=null&&ClientPlayNetworking.canSend(SocialPayload.TYPE))ClientPlayNetworking.send(new SocialPayload(p.toString()));}
 private static void clear(){generation++;connected=helloSent=false;authenticated=false;pending=null;FEEDBACK.clear();displayedFeedback="";PEERS.clear();PEER_EPOCH.clear();TRANSFERS.clear();SCHEDULED.clear();DEFINITIONS.clear();OUTBOX.clear();LocalLooks.clearRemote();EmotePlayer.clearRemote();sentRevision=-1;nextAppearance=0;awaitingAppearance=false;expectedAppearance="";}
}
