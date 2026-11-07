impl Game<'_> {
    fn draw_votex_hud(&self, batch: &mut HudBatch, corners: &mut Corners) {
        let extras = &self.settings.extras;
        if self.debug || (!extras.votex_fps && !extras.votex_coordinates) {
            return;
        }
        let fps = extras.votex_fps.then(|| format!("{} FPS", self.extras.fps.reading().fps));
        let coordinates = if extras.votex_coordinates {
            self.player.as_ref().map(|player| {
                let p = player.position;
                format!("XYZ  {}  {}  {}", p.x.floor() as i64, p.y.floor() as i64, p.z.floor() as i64)
            })
        } else {
            None
        };
        for line in [fps.as_deref(), coordinates.as_deref()].into_iter().flatten() {
            extras_hud::draw_lines(batch, &self.gui.atlas, corners, Corner::TopLeft, &[(line, 0xe5f2ff)]);
        }
    }
}
