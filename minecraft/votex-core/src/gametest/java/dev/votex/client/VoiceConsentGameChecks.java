package dev.votex.client;

import de.maxhenkel.voicechat.voice.client.ClientManager;
import dev.votex.config.AtomicJson;
import dev.votex.config.VoiceMicConsent;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Native production consent checks with a separate last-resort QA device veto. */
public final class VoiceConsentGameChecks {
 private VoiceConsentGameChecks(){}
 public static Map<String,Object> run(ClientGameTestContext context,Path destination)throws Exception{
  if(!"1".equals(System.getenv("VOTEX_VOICE_CONSENT_TEST")))throw new AssertionError("Explicit no-hardware Voice QA required");
  context.waitFor(mc->VoiceMicGate.ready()&&connected(),1200);
  context.runOnClient(mc->{if(VoiceMicGate.allowed()||VoiceMicGate.cancelledStarts()<1||VoiceNoCaptureTestPlugin.blocked()!=0)throw new AssertionError("Production default-deny hook did not run first on actual UDP connection");absent();if(!VotexSocial.openVoice()||!(mc.gui.screen() instanceof VoiceConsentScreen))throw new AssertionError("Native consent screen missing");});
  context.waitTicks(8);context.takeScreenshot(TestScreenshotOptions.of("voice-explicit-microphone-consent").withDestinationDir(destination).disableCounterPrefix());
  click(context,"Später");context.runOnClient(mc->{if(VoiceMicGate.allowed())throw new AssertionError("Dismiss granted device access");absent();VotexSocial.openVoice();});
  click(context,"Mikrofon aktivieren und Voice öffnen");
  context.runOnClient(mc->{if(!VoiceMicGate.allowed()||VoiceMicGate.permittedStarts()<1||VoiceNoCaptureTestPlugin.blocked()<1||!VoiceMicConsent.read(VotexClient.INSTANCE.directory.resolve("voice-microphone.json")).allowed())throw new AssertionError("Explicit native button did not persist consent and reach the independent QA veto");absent();mc.gui.setScreen(new VotexScreen(VotexClient.INSTANCE,"SETTINGS"));});
  click(context,"Mikrofonzugriff für Voice");
  context.runOnClient(mc->{if(VoiceMicGate.allowed()||VoiceMicConsent.read(VotexClient.INSTANCE.directory.resolve("voice-microphone.json")).allowed())throw new AssertionError("Native Settings revoke did not persist");int before=VoiceMicGate.cancelledStarts();var client=ClientManager.getClient();client.onVoiceChatConnected(client.getConnection());if(VoiceMicGate.cancelledStarts()<=before)throw new AssertionError("Reconnect did not pass revoked production gate");absent();if(!connected())throw new AssertionError("Mic revocation closed the UDP connection");});
  Map<String,Object> result=Map.of("productionDefaultDeniedBeforeDevice",true,"nativeConsentDismissDoesNotGrant",true,"nativeExplicitAllowPersisted",true,"nativeSettingsRevokePersisted",true,"udpRemainsConnected",true,"productionDeniedStarts",VoiceMicGate.cancelledStarts(),"productionPermittedStarts",VoiceMicGate.permittedStarts(),"independentTestDeviceVeto",VoiceNoCaptureTestPlugin.blocked(),"microphoneAndRecorderAbsent",true,"audioRoundTrip","not attempted; independent QA hook blocks physical microphone even after explicit permission");
  AtomicJson.write(destination.resolve("voice-production-consent-proof.json"),result);context.runOnClient(mc->mc.gui.setScreen(null));return result;
 }
 private static boolean connected(){var client=ClientManager.getClient();return client!=null&&client.getConnection()!=null&&client.getConnection().isInitialized()&&client.getConnection().isConnected();}
 private static void absent(){var client=ClientManager.getClient();if(client==null||client.getMicThread()!=null||client.getRecorder()!=null)throw new AssertionError("QA microphone/recorder unexpectedly present");}
 private static void click(ClientGameTestContext context,String label){context.runOnClient(mc->{Screen screen=mc.gui.screen();Button button=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.active&&b.getMessage().getString().equals(label)).findFirst().orElseThrow(()->new AssertionError("Missing Voice button "+label));float scale=screen instanceof VoiceConsentScreen||screen instanceof VotexScreen?dev.votex.ui.WorkspaceLayout.measure(screen.width,screen.height,net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale()).scale():1;var event=new MouseButtonEvent((button.getX()+button.getWidth()/2d)*scale,(button.getY()+button.getHeight()/2d)*scale,new MouseButtonInfo(0,0));if(!screen.mouseClicked(event,false))throw new AssertionError("Native Voice button hit-test failed");screen.mouseReleased(event);});context.waitTicks(3);}
}
