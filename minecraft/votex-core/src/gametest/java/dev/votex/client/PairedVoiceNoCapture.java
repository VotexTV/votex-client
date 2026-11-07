package dev.votex.client;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Test-process-only microphone-start cancellation. This class is never shipped in Core. */
final class PairedVoiceNoCapture {
 private static final AtomicInteger cancelled=new AtomicInteger();
 private PairedVoiceNoCapture(){}
 @SuppressWarnings("unchecked") static void install()throws Exception{
  if(!Set.of("A","B").contains(System.getenv("VOTEX_PAIRED_ROLE")))throw new AssertionError("Explicit paired QA required");
  Class<?> managerClass=Class.forName("de.maxhenkel.voicechat.plugins.PluginManager"),eventClass=Class.forName("de.maxhenkel.voicechat.api.internal.events.StartMicEvent"),eventApi=Class.forName("de.maxhenkel.voicechat.api.events.Event");
  Object manager=managerClass.getMethod("instance").invoke(null);var field=managerClass.getDeclaredField("events");field.setAccessible(true);
  Map<Class<?>,List<Consumer<Object>>> original=(Map<Class<?>,List<Consumer<Object>>>)field.get(manager);Map<Class<?>,List<Consumer<Object>>> next=new HashMap<>(original);List<Consumer<Object>> callbacks=new ArrayList<>();
  callbacks.add(event->{try{if(!Boolean.TRUE.equals(eventApi.getMethod("cancel").invoke(event)))throw new AssertionError("Microphone start cancellation rejected");cancelled.incrementAndGet();}catch(ReflectiveOperationException invalid){throw new AssertionError(invalid);}});callbacks.addAll(next.getOrDefault(eventClass,List.of()));next.put(eventClass,callbacks);field.set(manager,next);
  Object clientManager=Class.forName("de.maxhenkel.voicechat.plugins.ClientPluginManager").getMethod("instance").invoke(null);
  if(!Boolean.TRUE.equals(clientManager.getClass().getMethod("onStartMic").invoke(clientManager)))throw new AssertionError("No-capture guard failed before connection");
 }
 static boolean connected(){try{Object client=client();if(client==null)return false;Object connection=client.getClass().getMethod("getConnection").invoke(client);return connection!=null&&Boolean.TRUE.equals(connection.getClass().getMethod("isInitialized").invoke(connection))&&Boolean.TRUE.equals(connection.getClass().getMethod("isConnected").invoke(connection));}catch(ReflectiveOperationException unavailable){return false;}}
 static Map<String,Object> proof()throws Exception{
  Object client=client();if(!connected()||client==null)throw new AssertionError("Voice UDP connection not initialized");
  if(client.getClass().getMethod("getMicThread").invoke(client)!=null||client.getClass().getMethod("getRecorder").invoke(client)!=null)throw new AssertionError("Unexpected microphone or recording object");
  if(cancelled.get()<2)throw new AssertionError("Real connection did not pass the microphone no-capture guard");
  Object state=Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager").getMethod("getPlayerStateManager").invoke(null);
  if(!Boolean.TRUE.equals(state.getClass().getMethod("isDisabled").invoke(state))||!Boolean.TRUE.equals(state.getClass().getMethod("isMuted").invoke(state)))throw new AssertionError("Paired Voice must stay disabled and muted");
  String status=VotexSocial.voiceStatus();if(!status.startsWith("Voice Chat verbunden"))throw new AssertionError("Votex Voice bridge did not report its actual initialized connection: "+status);
  return Map.of("udpInitialized",true,"udpConnected",true,"bridgeStatus",status,"microphoneStartCancelled",cancelled.get(),"microphoneThreadAbsent",true,"recorderAbsent",true,"audioDisabled",true,"audioRoundTrip","not attempted; no microphone capture");
 }
 private static Object client()throws ReflectiveOperationException{return Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager").getMethod("getClient").invoke(null);}
}
