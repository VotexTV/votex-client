package dev.votex.client;

import dev.votex.config.VoiceMicConsent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/** Optional-mod boundary: this class contains no SVC types and is safe without Voice installed. */
public final class VoiceMicGate {
 public interface Adapter {void open(Screen parent);void startMutedMicrophone();void stopMicrophone();String status();}
 private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("votex/voice-microphone.json");
 private static final VoiceMicConsent CONSENT=load();
 private static volatile Adapter adapter;
 private static final AtomicInteger cancelled=new AtomicInteger(),permitted=new AtomicInteger();
 private VoiceMicGate(){}
 private static VoiceMicConsent load(){try{return VoiceMicConsent.read(FILE);}catch(Exception invalid){VotexClient.LOG.warning("Microphone permission could not be read; device access remains blocked");return VoiceMicConsent.denied(FILE);}}
 public static void attach(Adapter next){adapter=next;}
 public static boolean allowed(){return CONSENT.allowed();}
 public static boolean ready(){return adapter!=null;}
 /** Called before SVC constructs MicThread. No device, world or screen access on this thread. */
 public static boolean cancelMicrophoneStart(){boolean cancel=!allowed()||!ready();(cancel?cancelled:permitted).incrementAndGet();return cancel;}
 public static int cancelledStarts(){return cancelled.get();}public static int permittedStarts(){return permitted.get();}
 public static String status(){if(!FabricLoader.getInstance().isModLoaded("voicechat"))return "Simple Voice Chat fehlt in diesem Minecraft-Profil";Adapter voice=adapter;if(voice==null)return "Voice-Mikrofonfreigabe für diese Version nicht verfügbar";String state=voice.status();return state+(allowed()?"":" · Mikrofonzugriff gesperrt");}
 public static boolean open(Screen parent){if(!FabricLoader.getInstance().isModLoaded("voicechat")||!ready()){Minecraft.getInstance().gui.setScreen(new VoiceSetupScreen(VotexClient.INSTANCE,parent));return true;}if(!allowed())Minecraft.getInstance().gui.setScreen(new VoiceConsentScreen(VotexClient.INSTANCE,parent));else adapter.open(parent);return true;}
 /** Invoked only by the explicit consent button. Muting is retained before the device may start. */
 public static boolean allowAndOpen(Screen parent){try{if(!ready())return false;CONSENT.setAllowed(true);adapter.startMutedMicrophone();adapter.open(parent);return true;}catch(Exception|LinkageError failure){revoke();VotexClient.INSTANCE.error("Mikrofon konnte nicht freigegeben werden",new Exception(failure));return false;}}
 public static void revoke(){try{CONSENT.setAllowed(false);}catch(Exception invalid){VotexClient.LOG.warning("Microphone permission is revoked in this session, but could not be saved; retry before restarting");}Adapter voice=adapter;if(voice!=null)try{voice.stopMicrophone();}catch(LinkageError|RuntimeException invalid){VotexClient.LOG.warning("Voice microphone could not be stopped through the pinned adapter");}}
}
