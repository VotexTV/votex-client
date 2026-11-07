"""Create a reproducible Votex Engine source tree from the pinned Horizon archive."""

import argparse
import hashlib
import json
from pathlib import Path
from tempfile import TemporaryDirectory
from zipfile import ZipFile


UPSTREAM_SHA256 = "ac49dcdc19799284fdd03460f6daba4083192f6fe5c1bce9a415f0c0d86d58ce"
WINDOWS = {
    "game.rs": 2,
    "game/no_resources.rs": 1,
    "game/split/windows.rs": 1,
}
LABELS = {
    "horizon.pages.settings": "Votex Settings",
    "horizon.pages.open.horizon_graphics": "Votex Graphics...",
}


def patch(source: Path) -> None:
    client = source / "crates/horizon-client"
    old_title = 'with_title("Horizon")'
    for name, count in WINDOWS.items():
        path = client / "src" / name
        original = path.read_bytes()
        if original.count(old_title.encode()) != count:
            raise ValueError(f"Unexpected upstream window titles in {name}")
        path.write_bytes(original.replace(old_title.encode(), b'with_title("Votex Engine")'))

    languages = client / "assets/horizon/lang"
    for name in ("en_us.json", "de_de.json"):
        path = languages / name
        values = json.loads(path.read_text(encoding="utf-8"))
        for key, label in LABELS.items():
            if key not in values:
                raise ValueError(f"Missing upstream translation key {key} in {name}")
            values[key] = label
        values["key.votex.zoom"] = "Votex-Zoom" if name == "de_de.json" else "Votex Zoom"
        values["options.votexZoomSpeed"] = "Zoomtempo" if name == "de_de.json" else "Zoom speed"
        values["options.votexZoomWheel"] = "Zoom-Mausrad" if name == "de_de.json" else "Zoom wheel sensitivity"
        values["votex.hud.votex_fps"] = "FPS"
        values["votex.hud.votex_coordinates"] = "Koordinaten" if name == "de_de.json" else "Coordinates"
        values["votex.hud.votex_fps.tooltip"] = (
            "Zeigt die gemessene Bildrate." if name == "de_de.json" else "Shows the measured frame rate."
        )
        values["votex.hud.votex_coordinates.tooltip"] = (
            "Zeigt deine Blockkoordinaten." if name == "de_de.json" else "Shows your current block coordinates."
        )
        path.write_text(json.dumps(values, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    patch_title(source)
    patch_zoom(source)
    patch_zoom_settings(source)
    patch_graphics_glass(source)
    patch_hud(source)


def patch_hud(source: Path) -> None:
    render = source / "crates/horizon-client-render/src"
    extras = render / "extras.rs"
    for item, field, key, label, description in (
        ("VotexFps", "votex_fps", "votexFps", "FPS", "Shows the measured frame rate."),
        ("VotexCoordinates", "votex_coordinates", "votexCoordinates", "Coordinates", "Shows your current block coordinates."),
    ):
        insert_after(extras, "    pub no_ai: bool,", f"    pub {field}: bool,")
        insert_after(extras, "            no_ai: false,", f"            {field}: false,")
        insert_after(extras, "                horizon_core::no_ai::KEY => e.no_ai = flag(e.no_ai),",
                     f'                "{key}" => e.{field} = flag(e.{field}),')
        insert_after(extras, '        text.push_str(&format!("{EMOTE_KEY}:{}\\n", self.emote_key));',
                     f'        text.push_str(&format!("{key}:{{}}\\n", bool_text(self.{field})));')
        insert_after(extras, "    NoAi,", f"    {item},")
        insert_after(extras, '            Item::NoAi => "horizon.extras.no_ai",',
                     f'            Item::{item} => "votex.hud.{field}",')
        insert_after(extras, '            Item::NoAi => "horizon.extras.no_ai.tooltip",',
                     f'            Item::{item} => "votex.hud.{field}.tooltip",')
        insert_after(extras, '            Item::NoAi => "No AI",',
                     f'            Item::{item} => "{label}",')
        insert_after(extras, "            UiSoundReserve => return None,", f"            {item} => return None,")
        insert_after(extras, '            Item::ChatIndicator => "The coloured bar at the left of a chat line.",',
                     f'            Item::{item} => "{description}",')
        insert_after(extras, "            Item::NoAi => e.no_ai,", f"            Item::{item} => e.{field},")
        insert_after(extras, "            Item::NoAi => e.no_ai = !e.no_ai,",
                     f"            Item::{item} => e.{field} = !e.{field},")
    ui = render / "options_ui.rs"
    replace_once(ui, '                    Row::Buttons(\n                        open("horizon.pages.open.extras", Page::Extras),',
                 "                    Row::Buttons(\n"
                 "                        self.extra_button(s, crate::extras::Item::VotexFps),\n"
                 "                        Some(self.extra_button(s, crate::extras::Item::VotexCoordinates)),\n"
                 "                    ),\n"
                 '                    Row::Buttons(\n                        open("horizon.pages.open.extras", Page::Extras),')
    hud = source / "crates/horizon-client/src/game/extras.rs"
    game = source / "crates/horizon-client/src/game.rs"
    replace_once(game, "                    // Screen.keyPressed sees the physical key, repeats included.",
                 "                    if key == KeyCode::ShiftRight\n"
                 "                        && event.state == ElementState::Pressed\n"
                 "                        && !event.repeat\n"
                 "                        && self.native_playing()\n"
                 "                        && !self.screen_over_playing()\n"
                 "                    {\n"
                 "                        self.capture(false);\n"
                 "                        self.open_options_at(false, horizon_client::options_ui::Page::Horizon);\n"
                 "                        self.options_to_game = true;\n"
                 "                        return Ok(());\n"
                 "                    }\n"
                 "                    // Screen.keyPressed sees the physical key, repeats included.")
    insert_after(hud, "        self.draw_pvp_hud(batch, &mut corners);",
                 "        self.draw_votex_hud(batch, &mut corners);")
    with hud.open("ab") as output:
        output.write(b"\n" + Path(__file__).with_name("votex-engine-hud.rs").read_bytes())


def patch_graphics_glass(source: Path) -> None:
    render = source / "crates/horizon-client-render/src"
    style = render / "graphics_style.rs"
    replace_once(render / "graphics_ui.rs",
                 'shell::mark(batch, atlas, cols, &lang::get("Graphics"));',
                 'shell::mark(batch, atlas, cols, "Votex Graphics");')
    replace_once(style, "const GLASS: u32 = hex(B.page, 118);",
                 "const GLASS: u32 = hex(0x142236, 176);")
    replace_once(style, "const EDGE: u32 = hex(B.ink, 26);",
                 "const EDGE: u32 = hex(0xb9d9ef, 45);")
    replace_once(style, "    fill(batch, atlas, rect, GLASS);\n    ring(batch, atlas, rect, EDGE);",
                 "    let [x, y, w, h] = rect;\n"
                 "    votex_round_fill(batch, atlas, rect, 8.0, EDGE);\n"
                 "    votex_round_fill(batch, atlas, [x + 1.0, y + 1.0, w - 2.0, h - 2.0], 7.0, GLASS);")
    for color, radius in (("GROUP", 5), ("face", 5)):
        replace_once(style, f"    fill(batch, atlas, rect, {color});",
                     f"    votex_round_fill(batch, atlas, rect, {radius}.0, {color});")
    replace_once(style, "pub fn well(batch: &mut HudBatch, atlas: &HudAtlas, rect: [f32; 4]) {\n    fill(batch, atlas, rect, WELL);",
                 "pub fn well(batch: &mut HudBatch, atlas: &HudAtlas, rect: [f32; 4]) {\n    votex_round_fill(batch, atlas, rect, 5.0, WELL);")
    replace_once(style, "    fill(batch, atlas, handle, if live { HANDLE } else { MUTED });",
                 "    votex_round_fill(batch, atlas, handle, 2.0, if live { HANDLE } else { MUTED });")
    with style.open("ab") as output:
        output.write(b"\n" + Path(__file__).with_name("votex-graphics-glass.rs").read_bytes())


def patch_zoom_settings(source: Path) -> None:
    render = source / "crates/horizon-client-render/src"
    fields = (
        ("ZoomSpeed", "votex_zoom_speed", "votexZoomSpeed"),
        ("ZoomWheel", "votex_zoom_wheel", "votexZoomWheel"),
    )
    settings = render / "settings.rs"
    options = render / "options_file.rs"
    ui = render / "options_ui.rs"
    replace_once(settings, "    WheelSensitivity,", "    WheelSensitivity,\n    ZoomSpeed,\n    ZoomWheel,")
    replace_once(settings, "    pub mouse_wheel_sensitivity: f32,",
                 "    pub mouse_wheel_sensitivity: f32,\n    pub votex_zoom_speed: f32,\n    pub votex_zoom_wheel: f32,")
    replace_once(settings, "            mouse_wheel_sensitivity: 1.0,",
                 "            mouse_wheel_sensitivity: 1.0,\n            votex_zoom_speed: 1.0,\n            votex_zoom_wheel: 1.0,")
    replace_once(ui, "    Opt::WheelSensitivity,\n    Opt::DiscreteScroll,",
                 "    Opt::WheelSensitivity,\n    Opt::ZoomSpeed,\n    Opt::ZoomWheel,\n    Opt::DiscreteScroll,")
    replace_once(ui, "                Opt::WheelSensitivity,\n                Opt::DiscreteScroll,",
                 "                Opt::WheelSensitivity,\n                Opt::ZoomSpeed,\n                Opt::ZoomWheel,\n                Opt::DiscreteScroll,")
    for variant, field, key in fields:
        insert_after(settings, '            mouse_wheel_sensitivity: f.double("mouseWheelSensitivity") as f32,',
                     f'            {field}: f.double("{key}") as f32,')
        insert_after(settings, '        put_b(&mut f, "discrete_mouse_scroll", self.discrete_scroll);',
                     f'        put_f(&mut f, "{key}", self.{field});')
        insert_after(options, '        def("mouseWheelSensitivity", O(WheelSensitivity), V::Double(1.0)),',
                     f'        def("{key}", O(WheelSensitivity), V::Double(1.0)),')
        insert_after(ui, '            Opt::WheelSensitivity => "options.mouseWheelSensitivity",',
                     f'            Opt::{variant} => "options.{key}",')
        insert_after(ui, '            Opt::WheelSensitivity => unit(s.mouse_wheel_sensitivity, 0.01, 10.0),',
                     f'            Opt::{variant} => unit(s.{field}, 0.1, 3.0),')
        insert_after(ui, '            Opt::WheelSensitivity => s.mouse_wheel_sensitivity = 0.01 + v * 9.99,',
                     f'            Opt::{variant} => s.{field} = 0.1 + v * 2.9,')
        replace_once(ui, '            Opt::Volume(i) => {\n                let v = if i == 0 {',
                     f'            Opt::{variant} => generic(c, Text::literal(format!("{{:.2}}×", s.{field}))),\n'
                     '            Opt::Volume(i) => {\n                let v = if i == 0 {')


def insert_after(path: Path, line: str, addition: str) -> None:
    replace_once(path, line, line + "\n" + addition)


def patch_zoom(source: Path) -> None:
    client = source / "crates/horizon-client/src"
    keybinds = source / "crates/horizon-client-input/src/keybinds.rs"
    replace_once(keybinds, '    ("key.smoothCamera", "key.keyboard.unknown", "misc"),',
                 '    ("key.smoothCamera", "key.keyboard.unknown", "misc"),\n'
                 '    ("key.votex.zoom", "key.keyboard.c", "misc"),')
    replace_once(keybinds, '.filter(|m| m.2 != "debug")',
                 '.filter(|m| m.2 != "debug" && m.0 != "key.votex.zoom")')
    replace_once(keybinds, '*d == key && *c != "debug" && !self.is_default(n)',
                 '*d == key && *c != "debug" && *n != "key.votex.zoom" && !self.is_default(n)')
    replace_once(client / "game.rs", "        let prepare = spans::guard(\"prepare\");",
                 "        self.votex_zoom_frame();\n        let prepare = spans::guard(\"prepare\");")
    replace_once(client / "game.rs", "                    // A prompt is vanilla's `Minecraft.screen`,",
                 "                    if self.votex_zoom_scroll(raw_y as f32) {\n"
                 "                        return Ok(());\n"
                 "                    }\n"
                 "                    // A prompt is vanilla's `Minecraft.screen`,")
    extras = client / "game/extras.rs"
    replace_once(extras, "    fps: FpsCounter,", "    votex_zoom: VotexZoom,\n    fps: FpsCounter,")
    replace_once(extras, "        self.native_zoom_now().map_or",
                 "        if self.extras.votex_zoom.current > 0.0 && !self.scoping() {\n"
                 "            let factor = self.extras.votex_zoom.current.exp();\n"
                 "            return (factor, false, f64::from(factor.recip()));\n"
                 "        }\n"
                 "        self.native_zoom_now().map_or")
    replace_once(extras, "(factor, false, _) => fov / factor,",
                 "(factor, false, _) => (fov / factor).max(1.0),")
    with extras.open("ab") as output:
        output.write(b"\n" + Path(__file__).with_name("votex-engine-zoom.rs").read_bytes())


def replace_once(path: Path, original: str, replacement: str) -> None:
    data = path.read_bytes()
    before = original.encode()
    if data.count(before) != 1:
        raise ValueError(f"Unexpected upstream code in {path}: {original}")
    path.write_bytes(data.replace(before, replacement.encode()))


def patch_title(source: Path) -> None:
    title = source / "crates/horizon-client-render/src/title_edition.rs"
    original = title.read_bytes()
    call = b".then(|| compose(pixels))"
    if original.count(call) != 1:
        raise ValueError("Unexpected upstream title texture hook")
    # Keep the upstream composition checks and update the real texture
    # assertion for the branded output.
    assertion = b'columns(&out, EDITION_START..WIDTH),\n            columns(&pixels, EDITION_START..WIDTH)'
    normalized = original.replace(b"\r\n", b"\n")
    if normalized.count(assertion) != 1:
        raise ValueError("Unexpected upstream title texture assertion")
    normalized = normalized.replace(assertion, b"out.len(), WIDTH * HEIGHT * 4")
    banner = Path(__file__).with_name("votex-engine-title.rs").read_bytes()
    title.write_bytes(normalized.replace(call, b".then(|| votex_banner(pixels))") + b"\n" + banner)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    with args.archive.open("rb") as archive:
        if hashlib.file_digest(archive, "sha256").hexdigest() != UPSTREAM_SHA256:
            parser.error("Archive SHA-256 does not match the pinned upstream source")
        archive.seek(0)
        output = args.output.absolute()
        if output.exists():
            parser.error("Output already exists. Choose a fresh directory.")
        output.parent.mkdir(parents=True, exist_ok=True)
        with TemporaryDirectory(dir=output.parent) as temporary:
            stage = Path(temporary)
            with ZipFile(archive) as upstream:
                upstream.extractall(stage)
            source = stage / "horizon-main"
            patch(source)
            source.rename(output)
    print(output)


if __name__ == "__main__":
    main()
