package dev.votex.client.mixin;
import dev.votex.client.VotexPerformanceEngine;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class PerformanceMixin {
 @Inject(method="renderFrame",at=@At("HEAD"))private void votex$begin(boolean tick,CallbackInfo ci){var engine=VotexPerformanceEngine.INSTANCE;if(engine!=null)engine.frameBegin();}
 @Inject(method="renderFrame",at=@At("RETURN"))private void votex$end(boolean tick,CallbackInfo ci){var engine=VotexPerformanceEngine.INSTANCE;if(engine!=null)engine.frameEnd();}
 @Inject(method="onResourceLoadFinished",at=@At("RETURN"))private void votex$reload(CallbackInfo ci){dev.votex.client.UiText.clear();dev.votex.client.VotexGlassEngine.close();var engine=VotexPerformanceEngine.INSTANCE;if(engine!=null)engine.clearWorld();}
}
