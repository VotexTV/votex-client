package dev.votex.client;

import com.google.gson.*;
import dev.votex.arcade.ArcadeGames;
import dev.votex.client.mixin.FontAccessor;
import dev.votex.config.AtomicJson;
import dev.votex.config.ModuleRegistry;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** One isolated native run for the complete in-game workspace, real controls and real skin geometry. */
public final class VotexInGameGoalTest {
 private static final String[] PAGES={"HOME","MODULES","COSMETICS","SKINS","ANIMATIONS","FRIENDS","PERFORMANCE","SETTINGS"};
 private final Map<String,Object> evidence=new LinkedHashMap<>();
 private Path destination;
 public void run(ClientGameTestContext context){
  if(!"1".equals(System.getenv("VOTEX_INGAME_GOAL_TEST")))throw new AssertionError("Explicit isolated test dispatch required");
  context.getInput().resizeWindow(1600,900);
  context.runOnClient(mc->{mc.options.guiScale().set(2);mc.getWindow().setGuiScale(2);mc.options.renderDistance().set(6);mc.options.simulationDistance().set(5);mc.options.gamma().set(1d);});
  try(var world=context.worldBuilder().create()){
   world.getConnection().waitForChunksRender();
   if("1".equals(System.getenv("VOTEX_MODULES_REVIEW_TEST"))){reviewModules(context);return;}
   if("1".equals(System.getenv("VOTEX_MODPACK_COMPATIBILITY_TEST"))){
    context.runOnClient(mc->{var core=VotexClient.INSTANCE;destination=core.directory.resolve("goal-evidence");Files.createDirectories(destination);evidence.put("fixtureStartupBinding",ModpackGameChecks.restoreStartupBinding(mc));evidence.put("loadedModpack",ModpackGameChecks.loaded(mc));core.hud.tick(mc);if(core.modules.config().positions.containsKey("fps"))throw new AssertionError("Expected a pristine default HUD");if(core.hud.position(0).x<mc.getWindow().getGuiScaledWidth()/2)throw new AssertionError("Default HUD collides with the default minimap corner");});
    context.waitTicks(8);shot(context,"modpack-default-hud");
   }
   context.runOnClient(mc->{
    var core=VotexClient.INSTANCE;if(core==null||mc.player==null)throw new AssertionError("Client/level missing");
    Path game=mc.gameDirectory.toPath().toAbsolutePath().normalize();if(!game.toString().toLowerCase(Locale.ROOT).contains("clientgametest"))throw new AssertionError("Refusing to seed a non-test game directory: "+game);
    destination=core.directory.resolve("goal-evidence");Files.createDirectories(destination);core.lastError="";seed(core);if(!core.lastError.isEmpty())throw new AssertionError(core.lastError);
    core.theme=new dev.votex.config.ThemeConfig(0xff83baff,0xfff6faff,0xffa9bdd6,"1".equals(System.getenv("VOTEX_UI_FIDELITY_TEST"))?0x94101729:0xca152236,14,35);core.modules.config().menuAnimations=true;core.modules.config().blurMenus=true;
    core.modules.config().positions.clear();core.modules.config().appearance.clear();core.modules.config().x=12;core.modules.config().y=12;core.modules.config().scale=100;
    core.modules.config().enabled.put("fps",true);core.modules.config().enabled.put("coordinates",true);core.modules.config().enabled.put("armor",true);core.modules.config().enabled.put("keystrokes",true);core.modules.config().enabled.put("item_physics",false);core.hud.tick(mc);EmotePlayer.setSpeed(1);EmotePlayer.setLoop(false);
    if(ModuleRegistry.HUD_MODULES.size()<21)throw new AssertionError("Expected more than20 configurable HUD modules");mc.player.setXRot(0);mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);EmoteRigGameChecks.run();fontProof(mc);evidence.put("nativeRig","classic/slim 9 gestures; 40 resets; nested overlays; isolated deferred previews");
   });
   context.runOnClient(mc->{evidence.put("nativeHandshakeContact",HandshakeGameChecks.sweep(mc));evidence.put("firstPersonCameraGeometry",FirstPersonCameraGameChecks.run());});
   checkpoint(context,"native-rig-and-font");
   if("1".equals(System.getenv("VOTEX_BLUR_STRENGTH_TEST"))){evidence.put("menuBlur",MenuBlurGameChecks.run(context,destination));context.runOnClient(mc->{if(!VotexClient.INSTANCE.lastError.isEmpty())throw new AssertionError(VotexClient.INSTANCE.lastError);evidence.put("status","passed");AtomicJson.write(destination.resolve("native-blur-strength-proof.json"),evidence);});System.out.println("VOTEX_BLUR_STRENGTH_PASS liveExport+nativeGetter+shaderState+offSwitches+vanillaPreserved");return;}
   if("1".equals(System.getenv("VOTEX_INSTALL_NOTICE_TEST"))){evidence.put("installationNotice",InstallationNoticeGameChecks.run(context,destination));context.runOnClient(mc->{if(!VotexClient.INSTANCE.lastError.isEmpty())throw new AssertionError(VotexClient.INSTANCE.lastError);evidence.put("status","passed");AtomicJson.write(destination.resolve("native-installation-notice-proof.json"),evidence);});System.out.println("VOTEX_INSTALL_NOTICE_PASS actualStaging+unobstructedNativeControls+edgeClicks+twoDensities+reducedMotion");return;}
   if("1".equals(System.getenv("VOTEX_HOVER_EFFECTS_TEST"))){evidence.put("hoverEffects",HoverEffectsGameChecks.run(context,destination));context.runOnClient(mc->{evidence.put("status","passed");AtomicJson.write(destination.resolve("native-hover-proof.json"),evidence);});System.out.println("VOTEX_HOVER_EFFECTS_PASS liveTheme+nativeLight+stableHover+offSwitches+twoSizes");return;}
   if("1".equals(System.getenv("VOTEX_WORKSPACE_FLOW_TEST"))){
    compactWorkspaceControls(context);checkpoint(context,"flow-compact-workspaces");
    context.getInput().resizeWindow(1600,900);context.waitTicks(3);context.runOnClient(mc->VotexClient.INSTANCE.modules.config().workspaceWindow=new dev.votex.ui.WorkspaceWindow.Saved());
    moduleControls(context);wardrobeControls(context);emoteControls(context);checkpoint(context,"flow-module-wardrobe-emote-controls");
    evidence.put("socialUi",SocialUiGameChecks.run(context,destination));
    context.runOnClient(mc->{if(!VotexClient.INSTANCE.lastError.isEmpty())throw new AssertionError(VotexClient.INSTANCE.lastError);evidence.put("status","passed");evidence.put("scope","current native continuous lists, pinned controls, compact full-card fitting, selection/persistence, synthetic peer scrolling and no-microphone dialog cancellation");AtomicJson.write(destination.resolve("native-workspace-flow-proof.json"),evidence);});
    System.out.println("VOTEX_WORKSPACE_FLOW_PASS continuousLists+pinnedHeaders+compactCards+selection+24Peers+noMic");return;
   }
   if("1".equals(System.getenv("VOTEX_MODPACK_COMPATIBILITY_TEST"))){
    keyBindings(context);checkpoint(context,"modpack-real-shift-and-b-input");for(String page:PAGES){open(context,page);shot(context,"modpack-workspace-"+page.toLowerCase(Locale.ROOT));}checkpoint(context,"modpack-eight-workspaces");
    context.runOnClient(mc->{Arrays.stream(mc.options.keyMappings).filter(mapping->mapping.getName().equals("gui.xaero_new_waypoint")).forEach(mapping->mapping.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_B)));net.minecraft.client.KeyMapping.resetMapping();mc.options.save();});
    open(context,"SETTINGS");shot(context,"modpack-saved-key-conflict");click(context,"Emote-Tastenkonflikt lösen");context.runOnClient(mc->{if(StarterKeyBindings.conflict(mc))throw new AssertionError("The explicit repair left the saved B conflict active");});evidence.put("starterKeyBindings",Map.of("freshDefaultConflictResolved",true,"voiceNPreserved",true,"savedCustomKeyPreserved",true,"savedConflictRepairedOnlyByExplicitNativeButton",true));
    profileWorkspaceControls(context);checkpoint(context,"modpack-saved-key-repair-floating-window-first-person");
    context.getInput().resizeWindow(1280,720);context.waitTicks(3);
    for(String id:new String[]{"wave","clap","salute","celebrate"}){
     long start=System.nanoTime();context.runOnClient(mc->{mc.gui.setScreen(null);mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);EmotePlayer.play(id);});context.waitFor(mc->System.nanoTime()-start>1_100_000_000L,1000);shot(context,"modpack-gesture-"+id+"-front");context.runOnClient(mc->mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));context.waitTicks(3);shot(context,"modpack-gesture-"+id+"-back");
    }
    context.runOnClient(mc->{EmotePlayer.stopLocal();mc.gui.setScreen(null);});
    evidence.put("appleSkinConfig",ModpackGameChecks.appleSkin(context,destination));
    checkpoint(context,"modpack-world-poses-and-appleskin-config");
    evidence.put("shaderPipelines",ModpackGameChecks.shaders(context,destination));
    checkpoint(context,"modpack-two-shader-world-and-ui-pipelines");
    evidence.put("itemPhysics",ItemPhysicsGameChecks.run(context,destination));
    context.runOnClient(mc->{ModpackGameChecks.noMicrophone();if(!VotexClient.INSTANCE.lastError.isEmpty())throw new AssertionError(VotexClient.INSTANCE.lastError);evidence.put("status","passed");evidence.put("scope","one isolated world with all selected starter mods; native input/rendering; no authenticated/public session, physical audio or FPS benchmark");AtomicJson.write(destination.resolve("native-modpack-proof.json"),evidence);});
    System.out.println("VOTEX_MODPACK_COMPATIBILITY_PASS realWorld+loadedMods+shiftAndWheel+floatingWindow+8firstPerson+3dSkinLayers+appleSkinConfig+2shaderPipelines+itemPhysics+noMic");return;
   }
   if("1".equals(System.getenv("VOTEX_APPEARANCE_MOTION_TEST"))){evidence.put("appearanceMotion",AppearanceMotionGameChecks.run(context,destination));context.runOnClient(mc->{evidence.put("status","passed");AtomicJson.write(destination.resolve("native-appearance-motion-proof.json"),evidence);});System.out.println("VOTEX_APPEARANCE_MOTION_PASS displayedLookAndCapeFrames+nativeOrbit+cropDrag+export+draft");return;}
   if("1".equals(System.getenv("VOTEX_SOCIAL_UI_TEST"))){evidence.put("socialUi",SocialUiGameChecks.run(context,destination));context.runOnClient(mc->{evidence.put("status","passed");AtomicJson.write(destination.resolve("native-social-ui-proof.json"),evidence);});System.out.println("VOTEX_SOCIAL_UI_PASS livePeers+pagination+deferredRefresh+voiceDisplayedFrameCancel+noMic");return;}
   if("1".equals(System.getenv("VOTEX_CHAT_DENSITY_TEST"))){evidence.put("chatDensity",ChatDensityGameChecks.run(context,destination));evidence.put("socialFeedback",SocialFeedbackGameChecks.run(context,destination));context.runOnClient(mc->{evidence.put("status","passed");AtomicJson.write(destination.resolve("native-chat-density-proof.json"),evidence);});System.out.println("VOTEX_CHAT_DENSITY_PASS fiveSizesAndGuiCases+privateClipboardActions+liveHistory+scroll+popupResize+socialFeedback");return;}
   if("1".equals(System.getenv("VOTEX_INSTALL_FEEDBACK_TEST"))){evidence.put("installations",InstallationGameChecks.run(context,destination));context.runOnClient(mc->{evidence.put("status","passed");AtomicJson.write(destination.resolve("native-installation-feedback-proof.json"),evidence);});System.out.println("VOTEX_INSTALLATION_FEEDBACK_PASS actualStaging+survivesMenuClose+failure+reducedMotion");return;}
   if("1".equals(System.getenv("VOTEX_LIBRARY_ICON_TEST"))){evidence.put("openingInput",WorkspaceMotionGameChecks.run(context,destination));discoveryResizeControls(context);evidence.put("catalogIcons",CatalogIconsGameChecks.run(context,destination));context.runOnClient(mc->{evidence.put("status","passed");AtomicJson.write(destination.resolve("native-library-icons-proof.json"),evidence);});System.out.println("VOTEX_LIBRARY_ICONS_PASS liveWebPLogos+discoveryInstalledRoute+resizeSearch+boundedVisibleIcons+openingInput");return;}
   if("1".equals(System.getenv("VOTEX_PROFILE_WORKSPACE_TEST"))){profileWorkspaceControls(context);context.runOnClient(mc->{evidence.put("status","passed");AtomicJson.write(destination.resolve("native-profile-workspace-proof.json"),evidence);});System.out.println("VOTEX_PROFILE_WORKSPACE_PASS persistedWindow+fourCorners+firstPersonEmotes+heldItemPreserved");return;}
   if("1".equals(System.getenv("VOTEX_VOICE_CONSENT_TEST"))){evidence.put("voiceMicrophonePermission",VoiceConsentGameChecks.run(context,destination));checkpoint(context,"voice-microphone-permission");}
   keyBindings(context);
   // Complete pages, each after its actual wall-clock opening animation has settled.
   for(String page:PAGES){open(context,page);shot(context,"workspace-"+page.toLowerCase(Locale.ROOT));checkpoint(context,"workspace-"+page);}
   if("1".equals(System.getenv("VOTEX_UI_FIDELITY_TEST"))){
    List<Map<String,Object>> bounds=new ArrayList<>();
    for(int[] size:new int[][]{{768,480},{1280,720},{1920,1080}}){context.getInput().resizeWindow(size[0],size[1]);context.waitTicks(3);for(String page:PAGES)context.runOnClient(mc->{mc.gui.setScreen(new VotexScreen(VotexClient.INSTANCE,page));bounds.add(checkBounds(mc.gui.screen(),size));});open(context,"MODULES");shot(context,"clean-responsive-"+size[0]+"x"+size[1]);}
    compactWorkspaceControls(context);
    secondaryWorkspaceControls(context);
    arcadeHudWorkspaceControls(context);
    emoteWheelAndVoiceDialogsControls(context);
    discoveryResizeControls(context);
    context.getInput().resizeWindow(1600,900);context.waitTicks(3);open(context,"MODULES");
    context.runOnClient(mc->{if(UiSampling.smoothGlyphs()<100||UiSampling.smoothSurfaces()<100||!UiSampling.surfaceScopeClosed())throw new AssertionError("The real UI did not use scoped smooth font/surface sampling");evidence.put("smoothSampling",Map.of("actualGuiGlyphDraws",UiSampling.smoothGlyphs(),"actualGlassIconDraws",UiSampling.smoothSurfaces(),"surfaceScopeClosed",true,"scope","Votex font/GUI only; vanilla/world/pixel-art sampling preserved"));evidence.put("responsiveWidgetExtents",bounds);evidence.put("status","passed");evidence.put("kind","native UI fidelity follow-up, not full behavior regression");AtomicJson.write(destination.resolve("native-ui-fidelity-proof.json"),evidence);});
    System.out.println("VOTEX_NATIVE_UI_FIDELITY_PASS smoothOwnTypeface+smoothOwnSurfaces+themeAlpha+8workspaces+3sizes");if(!"1".equals(System.getenv("VOTEX_INGAME_REGRESSION_TEST")))return;
    // Independent functional fixture in the same process, after the real responsive UI phase.
    context.runOnClient(mc->{var core=VotexClient.INSTANCE;mc.gui.setScreen(null);seed(core);core.modules.config().positions.clear();core.modules.config().appearance.clear();core.modules.config().workspaceWindow=new dev.votex.ui.WorkspaceWindow.Saved();core.modules.config().enabled.put("fps",true);core.modules.config().enabled.put("coordinates",true);core.modules.config().enabled.put("armor",true);core.modules.config().enabled.put("keystrokes",true);core.modules.config().enabled.put("item_physics",false);core.modules.config().menuAnimations=true;core.modules.config().blurMenus=true;EmotePlayer.setSpeed(1);EmotePlayer.setLoop(false);EmotePlayer.stopLocal();core.hud.tick(mc);core.engine.saveModules(core.modules);mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.player.setXRot(0);});checkpoint(context,"responsive-phase-complete-functional-fixture-reset");
   }
   moduleControls(context);checkpoint(context,"module-controls");hudControls(context);checkpoint(context,"hud-controls");wardrobeControls(context);previewCacheControls(context);checkpoint(context,"wardrobe-controls");appearanceControls(context);checkpoint(context,"appearance-controls");emoteControls(context);checkpoint(context,"emote-controls");settingsControls(context);checkpoint(context,"settings-controls");arcadeControls(context);checkpoint(context,"arcade-controls");evidence.put("itemPhysics",ItemPhysicsGameChecks.run(context,destination));checkpoint(context,"item-physics");
   // Check physical widget extents at small/windowed, standard and wide desktop sizes.
   List<Map<String,Object>> bounds=new ArrayList<>();
   for(int[] size:new int[][]{{768,480},{1600,900},{1920,1080}}){
    context.getInput().resizeWindow(size[0],size[1]);context.waitTicks(4);
    for(String page:PAGES){context.runOnClient(mc->{mc.gui.setScreen(new VotexScreen(VotexClient.INSTANCE,page));bounds.add(checkBounds(mc.gui.screen(),size));});}
    open(context,"MODULES");shot(context,"responsive-"+size[0]+"x"+size[1]);
   }
   evidence.put("responsiveWidgetExtents",bounds);context.getInput().resizeWindow(1600,900);context.waitTicks(4);
   // Actual in-world playback, not thumbnail substitution. Both sides expose skin layer errors.
   // Keep the complete skin visible during the pose sweep, then verify wearables separately.
   context.runOnClient(mc->LocalLooks.visible=false);
   for(String id:new String[]{"wave","sit","dance","point","clap","salute","celebrate","bow","handshake"}){
    long started=System.nanoTime();context.runOnClient(mc->{mc.gui.setScreen(null);mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);EmotePlayer.setSpeed(1);EmotePlayer.setLoop(false);EmotePlayer.play(id);});
    context.waitFor(mc->System.nanoTime()-started>1_200_000_000L,1000);shot(context,"gesture-"+id+"-front");
    context.runOnClient(mc->mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));context.waitTicks(2);shot(context,"gesture-"+id+"-back");
   }
   long dressed=System.nanoTime();context.runOnClient(mc->{LocalLooks.visible=true;EmotePlayer.play("bow");});context.waitFor(mc->System.nanoTime()-dressed>1_300_000_000L,1000);shot(context,"authored-wings-bow-back");context.runOnClient(mc->mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));context.waitTicks(2);shot(context,"authored-wings-bow-front");
   context.runOnClient(mc->{EmotePlayer.stop();mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.gui.setScreen(new EmoteWheel(VotexClient.INSTANCE));});settle(context);shot(context,"emote-wheel-real-avatars");
   checkpoint(context,"gestures-and-responsive");evidence.put("socialRelay",SocialRelayGameChecks.run(context));checkpoint(context,"one-client-relay");if("1".equals(System.getenv("VOTEX_LIBRARY_CHAT_TEST"))){evidence.put("libraryChat",LibraryChatGameChecks.run(context,destination));checkpoint(context,"native-library-chat");}
   if("1".equals(System.getenv("VOTEX_INGAME_REGRESSION_TEST"))){
    evidence.put("chatDensity",ChatDensityGameChecks.run(context,destination));checkpoint(context,"regression-chat-density");
    evidence.put("socialFeedback",SocialFeedbackGameChecks.run(context,destination));checkpoint(context,"regression-social-feedback");
    evidence.put("socialUi",SocialUiGameChecks.run(context,destination));checkpoint(context,"regression-social-ui");
    evidence.put("appearanceMotion",AppearanceMotionGameChecks.run(context,destination));checkpoint(context,"regression-appearance-motion");
    evidence.put("installations",InstallationGameChecks.run(context,destination));checkpoint(context,"regression-installation-feedback");
    evidence.put("openingInput",WorkspaceMotionGameChecks.run(context,destination));checkpoint(context,"regression-opening-frame");
    evidence.put("catalogIcons",CatalogIconsGameChecks.run(context,destination));checkpoint(context,"regression-native-webp-and-cache");
    evidence.put("clipboardScope","test-only private in-memory clipboard; no platform clipboard access");
    evidence.put("acceptanceLimits",List.of("single private integrated world, not a new paired transport test","synthetic chat and social UI fixtures, not authenticated identities/signatures","no microphone capture or physical audio test","native launcher profile download/reverse-sync remains separate","not a final release or public deployment"));
   }
   context.runOnClient(mc->{if(!VotexClient.INSTANCE.lastError.isEmpty())throw new AssertionError(VotexClient.INSTANCE.lastError);evidence.put("status","passed");evidence.put("screenshots",destination.toString());AtomicJson.write(destination.resolve("native-goal-proof.json"),evidence);});
   System.out.println("VOTEX_INGAME_GOAL_PASS nativeUI+font+controls+responsive+9gestures");
  }catch(Exception failure){throw new AssertionError("Native in-game goal verification failed",failure);}
 }
 private void checkpoint(ClientGameTestContext context,String stage){context.runOnClient(mc->{evidence.put("lastVerifiedStage",stage);evidence.put("status","partial");try{AtomicJson.write(destination.resolve("native-goal-partial.json"),evidence);}catch(java.io.IOException failure){throw new AssertionError("Cannot save native stage evidence",failure);}System.out.println("VOTEX_GOAL_STAGE "+stage);});}
 private void profileWorkspaceControls(ClientGameTestContext context)throws Exception{
  context.getInput().resizeWindow(1600,900);context.waitTicks(3);open(context,"MODULES");
  context.runOnClient(mc->{Screen s=mc.gui.screen();float scale=screenScale(s);int x=integer(s,"left"),y=integer(s,"top"),w=integer(s,"w"),h=integer(s,"panelHeight");
   if(!s.mouseClicked(mouse((x+250)*scale,(y+30)*scale),false))throw new AssertionError("Titlebar did not start drag");s.mouseDragged(mouse((x+205)*scale,(y+45)*scale),-45*scale,15*scale);s.mouseReleased(mouse((x+205)*scale,(y+45)*scale));
   if(integer(s,"left")==x&&integer(s,"top")==y)throw new AssertionError("Window did not move");
   for(int corner=0;corner<4;corner++){
    x=integer(s,"left");y=integer(s,"top");w=integer(s,"w");h=integer(s,"panelHeight");int cx=x+(corner%2==0?0:w),cy=y+(corner<2?0:h),dx=corner%2==0?12:-12,dy=corner<2?10:-10;
    if(!s.mouseClicked(mouse(cx*scale,cy*scale),false))throw new AssertionError("Corner did not start resize");s.mouseDragged(mouse((cx+dx)*scale,(cy+dy)*scale),dx*scale,dy*scale);s.mouseReleased(mouse((cx+dx)*scale,(cy+dy)*scale));
    if(integer(s,"w")==w&&integer(s,"panelHeight")==h)throw new AssertionError("Corner did not resize");
   }
   int[] before={integer(s,"left"),integer(s,"top"),integer(s,"w"),integer(s,"panelHeight")};evidence.put("persistedWindowGeometry",before);
  });
  context.waitFor(mc->{try{var saved=JsonParser.parseString(Files.readString(VotexClient.INSTANCE.directory.resolve("modules.json"))).getAsJsonObject();return saved.get("workspaceWindow").equals(AtomicJson.GSON.toJsonTree(VotexClient.INSTANCE.modules.config().workspaceWindow));}catch(Exception e){return false;}},1000);
  context.runOnClient(mc->{int[] before=(int[])evidence.get("persistedWindowGeometry");var reloaded=new ModuleRegistry(VotexClient.INSTANCE.directory.resolve("modules.json"));reloaded.load();VotexClient.INSTANCE.modules.config().workspaceWindow=reloaded.config().workspaceWindow;
   mc.gui.setScreen(new VotexScreen(VotexClient.INSTANCE,"MODULES"));Screen next=mc.gui.screen();int[] after={integer(next,"left"),integer(next,"top"),integer(next,"w"),integer(next,"panelHeight")};if(!Arrays.equals(before,after))throw new AssertionError("Reopened window lost geometry");evidence.put("persistedWindowGeometry",before);
  });
  settle(context);shot(context,"resized-movable-mod-menu");
  for(String page:new String[]{"COSMETICS","ANIMATIONS","SETTINGS"}){open(context,page);shot(context,"resized-window-"+page.toLowerCase(Locale.ROOT));}
  context.getInput().resizeWindow(768,480);context.waitTicks(3);open(context,"MODULES");context.runOnClient(mc->{Screen s=mc.gui.screen();if(integer(s,"left")<0||integer(s,"top")<0||(integer(s,"left")+integer(s,"w"))*screenScale(s)>mc.getWindow().getGuiScaledWidth()+1)throw new AssertionError("Restored window leaves smaller monitor");});shot(context,"remembered-window-small-viewport");
  context.getInput().resizeWindow(1280,720);context.waitTicks(3);
  context.runOnClient(mc->{mc.gui.setScreen(null);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.player.setXRot(0);mc.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));});context.waitTicks(8);
  for(String id:new String[]{"wave","point","clap","salute","celebrate","bow","sit","dance"}){
   long before=context.computeOnClient(mc->FirstPersonEmotes.submittedFrames()),start=System.nanoTime();context.runOnClient(mc->EmotePlayer.play(id));context.waitFor(mc->System.nanoTime()-start>900_000_000L,1000);shot(context,"first-person-"+id);
   context.runOnClient(mc->{if(FirstPersonEmotes.submittedFrames()<=before+2)throw new AssertionError("No actual first-person arm submission: "+id);if(!mc.options.getCameraType().isFirstPerson())throw new AssertionError("Camera was forced out of first person");if(!mc.player.getMainHandItem().is(net.minecraft.world.item.Items.DIAMOND))throw new AssertionError("Gesture changed held item");});
  }
  context.runOnClient(mc->EmotePlayer.stop());long stop=System.nanoTime();context.waitFor(mc->System.nanoTime()-stop>1_200_000_000L,1000);long settled=context.computeOnClient(mc->FirstPersonEmotes.submittedFrames());context.waitTicks(5);context.runOnClient(mc->{if(FirstPersonEmotes.submittedFrames()!=settled)throw new AssertionError("First-person emote renderer remained active after fade");});shot(context,"first-person-normal-item-restored");
  evidence.put("firstPersonEmotes",Map.of("realSubmittedFrames",settled,"gestures",8,"cameraPreserved",true,"heldItemPreserved",true,"rendererInactiveAfterFade",true));
 }
 private void compactWorkspaceControls(ClientGameTestContext context){
  context.getInput().resizeWindow(768,480);context.waitTicks(3);
  for(String page:PAGES){open(context,page);context.runOnClient(mc->{var screen=mc.gui.screen();checkBounds(screen,new int[]{768,480});if(page.equals("COSMETICS")||page.equals("SKINS")){var first=screen.children().stream().filter(WardrobeCard.class::isInstance).map(WardrobeCard.class::cast).findFirst().orElseThrow();if(first.getY()<integer(screen,"scrollViewportTop")||first.getBottom()>integer(screen,"scrollViewportBottom"))throw new AssertionError("Compact wardrobe must show the preview and label of one complete card together");}});shot(context,"compact-"+page.toLowerCase(Locale.ROOT));}
  open(context,"PERFORMANCE");context.runOnClient(mc->{Screen screen=mc.gui.screen();float scale=screenScale(screen);if(!screen.mouseScrolled((integer(screen,"content")+40)*scale,(integer(screen,"scrollViewportTop")+20)*scale,0,-20))throw new AssertionError("Compact performance controls did not scroll");});settle(context);context.runOnClient(mc->{Screen screen=mc.gui.screen();var last=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(button->button.getMessage().getString().equals("JFR starten / stoppen")).findFirst().orElseThrow();if(!last.visible||last.getBottom()>integer(screen,"scrollViewportBottom"))throw new AssertionError("Performance footer hides the last controls");});shot(context,"compact-performance-scrolled");
  open(context,"ANIMATIONS");
  context.runOnClient(mc->{Screen screen=mc.gui.screen();float scale=screenScale(screen);int x=integer(screen,"content")+40,y=integer(screen,"top")+165;if(!screen.mouseScrolled(x*scale,y*scale,0,-20))throw new AssertionError("Compact content wheel was not handled");if(((Number)field(screen,"scrollTarget")).floatValue()<=((Number)field(screen,"pageScroll")).floatValue())throw new AssertionError("Smooth scroll skipped its target transition");});
  settle(context);shot(context,"compact-emotes-scrolled");
  boolean loop=context.computeOnClient(mc->EmotePlayer.loop());click(context,"Wiederholen");context.runOnClient(mc->{if(EmotePlayer.loop()==loop)throw new AssertionError("Scrolled playback toggle was not clickable");});click(context,"Wiederholen");
  context.runOnClient(mc->{Screen screen=mc.gui.screen();var covered=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(button->button.getMessage().getString().equals("Testen")).max(java.util.Comparator.comparingInt(button->((Number)((Map<?,?>)field(screen,"contentY")).get(button)).intValue())).orElseThrow();int original=((Number)((Map<?,?>)field(screen,"contentY")).get(covered)).intValue(),target=original-integer(screen,"scrollViewportBottom")+10;if(target<0||target>integer(screen,"h")-integer(screen,"panelHeight"))throw new AssertionError("Footer overlap fixture is outside scroll range");var frame=(dev.votex.ui.WorkspaceMotion.Frame)field(screen,"displayedMotion");float scale=screenScale(screen);double amount=(((Number)field(screen,"scrollTarget")).doubleValue()-target)/42d;screen.mouseScrolled(frame.screenX(integer(screen,"content")+40)*scale,frame.screenY(integer(screen,"scrollViewportTop")+20)*scale,0,amount);EmotePlayer.play("wave");});settle(context);
  context.runOnClient(mc->{Screen screen=mc.gui.screen();var stop=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(button->button.getMessage().getString().equals("Beenden")).findFirst().orElseThrow();double x=stop.getRight()-12,y=stop.getY()+stop.getHeight()/2d;if(screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).noneMatch(button->button.visible&&button.getMessage().getString().equals("Testen")&&button.isMouseOver(x,y)))throw new AssertionError("Test row does not overlap the fixed footer hit fixture");var frame=(dev.votex.ui.WorkspaceMotion.Frame)field(screen,"displayedMotion");float scale=screenScale(screen);var event=mouse(frame.screenX(x)*scale,frame.screenY(y)*scale);if(!screen.mouseClicked(event,false))throw new AssertionError("Fixed stop button rejected its visible edge");screen.mouseReleased(event);if(EmotePlayer.activeId()!=null)throw new AssertionError("Clipped emote row intercepted the fixed stop button");});shot(context,"compact-fixed-footer-click-isolation");evidence.put("fixedFooterClickIsolation",true);
  context.runOnClient(mc->{Screen screen=mc.gui.screen();Button row=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(button->button.getMessage().getString().equals("Sitzen")).findFirst().orElseThrow();int original=((Number)((Map<?,?>)field(screen,"contentY")).get(row)).intValue(),target=original-integer(screen,"top")-28;var frame=(dev.votex.ui.WorkspaceMotion.Frame)field(screen,"displayedMotion");float scale=screenScale(screen);double amount=(((Number)field(screen,"scrollTarget")).doubleValue()-target)/42d;if(!screen.mouseScrolled(frame.screenX(integer(screen,"content")+40)*scale,frame.screenY(integer(screen,"scrollViewportTop")+20)*scale,0,amount))throw new AssertionError("Clipped header fixture scroll was not handled");});settle(context);
  context.runOnClient(mc->{Screen screen=mc.gui.screen();float scale=screenScale(screen);Button clipped=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(button->button.getMessage().getString().equals("Sitzen")).findFirst().orElseThrow();int top=integer(screen,"top")+60;if(clipped.getY()>=top)throw new AssertionError("The clipping fixture is not above its content viewport");String before=LocalLooks.wheel[0];int left=integer(screen,"left"),y=integer(screen,"top");var event=mouse((clipped.getX()+20)*scale,(clipped.getY()+10)*scale);if(!screen.mouseClicked(event,false)||integer(screen,"windowEdge")!=16)throw new AssertionError("Header did not capture its window gesture above the clipped row");if(!LocalLooks.wheel[0].equals(before))throw new AssertionError("A clipped row changed the wheel");screen.mouseReleased(event);if(integer(screen,"windowEdge")!=0||integer(screen,"left")!=left||integer(screen,"top")!=y)throw new AssertionError("Click/release without drag moved or retained the window gesture");});
  open(context,"COSMETICS");click(context,"Alles  ⌄");shot(context,"compact-complete-category-popup");click(context,"Füße");context.runOnClient(mc->{if(integer(mc.gui.screen(),"category")!=9)throw new AssertionError("Last category was not reachable in compact window");});
  open(context,"SETTINGS");context.runOnClient(mc->{Screen screen=mc.gui.screen();float scale=screenScale(screen);screen.mouseScrolled((integer(screen,"content")+30)*scale,(integer(screen,"top")+160)*scale,0,-20);});settle(context);shot(context,"compact-settings-scrolled");context.runOnClient(mc->checkBounds(mc.gui.screen(),new int[]{768,480}));click(context,"");context.waitFor(mc->mc.gui.screen()==null,500);
  evidence.put("compactNativeControls",Map.of("window",List.of(768,480),"bodyFontMinimumPixels",14,"allEightWorkspaceViews",true,"smoothContentScroll",true,"scrolledPlaybackToggle",true,"clippedRowBehindHeaderDoesNotActivate",true,"headerCapturesAndReleasesWindowGesture",true,"lastCategoryAccessible",true,"headerCloseStillWorks",true));
 }
 private void secondaryWorkspaceControls(ClientGameTestContext context)throws Exception{
  Path cape=destination.resolve("secondary-ui-crop-fixture.png");var image=new java.awt.image.BufferedImage(64,96,java.awt.image.BufferedImage.TYPE_INT_ARGB);for(int y=0;y<96;y++)for(int x=0;x<64;x++)image.setRGB(x,y,0xff000000|(x*4<<16)|(y*2<<8)|0x90);javax.imageio.ImageIO.write(image,"png",cape.toFile());image.flush();
  var bounds=new ArrayList<Map<String,Object>>();
  for(int[] size:new int[][]{{768,480},{1280,720}}){
   context.getInput().resizeWindow(size[0],size[1]);context.waitTicks(3);String suffix=size[0]+"x"+size[1];
   context.runOnClient(mc->mc.gui.setScreen(new AppearanceScreen(VotexClient.INSTANCE,new VotexScreen(VotexClient.INSTANCE,"COSMETICS"))));settle(context);context.runOnClient(mc->bounds.add(checkBounds(mc.gui.screen(),size)));shot(context,"secondary-look-"+suffix);
   context.runOnClient(mc->search(mc.gui.screen()).setValue("Mein ungespeicherter Nametag"));click(context,"+");context.runOnClient(mc->{if(!search(mc.gui.screen()).getValue().equals("Mein ungespeicherter Nametag"))throw new AssertionError("Size control discarded the nametag draft");});click(context,"−");context.runOnClient(mc->search(mc.gui.screen()).setValue("Votex Client · Studio"));click(context,"Nametag speichern");context.runOnClient(mc->{if(!LocalLooks.text("nametag","").equals("Votex Client · Studio"))throw new AssertionError("Compact nametag save failed");});
   context.runOnClient(mc->{try{mc.gui.setScreen(new CapeCropScreen(VotexClient.INSTANCE,null,cape));}catch(Exception failure){throw new AssertionError(failure);}});settle(context);context.runOnClient(mc->{bounds.add(checkBounds(mc.gui.screen(),size));if(integer(mc.gui.screen(),"fh")<100)throw new AssertionError("Cape crop preview collapsed");});shot(context,"secondary-cape-portrait-"+suffix);
   click(context,"+");context.runOnClient(mc->{Screen screen=mc.gui.screen();float scale=screenScale(screen);double x=(integer(screen,"fx")+integer(screen,"fw")*.5)*scale,y=(integer(screen,"fy")+integer(screen,"fh")*.5)*scale;if(!screen.mouseClicked(mouse(x,y),false)||!screen.mouseDragged(mouse(x+12,y+8),12,8))throw new AssertionError("Readable cape crop stopped accepting drag");screen.mouseReleased(mouse(x+12,y+8));});click(context,"Fertige Cape-Textur");shot(context,"secondary-cape-atlas-"+suffix);
   context.runOnClient(mc->mc.gui.setScreen(new ModLibraryScreen(VotexClient.INSTANCE,null)));context.waitFor(mc->mc.gui.screen() instanceof ModLibraryScreen&&!((Boolean)field(mc.gui.screen(),"busy")),1000);settle(context);context.runOnClient(mc->{bounds.add(checkBounds(mc.gui.screen(),size));if(((List<?>)field(mc.gui.screen(),"installed")).size()<2)throw new AssertionError("No real Fabric library rows");});shot(context,"secondary-library-"+suffix);
   context.runOnClient(mc->{Screen screen=mc.gui.screen();float scale=screenScale(screen);double x=(integer(screen,"left")+16+integer(screen,"cardW")+10+25)*scale,y=(integer(screen,"top")+integer(screen,"listY")+22)*scale;if(!screen.mouseClicked(mouse(x,y),false)||field(mc.gui.screen(),"selected")==null)throw new AssertionError("Second library column is not selectable");bounds.add(checkBounds(mc.gui.screen(),size));if((Boolean)field(screen,"compact")){var query=search(screen);if(query.active)throw new AssertionError("Details drawer left the background query interactive");}});settle(context);shot(context,"secondary-library-details-"+suffix);
   context.runOnClient(mc->{Screen screen=mc.gui.screen();if((Boolean)field(screen,"compact")){if(!screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE,0,0))||field(screen,"selected")!=null)throw new AssertionError("Escape did not close the library details drawer");}});
   click(context,"CurseForge");shot(context,"secondary-library-curseforge-"+suffix);context.runOnClient(mc->bounds.add(checkBounds(mc.gui.screen(),size)));
  }
  context.getInput().resizeWindow(768,480);context.waitTicks(3);evidence.put("secondaryNativeChat",LibraryChatGameChecks.chat(context,destination));
  evidence.put("secondaryWorkspace",Map.of("bounds",bounds,"nametagDraftRetained",true,"nativeNametagSave",true,"nativeCapeCropDrag",true,"secondLibraryColumnSelectable",true,"compactDetailsIsolationAndEscape",true,"scope","Look, cape crop, actual installed library, API setup and styled chat; no new download, editor or microphone"));
 }
 private void arcadeHudWorkspaceControls(ClientGameTestContext context){
  var bounds=new ArrayList<Map<String,Object>>();
  for(int[] variant:new int[][]{{768,480,1},{768,480,2},{768,480,3},{1280,720,2}}){
   int[] size={variant[0],variant[1]};int gui=variant[2];context.getInput().resizeWindow(size[0],size[1]);context.waitTicks(3);
   context.runOnClient(mc->{mc.options.guiScale().set(gui);mc.getWindow().setGuiScale(gui);var core=VotexClient.INSTANCE;core.modules.config().positions.put("fps",new ModuleRegistry.Position(12,12));core.modules.config().appearance.put("fps",new ModuleRegistry.Appearance());core.hud.tick(mc);mc.gui.setScreen(new HudEditorScreen(core));bounds.add(checkBounds(mc.gui.screen(),size));var editor=mc.gui.screen();if(!editor.mouseClicked(mouse(17,17),false)||!editor.mouseDragged(mouse(87,37),70,20))throw new AssertionError("HUD drag was reinterpreted as toolbar coordinates at GUI scale "+gui);editor.mouseReleased(mouse(87,37));if(integer(editor,"selected")!=0)throw new AssertionError("Actual FPS element was not selected");});
   click(context,"+");context.runOnClient(mc->{var core=VotexClient.INSTANCE;if(core.modules.config().appearance.get("fps").scale!=110)throw new AssertionError("Scaled HUD plus icon did not change the selected element");String[] labels=(String[])field(core.hud,"labels");for(int i=0;i<labels.length;i++)if(core.modules.enabled(ModuleRegistry.HUD_MODULES.get(i).id())&&core.hud.width(i)<=mc.gui.screen().width-8&&UiText.width(labels[i])>Math.round(core.hud.width(i)/core.hud.scale(i))-21)throw new AssertionError("HUD renderer would truncate a label despite fitting its viewport: "+labels[i]);bounds.add(checkBounds(mc.gui.screen(),size));});settle(context);shot(context,"hud-readable-"+size[0]+"x"+size[1]+"-gui"+gui);
   click(context,"Leiste oben");context.runOnClient(mc->{var layout=(dev.votex.ui.HudEditorLayout)field(mc.gui.screen(),"layout");if(layout.y()!=12)throw new AssertionError("Top dock did not move");bounds.add(checkBounds(mc.gui.screen(),size));});shot(context,"hud-top-dock-"+size[0]+"x"+size[1]+"-gui"+gui);click(context,"Leiste unten");
   context.runOnClient(mc->{var editor=mc.gui.screen();var layout=(dev.votex.ui.HudEditorLayout)field(editor,"layout");if(!editor.mouseClicked(mouse((layout.x()+8)*layout.scale(),(layout.y()+10)*layout.scale()),false)||integer(editor,"dragging")!=-1)throw new AssertionError("Toolbar background leaked a world HUD click");});click(context,"Speichern");
   context.runOnClient(mc->{try{var core=VotexClient.INSTANCE;var loaded=new ModuleRegistry(core.directory.resolve("modules.json"));loaded.load();var live=core.modules.config().positions.get("fps");var stored=loaded.config().positions.get("fps");if(stored==null||live.x!=stored.x||live.y!=stored.y||loaded.config().appearance.get("fps").scale!=110)throw new AssertionError("Native HUD position/style save did not persist");}catch(java.io.IOException error){throw new AssertionError(error);}});
  }
  boolean originalFocus=context.computeOnClient(mc->mc.getWindow().isFocused());
  try{context.runOnClient(mc->windowFocus(mc,true));for(int[] size:new int[][]{{768,480},{1280,720}}){
   context.getInput().resizeWindow(size[0],size[1]);context.waitTicks(3);context.runOnClient(mc->{mc.options.guiScale().set(2);mc.getWindow().setGuiScale(2);mc.gui.setScreen(new ArcadeScreen(VotexClient.INSTANCE));bounds.add(checkBounds(mc.gui.screen(),size));});shot(context,"arcade-readable-menu-"+size[0]);
   for(String title:new String[]{"Pong","Tic-Tac-Toe","Flappy","Tetris"}){
    click(context,title);context.runOnClient(mc->{var screen=mc.gui.screen();bounds.add(checkBounds(screen,size));if(integer(screen,"by")+integer(screen,"bh")>integer(screen,"top")+integer(screen,"h")-40)throw new AssertionError("Game board crossed its footer");boolean focused=mc.getWindow().isFocused();try{windowFocus(mc,true);
     if(title.equals("Tic-Tac-Toe")){float scale=screenScale(screen);double x=(integer(screen,"bx")+integer(screen,"tile")*.5)*scale,y=(integer(screen,"by")+integer(screen,"tile")*.5)*scale;if(!screen.mouseClicked(mouse(x,y),false)||((ArcadeGames.TicTacToe)field(screen,"tic")).board[0]!=1)throw new AssertionError("Compact tic cell click failed");}
     if(title.equals("Flappy")){var flappy=(ArcadeGames.Flappy)field(screen,"flappy");if(screen.mouseClicked(mouse(1,1),false)||flappy.started)throw new AssertionError("Click outside the game started Flappy");screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_SPACE,0,0));if(!flappy.started)throw new AssertionError("Compact Flappy key input failed");}
     if(title.equals("Pong")){screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_W,0,0));windowFocus(mc,false);double before=((ArcadeGames.Pong)field(screen,"pong")).ballX;invokeStep(screen);if(!((Set<?>)field(screen,"keys")).isEmpty()||((ArcadeGames.Pong)field(screen,"pong")).ballX!=before)throw new AssertionError("Focus loss failed to pause/clear held controls");}
    }finally{windowFocus(mc,focused);}});
    if(title.equals("Tic-Tac-Toe")){click(context,"Zu zweit spielen");context.runOnClient(mc->{Screen screen=mc.gui.screen();var tic=(ArcadeGames.TicTacToe)field(screen,"tic");if(tic.ai||Arrays.stream(tic.board).anyMatch(value->value!=0))throw new AssertionError("Tic mode label did not select/reset local play");boolean focused=mc.getWindow().isFocused();try{windowFocus(mc,true);float scale=screenScale(screen);double x=(integer(screen,"bx")+integer(screen,"tile")*.5)*scale,y=(integer(screen,"by")+integer(screen,"tile")*.5)*scale;screen.mouseClicked(mouse(x,y),false);screen.mouseClicked(mouse(x+integer(screen,"tile")*scale,y),false);if(tic.board[0]!=1||tic.board[1]!=2)throw new AssertionError("Native local Tic did not alternate X/O");}finally{windowFocus(mc,focused);}});shot(context,"arcade-local-tic-"+size[0]);click(context,"Gegen KI spielen");context.runOnClient(mc->{if(!((ArcadeGames.TicTacToe)field(mc.gui.screen(),"tic")).ai)throw new AssertionError("Tic AI mode switch failed");});}
    if(title.equals("Tetris")){int oldX=context.computeOnClient(mc->((ArcadeGames.Tetris)field(mc.gui.screen(),"tetris")).x);click(context,"Nach links");context.runOnClient(mc->{if(((ArcadeGames.Tetris)field(mc.gui.screen(),"tetris")).x!=oldX-1)throw new AssertionError("Tetris left arrow missed");});click(context,"Nach rechts");click(context,"Drehen");click(context,"Fallen lassen");context.runOnClient(mc->{if(Arrays.stream(((ArcadeGames.Tetris)field(mc.gui.screen(),"tetris")).board).flatMapToInt(Arrays::stream).noneMatch(value->value!=0))throw new AssertionError("Compact native Tetris drop button missed");});}
    shot(context,"arcade-readable-"+title.toLowerCase(Locale.ROOT).replace('-','_')+"-"+size[0]);click(context,"Pause / Weiter");context.runOnClient(mc->{if(!((Boolean)field(mc.gui.screen(),"paused")))throw new AssertionError("Arcade pause button missed");});shot(context,"arcade-paused-"+title.toLowerCase(Locale.ROOT).replace('-','_')+"-"+size[0]);click(context,"Pause / Weiter");click(context,"Neu starten");context.runOnClient(mc->mc.gui.screen().onClose());
   }
  }}finally{context.runOnClient(mc->windowFocus(mc,originalFocus));}
  context.runOnClient(mc->{mc.options.guiScale().set(2);mc.getWindow().setGuiScale(2);});
  evidence.put("arcadeHudWorkspace",Map.of("bounds",bounds,"hudGuiScales",List.of(1,2,3),"nativeHudDragAndPersistence",true,"nativeDockSwitchAndOcclusion",true,"fourNativeGameInputs",true,"pauseAndRestartButtons",true,"focusStatePausesAndClearsKeys",true,"focusScope","isolated Window focus-state simulation; Fabric cancels native focus callbacks; not OS automation or a physical Alt-Tab test"));
 }
 private static void windowFocus(Minecraft mc,boolean focus){try{var window=mc.getWindow();var state=window.getClass().getDeclaredField("focused");state.setAccessible(true);state.setBoolean(window,focus);}catch(Exception error){throw new AssertionError(error);}}
 private static void invokeStep(Screen screen){try{var method=screen.getClass().getDeclaredMethod("step");method.setAccessible(true);method.invoke(screen);}catch(Exception error){throw new AssertionError(error);}}
 private void emoteWheelAndVoiceDialogsControls(ClientGameTestContext context){
  var bounds=new ArrayList<Map<String,Object>>();var originalCamera=context.computeOnClient(mc->mc.options.getCameraType());
  try{for(int[] variant:new int[][]{{768,480,2},{768,480,3},{1280,720,2}}){int[] size={variant[0],variant[1]};String label=size[0]+"x"+size[1]+"-gui"+variant[2];context.getInput().resizeWindow(size[0],size[1]);context.waitTicks(3);
   context.runOnClient(mc->{mc.options.guiScale().set(variant[2]);mc.getWindow().setGuiScale(variant[2]);mc.options.setCameraType(CameraType.FIRST_PERSON);EmotePlayer.stopLocal();mc.gui.setScreen(new EmoteWheel(VotexClient.INSTANCE));});settle(context);
   context.runOnClient(mc->{Screen screen=mc.gui.screen();bounds.add(checkBounds(screen,size));float scale=screenScale(screen);for(int[] card:(int[][])field(screen,"cards"))if(card[0]<0||card[1]<0||(card[0]+card[2])*scale>screen.width+.5||(card[1]+card[3])*scale>screen.height+.5)throw new AssertionError("A native wheel card clipped outside "+label);});shot(context,"wheel-readable-"+label);
   for(int slot=0;slot<8;slot++){int index=slot;if(slot>0){context.runOnClient(mc->mc.gui.setScreen(new EmoteWheel(VotexClient.INSTANCE)));settle(context);}context.runOnClient(mc->{Screen screen=mc.gui.screen();float scale=screenScale(screen);int[] card=((int[][])field(screen,"cards"))[index];float yaw=mc.player.getYRot(),pitch=mc.player.getXRot();if(!screen.mouseClicked(mouse((card[0]+card[2]/2d)*scale,(card[1]+card[3]/2d)*scale),false)||!LocalLooks.wheel[index].equals(EmotePlayer.activeId())||mc.gui.screen()!=null)throw new AssertionError("Native wheel clicked a wrong slot "+index+" at "+label);if(mc.player.getYRot()!=yaw||mc.player.getXRot()!=pitch)throw new AssertionError("A fitted wheel preview changed the player camera");EmotePlayer.stopLocal();});}
   context.runOnClient(mc->{mc.gui.setScreen(new EmoteWheel(VotexClient.INSTANCE));mc.gui.screen().keyPressed(new KeyEvent(GLFW.GLFW_KEY_8,0,0));if(!LocalLooks.wheel[7].equals(EmotePlayer.activeId())||mc.gui.screen()!=null)throw new AssertionError("Wheel number key lost slot8");EmotePlayer.stopLocal();mc.gui.setScreen(new VoiceConsentScreen(VotexClient.INSTANCE,new VotexScreen(VotexClient.INSTANCE,"SETTINGS")));});settle(context);context.runOnClient(mc->bounds.add(checkBounds(mc.gui.screen(),size)));shot(context,"voice-consent-readable-"+label);boolean permission=context.computeOnClient(mc->VoiceMicGate.allowed());click(context,"Später");context.runOnClient(mc->{if(VoiceMicGate.allowed()!=permission)throw new AssertionError("Dismiss changed microphone permission");mc.gui.setScreen(new VoiceSetupScreen(VotexClient.INSTANCE,new VotexScreen(VotexClient.INSTANCE,"FRIENDS")));});settle(context);context.runOnClient(mc->bounds.add(checkBounds(mc.gui.screen(),size)));shot(context,"voice-setup-readable-"+label);click(context,"Zurück");
  }}finally{context.runOnClient(mc->{mc.options.setCameraType(originalCamera);mc.options.guiScale().set(2);mc.getWindow().setGuiScale(2);EmotePlayer.stopLocal();});}
  evidence.put("wheelAndVoiceDialogs",Map.of("bounds",bounds,"allEightNativeCardClicks",true,"nativeNumberEightKey",true,"fittedOutfitPreview",true,"cameraPreserved",true,"voiceDismissPreservesPermission",true,"microphoneCapture","not requested, no consent button pressed"));
 }
 private void discoveryResizeControls(ClientGameTestContext context){
  context.getInput().resizeWindow(768,480);context.waitTicks(3);context.runOnClient(mc->mc.gui.setScreen(new VoiceSetupScreen(VotexClient.INSTANCE,null)));settle(context);click(context,"Voice in Mods & Shader suchen");awaitDiscovery(context);
  context.runOnClient(mc->{if(!field(mc.gui.screen(),"query").equals("Simple Voice Chat")||((List<?>)field(mc.gui.screen(),"projects")).isEmpty())throw new AssertionError("Voice setup did not lead to compatible live search: "+field(mc.gui.screen(),"status"));});shot(context,"library-voice-search-native-route");
  click(context,"Installiert");context.waitFor(mc->!((Boolean)field(mc.gui.screen(),"busy"))&&((List<?>)field(mc.gui.screen(),"installed")).size()>2,1000);context.runOnClient(mc->search(mc.gui.screen()).setValue(""));int last=context.computeOnClient(mc->(((List<?>)field(mc.gui.screen(),"installed")).size()-1)/(integer(mc.gui.screen(),"rows")*2));for(int i=0;i<last;i++)click(context,"›");
  context.getInput().resizeWindow(1280,720);context.waitTicks(4);context.runOnClient(mc->{Screen screen=mc.gui.screen();int count=((List<?>)field(screen,"installed")).size(),max=Math.max(0,(count-1)/(integer(screen,"rows")*2));if(count<3||integer(screen,"page")>max||integer(screen,"page")*integer(screen,"rows")*2>=count)throw new AssertionError("Installed library page is empty or out of range after resize");});shot(context,"library-last-installed-page-after-resize");
  click(context,"Entdecken");awaitDiscovery(context);click(context,"›");awaitDiscovery(context);
  context.getInput().resizeWindow(768,480);context.waitTicks(4);awaitDiscovery(context);shot(context,"library-live-discovery-compact-resize");
  context.getInput().resizeWindow(1280,720);context.waitTicks(4);awaitDiscovery(context);shot(context,"library-live-discovery-standard-resize");
  context.runOnClient(mc->{Screen screen=mc.gui.screen();Object stamp=field(screen,"completedSearch");evidence.put("nativeLibraryResize",Map.of("voiceSetupSearchQuery","Simple Voice Chat","liveVoiceResults",true,"installedLastPageClamped",true,"discoveryResizeRefetched",true,"acceptedRequestOffset",integer(stamp,"offset"),"acceptedRequestLimit",integer(stamp,"limit"),"visibleProjectIds",((List<?>)field(screen,"projects")).stream().map(ModLibraryService.Project.class::cast).map(ModLibraryService.Project::id).toList(),"scope","actual Modrinth HTTPS search; no mod download or installation"));});
 }
 private void awaitDiscovery(ClientGameTestContext context){context.waitFor(mc->{if(!(mc.gui.screen() instanceof ModLibraryScreen))return false;Screen screen=mc.gui.screen();Object stamp=field(screen,"completedSearch");return !((Boolean)field(screen,"busy"))&&stamp!=null&&integer(stamp,"limit")==integer(screen,"rows")*2&&integer(stamp,"offset")==integer(screen,"page")*integer(screen,"rows")*2;},1200);context.waitTicks(2);}
 private void seed(VotexClient core)throws Exception{
  JsonObject data=new JsonObject();data.addProperty("schemaVersion",1);data.addProperty("selectedSkin","qa-votex");data.addProperty("selectedCosmetic","qa-aurora");var skins=new JsonArray();
  for(String id:new String[]{"votex","studio","original"}){Path out=core.directory.resolve("assets/skin-qa-"+id+".png");Files.createDirectories(out.getParent());try(var in=getClass().getResourceAsStream("/assets/votex_client_test/skins/"+id+".png")){if(in==null)throw new AssertionError("Missing real test skin "+id);Files.copy(in,out,StandardCopyOption.REPLACE_EXISTING);}var item=new JsonObject();item.addProperty("id","qa-"+id);item.addProperty("name",switch(id){case "votex"->"Mein Votex Skin";case "studio"->"Studio Explorer";default->"Original Explorer";});skins.add(item);}
  data.add("skins",skins);JsonArray looks=new JsonArray();
  for(String[] outfit:new String[][]{{"qa-aurora","Aurora","wings","cloud-feathers"},{"qa-midnight","Midnight","head","creator-fox-hat"}}){JsonObject item=new JsonObject(),equipment=new JsonObject();item.addProperty("id",outfit[0]);item.addProperty("name",outfit[1]);item.addProperty("scale",100);item.addProperty("cape",false);item.addProperty("nametag","Votex Client");equipment.addProperty(outfit[2],outfit[3]);item.add("equipment",equipment);looks.add(item);}data.add("cosmetics",looks);data.add("customModels",new JsonArray());
  JsonArray custom=new JsonArray();JsonObject clip=new JsonObject();clip.addProperty("id","custom-emote-fade");clip.addProperty("name","Meine eigene Begrüßung");clip.addProperty("duration",3);JsonArray keys=new JsonArray();for(int f=0;f<3;f++){JsonObject key=new JsonObject();key.addProperty("time",f*.5);JsonArray pose=new JsonArray();for(int k=0;k<19;k++)pose.add(k==11?(f==1?-2.1:0):0);key.add("pose",pose);keys.add(key);}clip.add("keyframes",keys);custom.add(clip);data.add("customEmotes",custom);data.add("emoteWheel",AtomicJson.GSON.toJsonTree(EmotePlayer.IDS));AtomicJson.write(core.directory.resolve("presets.json"),data);LocalLooks.reload(core.directory);Wardrobe.refresh(core);Wardrobe.launcher(core);
  if(Wardrobe.skins().size()!=3||Wardrobe.emotes().size()!=9||Wardrobe.cosmetics().size()<45)throw new AssertionError("Seeded native catalogs unavailable");
 }
 private void keyBindings(ClientGameTestContext context){
  context.runOnClient(mc->mc.gui.setScreen(null));context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);context.waitFor(mc->mc.gui.screen() instanceof VotexScreen,300);shot(context,"home-entrance");long next=System.nanoTime()+180_000_000L;context.waitFor(mc->System.nanoTime()>=next,300);shot(context,"home-entrance-transition");context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);context.waitFor(mc->mc.gui.screen()==null,300);
  context.getInput().holdKey(GLFW.GLFW_KEY_B);context.waitFor(mc->mc.gui.screen() instanceof EmoteWheel,300);context.getInput().releaseKey(GLFW.GLFW_KEY_B);context.waitFor(mc->mc.gui.screen()==null,300);evidence.put("physicalBindings","actual rightShift opens animated home, Esc closes, B held/released opens/closes wheel");
 }
 private void fontProof(Minecraft mc)throws Exception{
  var source=((FontAccessor)mc.font).votex$provider().glyphs(UiText.STYLE.getFont());
  var definition=new TrueTypeGlyphProviderDefinition(Identifier.fromNamespaceAndPath("votex_client","noto-sans-regular.ttf"),11,4,TrueTypeGlyphProviderDefinition.Shift.NONE,"");
  var glyphs=new LinkedHashMap<String,Object>();boolean fractional=false;
  try(var direct=definition.unpack().left().orElseThrow().load(mc.getResourceManager())){
   for(int point:new int[]{'A','i','W','g','Ä','ß'}){float actual=source.getGlyph(point).info().getAdvance(),expected=direct.getGlyph(point).info().getAdvance();if(Math.abs(actual-expected)>.0001)throw new AssertionError("Votex font fell back for "+Character.toString(point)+": "+actual+" vs Noto "+expected);fractional|=Math.abs(actual-Math.round(actual))>.01;glyphs.put(Character.toString(point),actual);}
  }
  if(!fractional||UiText.width("iiii")>=UiText.width("WWWW"))throw new AssertionError("Sans proportional glyphs did not load");
  if(UiText.font().width("Votex iii WWW")!=mc.font.width(UiText.component("Votex iii WWW")))throw new AssertionError("EditBox metric font differs from displayed Votex font");
  evidence.put("font",Map.of("resource","votex_client:noto-sans-regular.ttf","style",UiText.STYLE.getFont().toString(),"glyphAdvancesMatchLoadedTTF",glyphs,"editBoxMetricsMatch",true));
 }
 private void reviewModules(ClientGameTestContext context) throws Exception {
  context.runOnClient(mc->{
   if(!mc.gameDirectory.toString().toLowerCase(Locale.ROOT).contains("clientgametest"))throw new AssertionError("Private world required");
   var core=VotexClient.INSTANCE;
   destination=core.directory.resolve("goal-evidence");Files.createDirectories(destination);
   core.modules.config().enabled.put("fps",true);
   core.modules.config().enabled.put("item_physics",false);
   core.modules.config().menuAnimations=false;
  });
  moduleControls(context);
  hudControls(context);
  context.runOnClient(mc->{
   var core=VotexClient.INSTANCE;
   var screen=new VotexScreen(core,"MODULES");mc.gui.setScreen(screen);
   var input=search(screen);screen.setFocused(input);
   input.setValue("haltbarkeit armor");
   if(search(screen)!=input||!input.isFocused()||((List<?>)field(screen,"matching")).size()!=1)
    throw new AssertionError("Search lost input state or failed multiword alias");
  });
  click(context,"Aktiv");
  context.runOnClient(mc->search(mc.gui.screen()).setValue("Item Physics"));
  click(context,"Item Physics");
  context.runOnClient(mc->{if(!((List<?>)field(mc.gui.screen(),"matching")).isEmpty())throw new AssertionError("Active filter retained disabled module");});
  shot(context,"modules-empty-state");click(context,"Filter zurücksetzen");
  var server=context.computeOnClient(mc->mc.getSingleplayerServer());
  var playerId=context.computeOnClient(mc->mc.player.getUUID());
  var fixture=server.submit(()->{
   var p=server.getPlayerList().getPlayer(playerId);
   var helmet=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET);helmet.setDamageValue(64);
   p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,helmet);
   p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
   p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED,1200,1));
   var base=p.blockPosition().offset(0,-1,4);
   p.level().setBlock(base,net.minecraft.world.level.block.Blocks.SHROOMLIGHT.defaultBlockState(),3);
   return true;
  });
  context.waitFor(mc->fixture.isDone(),1000);fixture.join();
  context.runOnClient(mc->{
   var core=VotexClient.INSTANCE;var config=core.modules.config();
   config.enabled.replaceAll((id,enabled)->false);
   for(String id:List.of("armor","effects","direction","fps","cps","keystrokes","frametime","target"))config.enabled.put(id,true);
   config.positions.clear();
   config.positions.put("effects",new ModuleRegistry.Position(180,16));
   config.positions.put("cps",new ModuleRegistry.Position(12,55));
   config.positions.put("frametime",new ModuleRegistry.Position(12,97));
   config.positions.put("keystrokes",new ModuleRegistry.Position(12,140));
   config.positions.put("direction",new ModuleRegistry.Position(370,16));
   config.positions.put("target",new ModuleRegistry.Position(370,72));
   mc.gui.setScreen(null);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.player.setYRot(0);mc.player.setXRot(42);
  });
  context.waitTicks(14);shot(context,"modules-specialized-hud");
  context.runOnClient(mc->{
   var config=VotexClient.INSTANCE.modules.config();
   config.enabled.replaceAll((id,enabled)->false);config.enabled.put("light",true);
  });
  context.waitTicks(12);shot(context,"modules-light-overlay");
  var daylight=server.submit(()->{
   var player=server.getPlayerList().getPlayer(playerId);
   var base=player.blockPosition().offset(0,-1,4);
   player.level().setBlock(base,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
   server.setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
   server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set noon");
   return true;
  });
  context.waitFor(mc->daylight.isDone(),1000);daylight.join();
  context.waitTicks(32);shot(context,"modules-light-day-risk");
  var nighttime=server.submit(()->{
   server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set midnight");
   return true;
  });
  context.waitFor(mc->nighttime.isDone(),1000);nighttime.join();
  context.waitTicks(24);shot(context,"modules-light-night-risk");
  context.runOnClient(mc->VotexClient.INSTANCE.modules.config().lightRisksOnly=true);
  context.waitTicks(10);shot(context,"modules-light-risks-only");
  context.runOnClient(mc->{VotexClient.INSTANCE.modules.config().enabled.put("light",false);});
  context.waitTicks(2);shot(context,"modules-light-disabled");
  evidence.put("itemPhysics",ItemPhysicsGameChecks.run(context,destination));
  context.runOnClient(mc->{
   var config=VotexClient.INSTANCE.modules.config();
   config.enabled.replaceAll((id,enabled)->false);
   config.positions.clear();
   for(String id:List.of("coordinates","speed","movement","world_time","clock","biome","dimension","day","session","players","weather","chunk"))config.enabled.put(id,true);
   mc.gui.setScreen(null);
  });
  context.getInput().holdKey(GLFW.GLFW_KEY_W);
  try {
   context.waitFor(mc->(double)field(VotexClient.INSTANCE.hud,"horizontalSpeed")>1,160);
   shot(context,"modules-world-context-moving");
  }finally{context.getInput().releaseKey(GLFW.GLFW_KEY_W);}
  context.waitFor(mc->(double)field(VotexClient.INSTANCE.hud,"horizontalSpeed")<.1,160);
  shot(context,"modules-world-context-idle");
  evidence.put("worldContext","native forward input raises measured speed, release returns to idle; contextual HUD screenshots retained");
  context.runOnClient(mc->{
   if(!VotexClient.INSTANCE.lastError.isEmpty())throw new AssertionError(VotexClient.INSTANCE.lastError);
   AtomicJson.write(destination.resolve("native-modules-review-proof.json"),evidence);
  });
 }
 private void moduleControls(ClientGameTestContext context){
  open(context,"MODULES");shot(context,"modules-continuous-scroll-top");context.runOnClient(mc->{var screen=(VotexScreen)mc.gui.screen();var modules=(List<?>)field(screen,"matching");if(modules.size()!=ModuleRegistry.MODULES.size()||modules.size()<20)throw new AssertionError("Module workspace omitted catalog entries");if(integer(screen,"h")<=integer(screen,"panelHeight"))throw new AssertionError("Full module catalog must use continuous scrolling");var buttons=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();if(buttons.stream().anyMatch(b->b.getMessage().getString().equals("Weiter")||b.getMessage().getString().equals("Seite 1")))throw new AssertionError("Module list still exposes page controls");String last=((ModuleRegistry.Module)modules.getLast()).name();if(buttons.stream().noneMatch(b->b.getMessage().getString().equals(last)))throw new AssertionError("Last module card is absent");float scale=screenScale(screen);var frame=(dev.votex.ui.WorkspaceMotion.Frame)field(screen,"displayedMotion");double x=frame.screenX(integer(screen,"content")+Math.max(10,integer(screen,"cw")/2))*scale,y=frame.screenY(integer(screen,"scrollViewportTop")+24)*scale;if(!screen.mouseScrolled(x,y,0,-100))throw new AssertionError("Module list rejected scroll input");if((float)field(screen,"scrollTarget")!=integer(screen,"h")-integer(screen,"panelHeight"))throw new AssertionError("Scroll did not reach the last module row");
  });context.waitFor(mc->{var screen=(VotexScreen)mc.gui.screen();return Math.abs((float)field(screen,"pageScroll")-(float)field(screen,"scrollTarget"))<.5f;},1000);context.runOnClient(mc->{var screen=(VotexScreen)mc.gui.screen();var buttons=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();String last=((ModuleRegistry.Module)((List<?>)field(screen,"matching")).getLast()).name();var lastCard=buttons.stream().filter(b->b.getMessage().getString().equals(last)).findFirst().orElseThrow();if(!lastCard.visible||lastCard.getY()<integer(screen,"scrollViewportTop")||lastCard.getY()+lastCard.getHeight()>integer(screen,"scrollViewportBottom"))throw new AssertionError("Last module card was not fully reachable at scroll end");if(search(screen).getY()!=integer(screen,"top")+82||buttons.stream().filter(b->b.getMessage().getString().equals("Alle")).findFirst().orElseThrow().getY()!=integer(screen,"top")+115)throw new AssertionError("Search/filter header moved with the module list");});context.waitTicks(2);shot(context,"modules-continuous-scroll-end");evidence.put("moduleContinuousScroll","all module cards rendered in one list; last card fully reachable; search/category controls pinned; no pager");open(context,"MODULES");boolean before=context.computeOnClient(mc->VotexClient.INSTANCE.modules.enabled("fps"));click(context,"Bildrate");context.runOnClient(mc->{if(VotexClient.INSTANCE.modules.enabled("fps")==before)throw new AssertionError("Module card did not toggle");});click(context,"Bildrate");
  context.runOnClient(mc->search(mc.gui.screen()).setValue("Item Physics"));click(context,"Item Physics");context.runOnClient(mc->{if(!VotexClient.INSTANCE.modules.enabled("item_physics"))throw new AssertionError("Filtered real module did not enable");});
  context.waitFor(mc->{try{var registry=new ModuleRegistry(VotexClient.INSTANCE.directory.resolve("modules.json"));registry.load();return registry.enabled("fps")&&registry.enabled("item_physics");}catch(Exception e){return false;}},300);evidence.put("moduleControls","native card hit-test; search; toggles persisted in modules.json");open(context,"MODULES");shot(context,"modules-enabled-and-search-tested");
 }
 private void hudControls(ClientGameTestContext context){
  context.runOnClient(mc->{var core=VotexClient.INSTANCE;core.hud.tick(mc);mc.gui.setScreen(new HudEditorScreen(core));var screen=mc.gui.screen();var position=core.hud.position(0);double x=position.x+5,y=position.y+5;var start=mouse(x,y);if(!screen.mouseClicked(start,false))throw new AssertionError("HUD item did not receive hit");if(!screen.mouseDragged(mouse(x+120,y+80),120,80))throw new AssertionError("HUD drag rejected");screen.mouseReleased(mouse(x+120,y+80));});click(context,"+");shot(context,"hud-drag-and-style");click(context,"Speichern");
  context.waitFor(mc->{try{var registry=new ModuleRegistry(VotexClient.INSTANCE.directory.resolve("modules.json"));registry.load();var p=registry.config().positions.get("fps");var a=registry.config().appearance.get("fps");return p!=null&&p.x>=100&&p.y>=72&&a!=null&&a.scale==110;}catch(Exception e){return false;}},300);evidence.put("hudControls","native pointer drag and scale button; new position/110percent persisted");
 }
 private void wardrobeControls(ClientGameTestContext context){
  open(context,"COSMETICS");click(context,"Alles  ⌄");click(context,"Capes");context.runOnClient(mc->{if(integer(mc.gui.screen(),"category")!=2)throw new AssertionError("Dropdown click reached underlying cosmetic instead of Cape option");});shot(context,"wardrobe-category-capes");click(context,"Capes  ⌄");click(context,"Alles");context.runOnClient(mc->search(mc.gui.screen()).setValue("Seraph Wings"));click(context,"Seraph Wings");context.runOnClient(mc->{if(Wardrobe.equipped(Wardrobe.cosmetics().stream().filter(i->i.id().equals("cloud-feathers")).findFirst().orElseThrow()))throw new AssertionError("Already-equipped wings should toggle off");});click(context,"Seraph Wings");context.runOnClient(mc->{if(!Wardrobe.equipped(Wardrobe.cosmetics().getFirst()))throw new AssertionError("Catalog item did not equip");});open(context,"COSMETICS");shot(context,"cosmetics-real-catalog-and-equipped-model");
  open(context,"SKINS");click(context,"Studio Explorer");context.runOnClient(mc->{if(!LocalLooks.assetSkinId().equals("qa-studio"))throw new AssertionError("Skin selection did not change native texture");});shot(context,"skins-selected-real-texture");
  context.waitFor(mc->{try{var json=JsonParser.parseString(Files.readString(VotexClient.INSTANCE.directory.resolve("ingame-look.json"))).getAsJsonObject();return json.get("skin").getAsString().equals("qa-studio")&&json.getAsJsonObject("equipment").get("wings").getAsString().equals("cloud-feathers");}catch(Exception e){return false;}},300);evidence.put("wardrobeControls","real authored catalog equip/unequip, native PNG skin selection, persisted selection");
 }
 private void emoteControls(ClientGameTestContext context){
  open(context,"ANIMATIONS");click(context,"2");click(context,"Sitzen");context.runOnClient(mc->{if(!LocalLooks.wheel[1].equals("sit"))throw new AssertionError("Wheel slot selection failed");});
  // Pick a genuinely different emote for slot2, then exercise playback through its real row.
  click(context,"Winken");context.runOnClient(mc->{if(!LocalLooks.wheel[1].equals("wave"))throw new AssertionError("Wheel reassignment failed");});
  context.runOnClient(mc->search(mc.gui.screen()).setValue("Meine eigene"));click(context,"Meine eigene Begrüßung");click(context,"Testen");context.runOnClient(mc->{if(!"custom-emote-fade".equals(EmotePlayer.activeId()))throw new AssertionError("Custom emote row did not start playback");});
  click(context,"Tempo: 1.00×");click(context,"Wiederholen");context.runOnClient(mc->{if(EmotePlayer.speed()!=1.25||!EmotePlayer.loop())throw new AssertionError("Playback controls failed");});shot(context,"custom-emote-native-playback-controls");click(context,"Animation beenden");
  context.waitFor(mc->{try{var json=JsonParser.parseString(Files.readString(VotexClient.INSTANCE.directory.resolve("ingame-look.json"))).getAsJsonObject();return json.get("speed").getAsDouble()==1.25&&json.get("loop").getAsBoolean()&&json.getAsJsonArray("wheel").get(1).getAsString().equals("custom-emote-fade");}catch(Exception e){return false;}},300);
  context.runOnClient(mc->{Wardrobe.playback(VotexClient.INSTANCE,1,false);EmotePlayer.stop();});evidence.put("emoteControls","native slot reassignment, launcher-authored custom playback, speed/loop persisted, stop");
 }
 private void previewCacheControls(ClientGameTestContext context)throws Exception{
  context.runOnClient(mc->{
   var renderer=mc.getEntityRenderDispatcher().getRenderer(mc.player);var state=(net.minecraft.client.renderer.entity.state.AvatarRenderState)renderer.createRenderState(mc.player,1);
   var selected=LocalLooks.skin(state);if(selected==null)throw new AssertionError("Selected real skin missing before cache sweep");var manager=mc.getTextureManager();var active=manager.getTexture(selected);
   var cacheField=Wardrobe.class.getDeclaredField("PREVIEWS");cacheField.setAccessible(true);var cache=(Map<?,?>)cacheField.get(null);int count=0;
   for(var item:Wardrobe.cosmetics())if(!item.id().startsWith("custom-")&&!item.id().startsWith("ingame-")){var texture=Wardrobe.preview(item.id());if(texture!=null){manager.getTexture(texture);count++;if(cache.size()>32)throw new AssertionError("Cosmetic thumbnail GPU cache grew beyond32");}}
   if(count<110||cache.size()!=32)throw new AssertionError("Thumbnail sweep did not exercise the reviewed library");
   var first=Wardrobe.cosmetics().getFirst().id();if(!Wardrobe.preview(first).equals(Wardrobe.preview(first)))throw new AssertionError("Evicted thumbnail did not reload");
   mc.gui.setScreen(null);if(!cache.isEmpty())throw new AssertionError("Leaving the wardrobe retained thumbnail textures");
   if(!selected.equals(LocalLooks.skin(state))||manager.getTexture(selected)!=active)throw new AssertionError("Releasing thumbnails disposed the equipped skin");
   evidence.put("boundedWardrobeGpuCache",Map.of("distinctPreviewsLoaded",count,"maximumRetainedPreviews",32,"releasedOnScreenRemoval",true,"selectedSkinTextureRetained",true));
  });
 }
 private void appearanceControls(ClientGameTestContext context)throws Exception{
  open(context,"COSMETICS");click(context,"Deinen Look anpassen");context.runOnClient(mc->search(mc.gui.screen()).setValue("Votex Client · Studio"));click(context,"Nametag speichern");click(context,"+");context.runOnClient(mc->{if(!LocalLooks.text("nametag","").equals("Votex Client · Studio")||Math.abs(LocalLooks.scale()-1.05)>.0001)throw new AssertionError("Native nametag/scale controls did not apply");});shot(context,"appearance-nametag-and-scale"); context.runOnClient(mc->{var screen=mc.gui.screen();var orbit=(AvatarPreview)field(screen,"preview");float yaw=mc.player.getYRot(),pitch=mc.player.getXRot();double sx=(integer(screen,"x")+550)*screenScale(screen),sy=(integer(screen,"y")+200)*screenScale(screen);if(!screen.mouseClicked(mouse(sx,sy),false)||!screen.mouseDragged(mouse(sx+240,sy+45),240,45))throw new AssertionError("Preview orbit did not handle pointer drag");screen.mouseReleased(mouse(sx+240,sy+45));if(!screen.mouseScrolled(sx,sy,0,2))throw new AssertionError("Preview zoom did not handle scroll");if(mc.player.getYRot()!=yaw||mc.player.getXRot()!=pitch)throw new AssertionError("Preview interaction turned the world player");});shot(context,"appearance-orbit-back-and-zoom");
  byte[] original=context.computeOnClient(mc->Files.readAllBytes(VotexClient.INSTANCE.directory.resolve("assets/skin-qa-votex.png")));
  context.runOnClient(mc->mc.gui.setScreen(new CapeCropScreen(VotexClient.INSTANCE,mc.gui.screen(),VotexClient.INSTANCE.directory.resolve("assets/skin-qa-votex.png"))));click(context,"+");
  context.runOnClient(mc->{Screen screen=mc.gui.screen();float scale=screenScale(screen);double x=(integer(screen,"fx")+integer(screen,"fw")*.5)*scale,y=(integer(screen,"fy")+integer(screen,"fh")*.5)*scale;if(!screen.mouseClicked(mouse(x,y),false)||!screen.mouseDragged(mouse(x+28,y+14),28,14))throw new AssertionError("Native cape crop drag failed");screen.mouseReleased(mouse(x+28,y+14));});shot(context,"cape-crop-real-png-and-controls");click(context,"Cape übernehmen");
  context.runOnClient(mc->{var core=VotexClient.INSTANCE;String asset=LocalLooks.assetCapeId();if(!asset.matches("ingame-[a-f0-9-]{36}"))throw new AssertionError("Cape selection missing after native crop save");var output=javax.imageio.ImageIO.read(core.directory.resolve("assets/cape-"+asset+".png").toFile());if(output.getWidth()!=64||output.getHeight()!=32)throw new AssertionError("Crop did not generate Minecraft cape dimensions");if(!Arrays.equals(original,Files.readAllBytes(core.directory.resolve("assets/skin-qa-votex.png"))))throw new AssertionError("Cape crop modified its source PNG");});
  context.waitFor(mc->{try{var json=JsonParser.parseString(Files.readString(VotexClient.INSTANCE.directory.resolve("ingame-look.json"))).getAsJsonObject();return json.get("nametag").getAsString().equals("Votex Client · Studio")&&json.get("scale").getAsInt()==105&&json.get("customCape").getAsString().startsWith("ingame-");}catch(Exception e){return false;}},300);
  String imported=context.computeOnClient(mc->LocalLooks.assetCapeId()); open(context,"COSMETICS");context.runOnClient(mc->search(mc.gui.screen()).setValue("Eigenes Cape"));click(context,"Eigenes Cape 1");context.runOnClient(mc->{if(LocalLooks.enabled("cape"))throw new AssertionError("Imported cape toggle did not unequip");});click(context,"Eigenes Cape 1");context.runOnClient(mc->{Wardrobe.refresh(VotexClient.INSTANCE);if(!LocalLooks.assetCapeId().equals(imported)||Wardrobe.cosmetics().stream().noneMatch(i->i.id().equals(imported)))throw new AssertionError("Imported cape missing after wardrobe reload");});shot(context,"imported-cape-library-and-reselection"); evidence.put("appearanceControls","native nametag/scale; real orbit/zoom preserves world rotation; portrait motif maps to bothcape faces; zoom/drag PNG; 64x32 export, library toggle/reselect and reload; source unchanged");
 }
 private void settingsControls(ClientGameTestContext context){open(context,"SETTINGS");click(context,"Menü-Animationen");context.runOnClient(mc->{if(VotexClient.INSTANCE.modules.config().menuAnimations)throw new AssertionError("Animation switch failed");mc.gui.screen().onClose();if(mc.gui.screen()!=null)throw new AssertionError("Disabled animation still delayed closing");});open(context,"SETTINGS");click(context,"Menü-Animationen");evidence.put("animationSwitch","native switch disables close animation immediately");}
 private void arcadeControls(ClientGameTestContext context){
  context.runOnClient(mc->mc.gui.setScreen(new ArcadeScreen(VotexClient.INSTANCE)));shot(context,"arcade-selection");
  for(String title:new String[]{"Pong","Tic-Tac-Toe","Flappy","Tetris"}){
   click(context,title);context.runOnClient(mc->{var screen=mc.gui.screen();if(title.equals("Tic-Tac-Toe")){int x=integer(screen,"bx"),y=integer(screen,"by"),tile=integer(screen,"tile");float scale=screenScale(screen);screen.mouseClicked(mouse((x+tile*.5)*scale,(y+tile*.5)*scale),false);ArcadeGames.TicTacToe tic=(ArcadeGames.TicTacToe)field(screen,"tic");if(tic.board[0]!=1)throw new AssertionError("Native tic-tac-toe cell click failed");}if(title.equals("Flappy")){screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_SPACE,0,0));ArcadeGames.Flappy flappy=(ArcadeGames.Flappy)field(screen,"flappy");if(!flappy.started)throw new AssertionError("Native Flappy space key failed");}if(title.equals("Tetris")){screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_SPACE,0,0));ArcadeGames.Tetris tetris=(ArcadeGames.Tetris)field(screen,"tetris");if(Arrays.stream(tetris.board).flatMapToInt(Arrays::stream).noneMatch(i->i!=0))throw new AssertionError("Native Tetris hard-drop key failed");}});context.waitTicks(2);shot(context,"arcade-"+title.toLowerCase(Locale.ROOT).replace('-','_'));context.runOnClient(mc->mc.gui.screen().onClose());
  }evidence.put("arcadeControls","4 screens; real native Tic-Tac-Toe cell, Flappy space and Tetris hard drop");
 }
 private void open(ClientGameTestContext context,String page){context.runOnClient(mc->{if(!VotexClient.INSTANCE.lastError.isEmpty())throw new AssertionError(VotexClient.INSTANCE.lastError);mc.gui.setScreen(new VotexScreen(VotexClient.INSTANCE,page));});settle(context);}
 private void settle(ClientGameTestContext context){long ready=System.nanoTime()+600_000_000L;context.waitFor(mc->System.nanoTime()>=ready,1000);context.waitTicks(2);}
 private void shot(ClientGameTestContext context,String name){context.takeScreenshot(TestScreenshotOptions.of("votex-goal-"+name).withDestinationDir(destination).disableCounterPrefix());}
 private static EditBox search(Screen screen){return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow(()->new AssertionError("Search box not present"));}
 private static float screenScale(Screen screen){if(screen instanceof HudEditorScreen)return ((dev.votex.ui.HudEditorLayout)field(screen,"layout")).scale();if(screen instanceof VotexScreen)return ((Number)field(screen,"uiScale")).floatValue();if(screen instanceof AppearanceScreen||screen instanceof ModLibraryScreen||screen instanceof VoiceSetupScreen||screen instanceof VoiceConsentScreen)return ((Number)field(screen,"scale")).floatValue();if(screen instanceof CapeCropScreen||screen instanceof EmoteWheel)return ((Number)field(screen,"uiScale")).floatValue();return screen instanceof ArcadeScreen?((Number)field(screen,"scale")).floatValue():1;}
 private void click(ClientGameTestContext context,String label){context.runOnClient(mc->{Screen screen=mc.gui.screen();Button button=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(label)).findFirst().orElseThrow(()->new AssertionError("Native button missing: "+label));float scale=screenScale(screen);double x=button.getX()+button.getWidth()*.5,y=button.getY()+button.getHeight()*.5;if(screen instanceof VotexScreen||screen instanceof AppearanceScreen||screen instanceof CapeCropScreen||screen instanceof ModLibraryScreen||screen instanceof VoiceSetupScreen||screen instanceof VoiceConsentScreen){var frame=(dev.votex.ui.WorkspaceMotion.Frame)field(screen,"displayedMotion");x=frame.screenX(x);y=frame.screenY(y);}var event=mouse(x*scale,y*scale);if(!screen.mouseClicked(event,false))throw new AssertionError("Native hit-test rejected "+label);screen.mouseReleased(event);});context.waitTicks(2);}
 private Map<String,Object> checkBounds(Screen screen,int[] physical){float scale=screenScale(screen);for(var child:screen.children())if(child instanceof AbstractWidget widget&&widget.visible){double x=widget.getX()*scale,y=widget.getY()*scale,right=x+widget.getWidth()*scale,bottom=y+widget.getHeight()*scale;
  if(screen instanceof VotexScreen&&((Map<?,?>)field(screen,"contentY")).containsKey(widget)){double clipTop=(integer(screen,"top")+60)*scale,clipBottom=(integer(screen,"top")+integer(screen,"panelHeight")-12)*scale;y=Math.max(y,clipTop);bottom=Math.min(bottom,clipBottom);if(bottom<=y)throw new AssertionError("Offscreen content widget remained visible");}
  if(x<-.1||y<-.1||right>screen.width+.5||bottom>screen.height+.5)throw new AssertionError("Clipped native widget "+widget.getMessage().getString()+" at "+Arrays.toString(physical)+": "+x+","+y+","+right+","+bottom+" in "+screen.width+"x"+screen.height);
 }double body=11*scale*Minecraft.getInstance().getWindow().getGuiScale();if((screen instanceof VotexScreen||screen instanceof AppearanceScreen||screen instanceof CapeCropScreen||screen instanceof ModLibraryScreen||screen instanceof HudEditorScreen||screen instanceof ArcadeScreen||screen instanceof EmoteWheel||screen instanceof VoiceConsentScreen||screen instanceof VoiceSetupScreen)&&body<14)throw new AssertionError("Readable type density was lost at "+Arrays.toString(physical));return Map.of("physical",List.of(physical[0],physical[1]),"gui",List.of(screen.width,screen.height),"widgets",screen.children().size(),"physicalBodyFontPixels",body);}
 private static MouseButtonEvent mouse(double x,double y){return new MouseButtonEvent(x,y,new MouseButtonInfo(0,0));}
 private static Object field(Object target,String name){try{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}catch(Exception e){throw new AssertionError(e);}}
 private static int integer(Object target,String name){return (Integer)field(target,name);}
}
