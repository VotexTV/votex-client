package dev.votex.client;

/** Per-extraction immutable avatar preview data; no live entity or global playback state is retained. */
public interface EmotePreviewState {
 double[] votex$previewPose();
 void votex$previewPose(double[] pose);
 EmotePlayer.ContactPose votex$contactPose();
 void votex$contactPose(EmotePlayer.ContactPose pose);
}
