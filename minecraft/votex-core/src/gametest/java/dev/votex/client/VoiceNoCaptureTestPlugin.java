package dev.votex.client;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.internal.events.StartMicEvent;
import java.util.concurrent.atomic.AtomicInteger;

/** QA-only backup after the production permission callback. Never shipped in Core. */
public final class VoiceNoCaptureTestPlugin implements VoicechatPlugin {
 private static final AtomicInteger blocked=new AtomicInteger();
 @Override public String getPluginId(){return "votex_test_no_hardware_capture";}
 @Override public void registerEvents(EventRegistration events){if("1".equals(System.getenv("VOTEX_VOICE_CONSENT_TEST")))events.registerEvent(StartMicEvent.class,event->{event.cancel();blocked.incrementAndGet();},-1000);}
 public static int blocked(){return blocked.get();}
}
