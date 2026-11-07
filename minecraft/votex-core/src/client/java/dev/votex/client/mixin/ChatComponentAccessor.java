package dev.votex.client.mixin;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.List;
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
 @Accessor("allMessages") List<GuiMessage> votex$messages();
 @Accessor("trimmedMessages") List<GuiMessage.Line> votex$lines();
 @Accessor("chatScrollbarPos") int votex$scroll();
 @Accessor("chatScrollbarPos") void votex$scroll(int value);
 @Invoker("refreshTrimmedMessages") void votex$refresh();
 @Invoker("getScale") double votex$scale();
 @Invoker("getLineHeight") int votex$lineHeight();
 @Invoker("getWidth") int votex$width();
}
