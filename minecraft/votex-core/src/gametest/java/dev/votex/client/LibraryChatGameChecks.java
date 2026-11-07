package dev.votex.client;

import com.google.gson.*;
import dev.votex.client.mixin.ChatComponentAccessor;
import dev.votex.config.AtomicJson;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.lwjgl.glfw.GLFW;

/** Real native controls and original chat objects in an explicitly isolated Fabric test process. */
public final class LibraryChatGameChecks {
 private LibraryChatGameChecks(){}
 public static Map<String,Object> run(ClientGameTestContext context,Path destination)throws Exception{
  Path root=context.computeOnClient(mc->mc.gameDirectory.toPath().toAbsolutePath().normalize());
  if(!root.toString().toLowerCase(Locale.ROOT).contains("clientgametest"))throw new AssertionError("Refusing a non-isolated addon/chat fixture");
  Files.createDirectories(destination);var evidence=new LinkedHashMap<String,Object>();
  context.runOnClient(mc->mc.gui.setScreen(new ModLibraryScreen(VotexClient.INSTANCE,null)));ready(context);
  context.runOnClient(mc->{var items=field(mc.gui.screen(),"installed");if(!(items instanceof List<?> list)||list.isEmpty())throw new AssertionError("No actual Fabric library entries");});shot(context,destination,"library-installed");
  click(context,"Entdecken");ready(context);
  context.runOnClient(mc->edit(mc.gui.screen()).setValue("sodium"));click(context,"Suchen");ready(context);
  context.runOnClient(mc->{List<?> projects=(List<?>)field(mc.gui.screen(),"projects");if(projects.isEmpty())throw new AssertionError("Live Modrinth search returned no Sodium-compatible projects: "+field(mc.gui.screen(),"status"));boolean sodium=projects.stream().map(ModLibraryService.Project.class::cast).anyMatch(project->project.name().toLowerCase(Locale.ROOT).contains("sodium"));if(!sodium)throw new AssertionError("Live search did not return Sodium");evidence.put("modrinth",Map.of("endpoint","https://api.modrinth.com/v2/search","game","26.2","loader","fabric","query","sodium","results",projects.stream().map(ModLibraryService.Project.class::cast).map(project->Map.of("id",project.id(),"name",project.name())).toList()));if(((Map<?,?>)field(mc.gui.screen(),"icons")).size()>80||((Set<?>)field(mc.gui.screen(),"requestedIcons")).size()>32)throw new AssertionError("Unbounded icon residency or requests");});shot(context,destination,"library-live-modrinth-search");
  click(context,"Installiert");ready(context);context.runOnClient(mc->edit(mc.gui.screen()).setValue(""));
  Path source=destination.resolve("votex-native-library-fixture.jar");
  try(var zip=new ZipOutputStream(Files.newOutputStream(source))){zip.putNextEntry(new ZipEntry("fabric.mod.json"));zip.write("{\"schemaVersion\":1,\"id\":\"votex_native_library_fixture\",\"name\":\"Native Library Fixture\",\"version\":\"1.0.0\",\"environment\":\"client\",\"depends\":{\"minecraft\":\"26.2\",\"fabricloader\":\">=0.19.5\"}}".getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
  String sourceHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(Files.readAllBytes(source)));
  context.runOnClient(mc->mc.gui.screen().onFilesDrop(List.of(source)));ready(context);
  verifyQueue(root,sourceHash);click(context,"Vorgemerkt");ready(context);selectFirst(context);shot(context,destination,"library-native-import-pending");click(context,"Vormerkung zurücknehmen");ready(context);
  if(!ModLibraryService.readQueue(root).isEmpty())throw new AssertionError("Native cancel left the import queued");
  // Leave one explicit import staged for a separately invoked Rust launcher application check.
  context.runOnClient(mc->mc.gui.screen().onFilesDrop(List.of(source)));ready(context);verifyQueue(root,sourceHash);
  if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(Files.readAllBytes(source))).equals(sourceHash))throw new AssertionError("Import modified its source file");
  evidence.put("nativeImport",Map.of("source",source.toString(),"fixtureRoot",root.toString(),"filename",source.getFileName().toString(),"sha512",sourceHash,"sourceUnchanged",true,"loadedJarsUnchanged",true,"nativeCancelThenRestage",true,"launcherApplyProof","requires separate Rust check against this exact fixture"));
  evidence.put("chat",chat(context,destination));AtomicJson.write(destination.resolve("library-chat-proof.json"),evidence);return evidence;
 }
 private static void verifyQueue(Path root,String hash)throws Exception{var actions=ModLibraryService.readQueue(root);if(actions.size()!=1)throw new AssertionError("Expected one isolated staged import, found "+actions.size());var action=actions.get(0).getAsJsonObject();if(!action.get("kind").getAsString().equals("install")||!action.get("sha512").getAsString().equals(hash)||!action.get("filename").getAsString().equals("votex-native-library-fixture.jar"))throw new AssertionError("Wrong import plan");Path staged=root.resolve("config/votex/addon-staging").resolve(hash+".bin");if(!Files.isRegularFile(staged)||!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(Files.readAllBytes(staged))).equals(hash))throw new AssertionError("Staged file did not preserve SHA-512");if(Files.exists(root.resolve("mods/votex-native-library-fixture.jar")))throw new AssertionError("Loaded mod folder changed before restart");}
 static Map<String,Object> chat(ClientGameTestContext context,Path destination){
  var originalHistory=new ArrayList<GuiMessage>();var originalClipboard=new String[1];var originals=new ArrayList<GuiMessage>();var oldFlags=new boolean[4];
  context.runOnClient(mc->{originalClipboard[0]=mc.keyboardHandler.getClipboard();var core=VotexClient.INSTANCE;oldFlags[0]=core.modules.enabled("chat");oldFlags[1]=core.modules.config().chatSearch;oldFlags[2]=core.modules.config().chatContextMenu;oldFlags[3]=core.modules.config().chatStackDuplicates;core.modules.config().enabled.put("chat",true);core.modules.config().chatSearch=true;core.modules.config().chatContextMenu=true;core.modules.config().chatStackDuplicates=true;var chat=mc.gui.hud.getChat();var access=(ChatComponentAccessor)chat;originalHistory.addAll(access.votex$messages());chat.clearMessages(false);Component content=Component.translatable("chat.type.text",Component.literal("QACreator").withStyle(ChatFormatting.AQUA),Component.literal("Chat Prüfung äöü").withStyle(ChatFormatting.GREEN));for(int i=0;i<3;i++){byte[] bytes=new byte[MessageSignature.BYTES];Arrays.fill(bytes,(byte)(11+i));chat.addPlayerMessage(content.copy(),new MessageSignature(bytes),null);originals.add(access.votex$messages().getFirst());}access.votex$refresh();if(access.votex$messages().size()!=3||access.votex$lines().stream().map(line->line.parent()).distinct().count()!=1)throw new AssertionError("Duplicate stacking did not preserve 3 originals / one display group");StringBuilder text=new StringBuilder();for(var line:access.votex$lines())line.content().accept((i,style,cp)->{text.appendCodePoint(cp);return true;});if(!text.toString().contains("×3"))throw new AssertionError("Duplicate display counter missing");for(var message:originals)if(access.votex$messages().stream().noneMatch(item->item==message)||message.signature()==null||!message.content().equals(content))throw new AssertionError("Chat stacking mutated an original content/signature");mc.gui.setScreen(new ChatScreen("mein Entwurf",false));double chatY=mc.getWindow().getGuiScaledHeight()-40-access.votex$scale()*access.votex$lineHeight()*.5;if(!mc.gui.screen().mouseClicked(mouse(12,chatY,1),false)||!(mc.gui.screen() instanceof ChatContextScreen))throw new AssertionError("Actual ChatScreen right-click mixin did not open the selected message");mc.gui.screen().onClose();if(!mc.gui.screen().keyPressed(new KeyEvent(GLFW.GLFW_KEY_F,0,GLFW.GLFW_MOD_CONTROL))||!(mc.gui.screen() instanceof ChatHistoryScreen))throw new AssertionError("Actual ChatScreen CTRL-F mixin did not open history");edit(mc.gui.screen()).setValue("pRüFuNg");if(((List<?>)field(mc.gui.screen(),"rows")).size()!=3)throw new AssertionError("Search did not include stacked original messages");});
  try{
   shot(context,destination,"chat-control-f-history");
   context.runOnClient(mc->{Screen screen=mc.gui.screen();var bounds=(dev.votex.ui.ChatLayout.History)field(screen,"layout");if(!screen.mouseClicked(pointer(screen,bounds.listLeft()+10,bounds.listTop()+16,1),false)||!(mc.gui.screen() instanceof ChatContextScreen))throw new AssertionError("History row context hit-test failed");});shot(context,destination,"chat-message-context");click(context,"Nachricht kopieren");context.runOnClient(mc->{if(!mc.keyboardHandler.getClipboard().contains("Chat Prüfung äöü"))throw new AssertionError("Native message clipboard copy failed");});
   context.runOnClient(mc->{Screen screen=mc.gui.screen();var bounds=(dev.votex.ui.ChatLayout.History)field(screen,"layout");screen.mouseClicked(pointer(screen,bounds.listLeft()+10,bounds.listTop()+16,1),false);});click(context,"Name kopieren");context.runOnClient(mc->{if(!mc.keyboardHandler.getClipboard().equals("QACreator"))throw new AssertionError("Native sender clipboard copy failed");});
   context.runOnClient(mc->{Screen screen=mc.gui.screen();var bounds=(dev.votex.ui.ChatLayout.History)field(screen,"layout");screen.mouseClicked(pointer(screen,bounds.listLeft()+10,bounds.listTop()+16,1),false);});click(context,"An QACreator schreiben");context.runOnClient(mc->{if(!(mc.gui.screen() instanceof ChatScreen)||!edit(mc.gui.screen()).getValue().equals("/msg QACreator "))throw new AssertionError("Reply did not prepare the correct unsent /msg draft");var core=VotexClient.INSTANCE;core.modules.config().chatStackDuplicates=false;((ChatComponentAccessor)mc.gui.hud.getChat()).votex$refresh();if(((ChatComponentAccessor)mc.gui.hud.getChat()).votex$lines().stream().map(line->line.parent()).distinct().count()!=3)throw new AssertionError("Disabling duplicate grouping did not restore all original display rows");});
   return Map.of("nativeCtrlF",true,"caseInsensitiveUnicodeSearch",true,"rightClickContext",true,"clipboardMessageAndName",true,"replyDraftUnsent",true,"threeOriginalObjectsAndSignaturesPreserved",true,"presentationOnlyStackCounter",true,"restoresAllRowsWhenDisabled",true,"messageOrigin","synthetic signed-message objects in actual Minecraft GUI; no cryptographic verification claim");
  }finally{context.runOnClient(mc->{mc.keyboardHandler.setClipboard(originalClipboard[0]);var core=VotexClient.INSTANCE;core.modules.config().enabled.put("chat",oldFlags[0]);core.modules.config().chatSearch=oldFlags[1];core.modules.config().chatContextMenu=oldFlags[2];core.modules.config().chatStackDuplicates=oldFlags[3];var access=(ChatComponentAccessor)mc.gui.hud.getChat();access.votex$messages().clear();access.votex$messages().addAll(originalHistory);access.votex$refresh();mc.gui.setScreen(null);});}
 }
 private static void ready(ClientGameTestContext context){context.waitFor(mc->mc.gui.screen() instanceof ModLibraryScreen&&!((Boolean)field(mc.gui.screen(),"busy")),1200);context.waitTicks(2);}
 private static void selectFirst(ClientGameTestContext context){context.runOnClient(mc->{Screen screen=mc.gui.screen();screen.mouseClicked(pointer(screen,integer(screen,"left")+40,integer(screen,"top")+integer(screen,"listY")+23,0),false);});}
 private static void click(ClientGameTestContext context,String label){context.runOnClient(mc->{Screen screen=mc.gui.screen();Button button=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(label)&&b.active).findFirst().orElseThrow(()->new AssertionError("Native addon/chat button missing: "+label));float scale=scale(screen);var e=pointer(screen,button.getX()+button.getWidth()*.5,button.getY()+button.getHeight()*.5,0);if(!screen.mouseClicked(e,false))throw new AssertionError("Native button rejected "+label);screen.mouseReleased(e);});context.waitTicks(2);}
 private static float scale(Screen screen){return screen instanceof ModLibraryScreen||screen instanceof ChatHistoryScreen||screen instanceof ChatContextScreen?((Number)field(screen,"scale")).floatValue():1;}
 private static MouseButtonEvent pointer(Screen screen,double x,double y,int button){float scale=scale(screen);if(screen instanceof ModLibraryScreen||screen instanceof ChatHistoryScreen||screen instanceof ChatContextScreen){var frame=(dev.votex.ui.WorkspaceMotion.Frame)field(screen,"displayedMotion");x=frame.screenX(x);y=frame.screenY(y);}return mouse(x*scale,y*scale,button);}
 private static void shot(ClientGameTestContext context,Path destination,String name){context.waitTicks(2);context.takeScreenshot(TestScreenshotOptions.of("votex-goal-"+name).withDestinationDir(destination).disableCounterPrefix());}
 private static EditBox edit(Screen screen){return screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();}
 private static MouseButtonEvent mouse(double x,double y,int button){return new MouseButtonEvent(x,y,new MouseButtonInfo(button,0));}
 private static Object field(Object target,String name){try{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}catch(Exception e){throw new AssertionError(e);}}
 private static int integer(Object target,String name){return (Integer)field(target,name);}
}
