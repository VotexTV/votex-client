package dev.votex.client;

import dev.votex.client.mixin.ChatComponentAccessor;
import dev.votex.ui.ChatLayout;
import dev.votex.ui.WorkspaceMotion;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

/** Native sizes, real chat controls and private clipboard dispatch; no chat message is transmitted. */
final class ChatDensityGameChecks {
 static Map<String,Object> run(ClientGameTestContext c,Path images)throws Exception{
  var cases=new ArrayList<Map<String,Object>>();
  for(int[] size:new int[][]{{768,480,1},{768,480,2},{768,480,3},{1280,720,2},{1920,1080,3}}){
   c.getInput().resizeWindow(size[0],size[1]);c.waitTicks(3);c.runOnClient(mc->{mc.options.guiScale().set(size[2]);mc.getWindow().setGuiScale(size[2]);});
   Path dir=images.resolve("chat-"+size[0]+"x"+size[1]+"-gui"+size[2]);Files.createDirectories(dir);
   var proof=LibraryChatGameChecks.chat(c,dir);cases.add(Map.of("width",size[0],"height",size[1],"requestedGuiScale",size[2],"controls",proof));
  }
  c.getInput().resizeWindow(768,480);c.waitTicks(3);c.runOnClient(mc->{mc.options.guiScale().set(2);mc.getWindow().setGuiScale(2);});
  var originals=new ArrayList<GuiMessage>();var sourceObjects=new ArrayList<GuiMessage>();var flags=new boolean[2];
  c.runOnClient(mc->{var core=VotexClient.INSTANCE;flags[0]=core.modules.enabled("chat");flags[1]=core.modules.config().menuAnimations;core.modules.config().enabled.put("chat",true);core.modules.config().menuAnimations=true;var chat=mc.gui.hud.getChat();var access=(ChatComponentAccessor)chat;originals.addAll(access.votex$messages());chat.clearMessages(false);
   for(int i=0;i<80;i++){byte[] sig=new byte[MessageSignature.BYTES];Arrays.fill(sig,(byte)(i+1));Component body=Component.literal(i==20?"Prüfung äöü · ein eindeutiger Treffer":i==79?"Lange Nachricht · "+"Alle Details bleiben beim Kopieren erhalten. ".repeat(55):"Nachricht "+i+" · gemeinsam unterwegs").withStyle(ChatFormatting.GREEN);chat.addPlayerMessage(Component.translatable("chat.type.text",Component.literal("QACreator").withStyle(ChatFormatting.AQUA),body),new MessageSignature(sig),null);sourceObjects.add(access.votex$messages().getFirst());}
   mc.gui.setScreen(new ChatHistoryScreen(core,"Entwurf ✓ äöü"));
  });
  try{
   c.waitTicks(8);
   c.runOnClient(mc->{var row=((List<?>)field(mc.gui.screen(),"rows")).getFirst();var colors=new HashSet<Integer>();for(var raw:(List<?>)field(row,"lines"))((net.minecraft.util.FormattedCharSequence)raw).accept((index,style,codepoint)->{if(!style.getFont().equals(UiText.STYLE.getFont()))throw new AssertionError("Default message font was not replaced in UI presentation");if(style.getColor()!=null)colors.add(style.getColor().getValue());return true;});if(!colors.contains(Component.literal("x").withStyle(ChatFormatting.GREEN).getStyle().getColor().getValue())||!colors.contains(Component.literal("x").withStyle(ChatFormatting.AQUA).getStyle().getColor().getValue()))throw new AssertionError("Message colors were lost");var original=sourceObjects.get(79);if(original.signature()==null||!original.content().getStyle().getFont().equals(net.minecraft.network.chat.FontDescription.DEFAULT))throw new AssertionError("Signed original was rewritten");var custom=new net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.fromNamespaceAndPath("qa","custom_font"));UiText.presentation(Component.literal("badge").withStyle(net.minecraft.network.chat.Style.EMPTY.withFont(custom))).visit((style,text)->{if(!style.getFont().equals(custom))throw new AssertionError("Explicit custom font lost");return Optional.empty();},net.minecraft.network.chat.Style.EMPTY);});
   shot(c,images,"chat-many-messages-compact");
   c.runOnClient(mc->{var screen=mc.gui.screen();var b=(ChatLayout.History)field(screen,"layout");screen.mouseClicked(point(screen,b.listLeft()+10,b.listTop()+16,1),false);});c.waitTicks(3);
   c.runOnClient(mc->{var screen=mc.gui.screen();var button=screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance).map(net.minecraft.client.gui.components.Button.class::cast).filter(b->b.getMessage().getString().equals("Nachricht kopieren")).findFirst().orElseThrow();screen.mouseClicked(point(screen,button.getX()+button.getWidth()/2d,button.getY()+button.getHeight()/2d,0),false);if(!mc.keyboardHandler.getClipboard().equals(sourceObjects.get(79).content().getString()))throw new AssertionError("Truncated preview copied only a fragment");});c.waitTicks(3);
   c.runOnClient(mc->{var screen=mc.gui.screen();var b=(ChatLayout.History)field(screen,"layout");var gap=point(screen,b.listLeft()+10,b.listTop()+52,1);if(screen.mouseClicked(gap,false)||mc.gui.screen()!=screen)throw new AssertionError("Inter-row spacing opened a message");var e=point(screen,b.listLeft()+10,b.listTop()+16,0);double previous=((Number)field(screen,"scroll")).doubleValue();if(!screen.mouseScrolled(e.x(),e.y(),0,-.5))throw new AssertionError("Fractional scroll not consumed");if(((Number)field(screen,"scroll")).doubleValue()!=previous||((Number)field(screen,"scrollTarget")).doubleValue()<=previous)throw new AssertionError("Smooth wheel scroll changed the displayed list instantly");});
   c.waitTicks(6);
   c.runOnClient(mc->{var screen=mc.gui.screen();if(((Number)field(screen,"scroll")).doubleValue()<=0)throw new AssertionError("Smooth scroll did not advance");var b=(ChatLayout.History)field(screen,"layout");var start=point(screen,b.left()+b.width()-15,b.listTop()+5,0);var end=point(screen,b.left()+b.width()-15,b.listTop()+b.listHeight()-1,0);if(!screen.mouseClicked(start,false)||!screen.mouseDragged(end,end.x()-start.x(),end.y()-start.y()))throw new AssertionError("Custom scroll thumb did not drag");screen.mouseReleased(end);if(Math.abs(((Number)field(screen,"scroll")).doubleValue()-b.maxScroll(80))>.1)throw new AssertionError("Thumb did not reach oldest rows");});
   c.waitTicks(2);shot(c,images,"chat-oldest-messages-thumb");
   var anchor=c.computeOnClient(mc->{var rows=(List<?>)field(mc.gui.screen(),"rows");int index=(int)(((Number)field(mc.gui.screen(),"scroll")).doubleValue()/ChatLayout.ROW_STEP);return field(rows.get(index),"message");});
   c.runOnClient(mc->mc.gui.hud.getChat().addPlayerMessage(Component.literal("<QACreator> Neue Nachricht während des Lesens"),null,null));c.waitTicks(3);
   c.runOnClient(mc->{var rows=(List<?>)field(mc.gui.screen(),"rows");int index=(int)(((Number)field(mc.gui.screen(),"scroll")).doubleValue()/ChatLayout.ROW_STEP);if(field(rows.get(index),"message")!=anchor)throw new AssertionError("New arrival moved the currently read message");var access=(ChatComponentAccessor)mc.gui.hud.getChat();access.votex$messages().remove(sourceObjects.get(30));access.votex$refresh();});
   c.waitTicks(3);c.runOnClient(mc->{if(((List<?>)field(mc.gui.screen(),"rows")).size()!=80)throw new AssertionError("Deletion behind unchanged newest message remained in search");edit(mc.gui.screen()).setValue("ÄÖÜ");});c.waitTicks(2);
   c.runOnClient(mc->{if(((List<?>)field(mc.gui.screen(),"rows")).size()!=1)throw new AssertionError("Unicode filter mismatch");var original=sourceObjects.get(20);if(original.signature()==null||!((ChatComponentAccessor)mc.gui.hud.getChat()).votex$messages().contains(original))throw new AssertionError("Original signature or object lost");var history=mc.gui.screen();mc.gui.setScreen(new ChatContextScreen(VotexClient.INSTANCE,original,"Entwurf ✓ äöü",history.width-1,history.height-1,history));});
   c.waitTicks(3);shot(c,images,"chat-context-bottom-corner");c.getInput().resizeWindow(1280,720);c.waitTicks(4);
   c.runOnClient(mc->{var screen=mc.gui.screen();var b=(ChatLayout.Context)field(screen,"layout");var parent=(Screen)field(screen,"parent");if(parent.width!=screen.width||parent.height!=screen.height)throw new AssertionError("History beneath popup did not resize");if(b.top()+b.height()>b.viewportHeight()-11||b.left()+b.width()>b.viewportWidth()-11)throw new AssertionError("Popup escaped viewport");});
   shot(c,images,"chat-context-resized-with-parent");
   // 1280x720/GUI2 and1920x1080/GUI3 have the same GUI-coordinate extent.
   // Changing physical density must still rebuild the history under the popup.
   c.runOnClient(mc->{mc.options.guiScale().set(3);mc.getWindow().setGuiScale(3);});c.getInput().resizeWindow(1920,1080);c.waitTicks(4);
   c.runOnClient(mc->{var popup=mc.gui.screen();popup.resize(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());var parent=(Screen)field(popup,"parent");var actual=(ChatLayout.History)field(parent,"layout");var expected=ChatLayout.history(popup.width,popup.height,mc.getWindow().getGuiScale());if(popup.width!=640||popup.height!=360||!actual.equals(expected))throw new AssertionError("Identical GUI coordinates retained stale parent density");});
   c.waitTicks(2);shot(c,images,"chat-context-same-coordinates-new-density");
   c.runOnClient(mc->{mc.gui.screen().mouseClicked(new MouseButtonEvent(0,0,new MouseButtonInfo(0,0)),false);if(!(mc.gui.screen() instanceof ChatHistoryScreen)||!edit(mc.gui.screen()).getValue().equals("ÄÖÜ"))throw new AssertionError("Outside close lost query");mc.gui.screen().onClose();if(!(mc.gui.screen() instanceof ChatScreen)||!edit(mc.gui.screen()).getValue().equals("Entwurf ✓ äöü"))throw new AssertionError("Return to chat lost unsent draft");});
   var proof=new LinkedHashMap<String,Object>();proof.put("sizeAndGuiCases",cases);for(String key:List.of("defaultMessagesUseSmoothUiFontWithColors","explicitCustomFontsRetained","truncatedPreviewCopiesFullOriginal","rowGapRejected","fractionalSmoothScroll","thumbReachesOldest","newMessagesPreserveReadingAnchor","oldMessageDeletionReflected","popupAndParentResize","sameGuiCoordinatesNewPhysicalDensity","outsideClickPreservesQueryAndDraft"))proof.put(key,true);proof.put("clipboard","test-only private in-memory backend; no user clipboard access");proof.put("messageOrigin","synthetic signed GUI objects; no cryptographic or network assertion");return proof;
  }finally{c.runOnClient(mc->{var access=(ChatComponentAccessor)mc.gui.hud.getChat();access.votex$messages().clear();access.votex$messages().addAll(originals);access.votex$refresh();VotexClient.INSTANCE.modules.config().enabled.put("chat",flags[0]);VotexClient.INSTANCE.modules.config().menuAnimations=flags[1];mc.gui.setScreen(null);});}
 }
 private static MouseButtonEvent point(Screen s,double x,double y,int button){float scale=((Number)field(s,"scale")).floatValue();var f=(WorkspaceMotion.Frame)field(s,"displayedMotion");return new MouseButtonEvent(f.screenX(x)*scale,f.screenY(y)*scale,new MouseButtonInfo(button,0));}
 private static void shot(ClientGameTestContext c,Path p,String name){c.takeScreenshot(TestScreenshotOptions.of("votex-goal-"+name).withDestinationDir(p).disableCounterPrefix());}
 private static EditBox edit(Screen s){return s.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();}
 private static Object field(Object o,String key){try{var f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
}
