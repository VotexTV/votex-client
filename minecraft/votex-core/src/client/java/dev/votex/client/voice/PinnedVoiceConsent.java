package dev.votex.client.voice;

import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.internal.events.StartMicEvent;
import de.maxhenkel.voicechat.gui.VoiceChatScreen;
import de.maxhenkel.voicechat.gui.onboarding.OnboardingManager;
import de.maxhenkel.voicechat.voice.client.ClientManager;
import dev.votex.client.VoiceMicGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** All version-sensitive classes stay behind VoiceConsentPlugin's exact version branch. */
final class PinnedVoiceConsent implements VoiceMicGate.Adapter {
 static void register(EventRegistration events){events.registerEvent(StartMicEvent.class,event->{if(VoiceMicGate.cancelMicrophoneStart())event.cancel();},1000);VoiceMicGate.attach(new PinnedVoiceConsent());}
 @Override public void open(Screen parent){if(OnboardingManager.isOnboarding())OnboardingManager.startOnboarding(parent);else Minecraft.getInstance().gui.setScreen(new VoiceChatScreen());}
 @Override public void startMutedMicrophone(){ClientManager.getPlayerStateManager().setMuted(true);var client=ClientManager.getClient();if(client!=null&&client.getConnection()!=null&&client.getConnection().isInitialized()&&client.getMicThread()==null)client.onVoiceChatConnected(client.getConnection());}
 @Override public void stopMicrophone(){ClientManager.getPlayerStateManager().setMuted(true);var client=ClientManager.getClient();if(client!=null)client.closeMicThread();}
 @Override public String status(){var client=ClientManager.getClient();if(client==null)return "Voice Chat wartet auf eine Serververbindung";if(client.getConnection()==null||!client.getConnection().isInitialized())return "Voice Chat ist installiert · mit diesem Server nicht verbunden";var state=ClientManager.getPlayerStateManager();if(state.isDisabled())return "Voice Chat verbunden · Audio ausgeschaltet";return state.isMuted()?"Voice Chat verbunden · Mikrofon stumm":"Voice Chat verbunden · Mikrofonsteuerung in den Voice-Einstellungen";}
}
