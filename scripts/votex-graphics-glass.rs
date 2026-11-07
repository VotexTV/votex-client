/// Rounded GUI geometry in the existing HUD batch. This is the tint and edge
/// layer. The scene blur and refraction belong in the renderer's glass pass.
fn votex_round_fill(
    batch: &mut HudBatch,
    atlas: &HudAtlas,
    [x, y, w, h]: [f32; 4],
    radius: f32,
    color: u32,
) {
    if w <= 0.0 || h <= 0.0 {
        return;
    }
    let r = radius.min(w / 2.0).min(h / 2.0);
    let steps = r.ceil() as usize;
    if steps == 0 {
        fill(batch, atlas, [x, y, w, h], color);
        return;
    }
    let step = r / steps as f32;
    for i in 0..steps {
        let offset = i as f32 * step;
        let dy = r - offset - step / 2.0;
        let inset = r - (r * r - dy * dy).max(0.0).sqrt();
        let width = w - inset * 2.0;
        fill(batch, atlas, [x + inset, y + offset, width, step], color);
        fill(batch, atlas, [x + inset, y + h - offset - step, width, step], color);
    }
    if h > 2.0 * r {
        fill(batch, atlas, [x, y + r, w, h - 2.0 * r], color);
    }
}
