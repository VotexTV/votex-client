package dev.votex.client.mixin;
import dev.votex.client.LocalLooks;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Apply the same skin and arm model to world, hand and preview renderers. */
@Mixin(AbstractClientPlayer.class)
public abstract class LocalSkinMixin {
 @Inject(method="getSkin",at=@At("RETURN"),cancellable=true)
 private void votex$skin(CallbackInfoReturnable<PlayerSkin> result){var player=(AbstractClientPlayer)(Object)this;result.setReturnValue(LocalLooks.playerSkin(player.getUUID(),result.getReturnValue()));}
}
