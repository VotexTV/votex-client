package dev.votex.client.mixin;
import dev.votex.chat.DuplicateGroups;
import dev.votex.client.ChatTools;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
 @Unique private final DuplicateGroups<GuiMessage> votex$groups=new DuplicateGroups<>();
 @Unique private boolean votex$stacking;
 @Unique private int votex$savedScroll=-1;
 @Inject(method="refreshTrimmedMessages",at=@At("HEAD")) private void votex$groups(CallbackInfo ci){votex$stacking=ChatTools.stacking();var access=(ChatComponentAccessor)this;if(ChatTools.enabled()){votex$savedScroll=access.votex$scroll();access.votex$scroll(0);}votex$groups.rebuild(access.votex$messages(),m->List.of(m.source(),m.content()));}
 @Inject(method="refreshTrimmedMessages",at=@At("TAIL")) private void votex$restoreScroll(CallbackInfo ci){if(votex$savedScroll>=0){var access=(ChatComponentAccessor)this;access.votex$scroll(Math.min(votex$savedScroll,Math.max(0,access.votex$lines().size()-((ChatComponent)(Object)this).getLinesPerPage())));votex$savedScroll=-1;}}
 @Inject(method="clearMessages",at=@At("HEAD")) private void votex$clear(boolean clear,CallbackInfo ci){votex$groups.clear();}
 @Inject(method="addMessageToQueue",at=@At("TAIL")) private void votex$newMessage(GuiMessage message,CallbackInfo ci){if(ChatTools.stacking())((ChatComponentAccessor)this).votex$refresh();}
 @Inject(method="tick",at=@At("TAIL")) private void votex$configChanged(CallbackInfo ci){if(votex$stacking!=ChatTools.stacking())((ChatComponentAccessor)this).votex$refresh();}
 @Inject(method="addMessageToDisplayQueue",at=@At("HEAD"),cancellable=true) private void votex$skip(GuiMessage message,CallbackInfo ci){if(ChatTools.stacking()&&votex$groups.count(message)==0)ci.cancel();}
 @Redirect(method="addMessageToDisplayQueue",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/chat/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"))
 private List<FormattedCharSequence> votex$count(GuiMessage original,Font font,int width){int count=ChatTools.stacking()?votex$groups.count(original):1;if(count<=1)return original.splitLines(font,width);Component text=original.content().copy().append(Component.literal("  ×"+count).withStyle(ChatFormatting.GRAY));return new GuiMessage(original.addedTime(),text,original.signature(),original.source(),original.tag()).splitLines(font,width);}
}
