package dev.votex.client.mixin;
import dev.votex.client.ChatTools;
import dev.votex.client.VotexClient;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
 @Shadow protected EditBox input;
 @Inject(method="keyPressed",at=@At("HEAD"),cancellable=true) private void votex$search(KeyEvent e,CallbackInfoReturnable<Boolean> cir){if(ChatTools.enabled()&&VotexClient.INSTANCE.modules.config().chatSearch&&e.key()==GLFW.GLFW_KEY_F&&e.hasControlDown()){ChatTools.openSearch(input.getValue());cir.setReturnValue(true);}}
 @Inject(method="mouseClicked",at=@At("HEAD"),cancellable=true) private void votex$context(MouseButtonEvent e,boolean twice,CallbackInfoReturnable<Boolean> cir){if(ChatTools.enabled()&&VotexClient.INSTANCE.modules.config().chatContextMenu&&e.button()==1){var message=ChatTools.at(e.x(),e.y());if(message!=null){ChatTools.openContext(message,input.getValue(),(int)e.x(),(int)e.y());cir.setReturnValue(true);}}}
}
