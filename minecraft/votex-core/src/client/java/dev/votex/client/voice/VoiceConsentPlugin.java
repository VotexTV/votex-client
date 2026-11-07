package dev.votex.client.voice;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import net.fabricmc.loader.api.FabricLoader;

/** Loaded only by SVC's plugin entrypoint, never by the normal optional-mod-free client path.
 * StartMicEvent is explicitly INTERNAL in upstream 2.6.24. There is no public pre-device-open
 * veto. This typed adapter is therefore version pinned and stays unadvertised until native QA.
 */
public final class VoiceConsentPlugin implements VoicechatPlugin {
 @Override public String getPluginId(){return "votex_microphone_consent";}
 @Override public void registerEvents(EventRegistration events){
  String version=FabricLoader.getInstance().getModContainer("voicechat").orElseThrow().getMetadata().getVersion().getFriendlyString();
  // Resolve the class containing internal SVC types only after the exact version check.
  // Older/newer user-owned installations remain untouched and use their own Voice controls.
  if(version.equals("2.6.24+26.2"))PinnedVoiceConsent.register(events);
 }
}
