package dev.votex.client.mixin;

import dev.votex.client.EmotePreviewState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements EmotePreviewState {
 @Unique private double[] votex$previewPose;
 @Unique private dev.votex.client.EmotePlayer.ContactPose votex$contactPose;
 @Override public double[] votex$previewPose(){return votex$previewPose;}
 @Override public void votex$previewPose(double[] pose){votex$previewPose=pose;}
 @Override public dev.votex.client.EmotePlayer.ContactPose votex$contactPose(){return votex$contactPose;}
 @Override public void votex$contactPose(dev.votex.client.EmotePlayer.ContactPose pose){votex$contactPose=pose;}
}
