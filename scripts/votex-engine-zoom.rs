/// Log magnification makes wheel input and easing proportional at every scale.
pub(super) struct VotexZoom {
    requested: f32,
    current: f32,
    updated_at: Option<f64>,
}

impl Default for VotexZoom {
    fn default() -> Self {
        Self {
            requested: 4.0_f32.ln(),
            current: 0.0,
            updated_at: None,
        }
    }
}

impl VotexZoom {
    fn wheel(&mut self, delta: f32, sensitivity: f32) {
        if delta.is_finite() {
            let step = 0.18 * sensitivity.clamp(0.1, 3.0);
            self.requested = (self.requested + delta * step).clamp(1.25_f32.ln(), 40.0_f32.ln());
        }
    }

    fn advance(&mut self, now: f64, held: bool, speed: f32) {
        let elapsed = self.updated_at.replace(now).map_or(0.0, |last| (now - last).clamp(0.0, 0.1));
        let target = if held { self.requested } else { 0.0 };
        let rate = 18.0 * speed.clamp(0.1, 3.0);
        self.current += (target - self.current) * (1.0 - (-rate * elapsed as f32).exp());
        if (target - self.current).abs() < 0.001 {
            self.current = target;
        }
    }
}

impl Game<'_> {
    fn votex_zoom_held(&self) -> bool {
        use horizon_client::keybinds::{key_code, mouse_button};
        if !self.focused || !self.native_playing() || self.screen_over_playing() || self.scoping() {
            return false;
        }
        let debug = self.settings.keys.get("key.debug.modifier");
        if key_code(debug).is_some_and(|key| self.physical_keys.contains(&key)) {
            return false;
        }
        let binding = self.settings.keys.get("key.votex.zoom");
        key_code(binding).is_some_and(|key| self.physical_keys.contains(&key))
            || mouse_button(binding).is_some_and(|button| self.physical_buttons.contains(&button))
    }

    pub(super) fn votex_zoom_frame(&mut self) {
        let now = self.extras_now();
        let held = self.votex_zoom_held();
        self.extras.votex_zoom.advance(now, held, self.settings.votex_zoom_speed);
    }

    pub(super) fn votex_zoom_scroll(&mut self, delta: f32) -> bool {
        if self.votex_zoom_held() && delta != 0.0 {
            self.extras.votex_zoom.wheel(delta, self.settings.votex_zoom_wheel);
            true
        } else {
            false
        }
    }
}
