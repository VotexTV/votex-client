package dev.votex.client.qa;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Native button dispatch tests use a private clipboard; user clipboard formats are never replaced. */
@Mixin(KeyboardHandler.class)
public abstract class ChatClipboardMixin {
 @Unique private String votex$qaClipboard="";
 @Unique private static boolean votex$privateClipboard(){return "1".equals(System.getenv("VOTEX_CHAT_DENSITY_TEST"))||"1".equals(System.getenv("VOTEX_PRIVATE_CLIPBOARD_TEST"));}
 @Inject(method="getClipboard",at=@At("HEAD"),cancellable=true) private void votex$read(CallbackInfoReturnable<String> result){if(votex$privateClipboard())result.setReturnValue(votex$qaClipboard);}
 @Inject(method="setClipboard",at=@At("HEAD"),cancellable=true) private void votex$write(String text,CallbackInfo result){if(votex$privateClipboard()){votex$qaClipboard=text;result.cancel();}}
}
