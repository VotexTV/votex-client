package dev.votex.client.mixin;
import dev.votex.client.ItemPhysicsState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityStateMixin implements ItemPhysicsState {
 @Unique private boolean votex$grounded;
 public void votex$grounded(boolean value){votex$grounded=value;}
 public boolean votex$grounded(){return votex$grounded;}
}
