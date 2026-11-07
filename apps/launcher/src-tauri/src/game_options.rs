use crate::storage::{atomic, Context, Profile, Result};
use std::{collections::BTreeMap, path::Path};
const EXTRA: &[&str] = &[
    "ao",
    "biomeBlendRadius",
    "chunkSectionFadeInTime",
    "cutoutLeaves",
    "entityDistanceScaling",
    "entityShadows",
    "fovEffectScale",
    "darknessEffectScale",
    "glintSpeed",
    "glintStrength",
    "gamma",
    "maxAnisotropyBit",
    "textureFiltering",
    "mipmapLevels",
    "particles",
    "renderClouds",
    "cloudRange",
    "screenEffectScale",
    "vignette",
    "weatherRadius",
    "improvedTransparency",
    "prioritizeChunkUpdates",
];
pub(crate) fn validate_extra(values: &BTreeMap<String, String>) -> Result<()> {
    if values.len() > 32
        || values
            .iter()
            .any(|(k, v)| !EXTRA.contains(&k.as_str()) || v.len() > 128 || v.contains(['\r', '\n']))
    {
        return Err("Invalid saved visual game options".into());
    }
    Ok(())
}
pub fn stamp(root: &Path) -> [Option<(u64, std::time::SystemTime)>; 3] {
    [
        "options.txt",
        "config/iris.properties",
        "config/votex/performance.json",
    ]
    .map(|path| {
        std::fs::metadata(root.join(path))
            .ok()
            .and_then(|m| m.modified().ok().map(|time| (m.len(), time)))
    })
}
pub fn merge(old: &str, p: &Profile) -> String {
    let mut chosen = BTreeMap::from([
        ("renderDistance", p.render_distance.to_string()),
        ("simulationDistance", p.simulation_distance.to_string()),
        (
            "maxFps",
            if p.max_fps == 0 {
                "260".into()
            } else {
                p.max_fps.to_string()
            },
        ),
        (
            "graphicsMode",
            match p.graphics.as_str() {
                "fast" => "0",
                "fabulous" => "2",
                _ => "1",
            }
            .into(),
        ),
        ("fancyGraphics", (p.graphics != "fast").to_string()),
        ("enableVsync", p.vsync.to_string()),
        ("guiScale", p.gui_scale.to_string()),
        ("fov", ((p.fov - 70.0) / 40.0).to_string()),
    ]);
    if p.minecraft.starts_with("26.") {
        chosen.insert("graphicsPreset", format!("\"{}\"", p.graphics));
        chosen.insert("cutoutLeaves", (p.graphics != "fast").to_string());
        chosen.insert(
            "improvedTransparency",
            (p.graphics == "fabulous").to_string(),
        );
    }
    // In Minecraft's options format, 260 is the unlimited slider value on modern versions.
    for (key, value) in &p.game_options {
        if EXTRA.contains(&key.as_str()) {
            chosen.insert(key.as_str(), value.clone());
        }
    }
    let mut lines = Vec::new();
    for line in old.lines() {
        if let Some((key, _)) = line.split_once(':') {
            if let Some(value) = chosen.remove(key) {
                lines.push(format!("{key}:{value}"));
                continue;
            }
            if matches!(
                key,
                "renderDistance"
                    | "simulationDistance"
                    | "maxFps"
                    | "graphicsMode"
                    | "fancyGraphics"
                    | "enableVsync"
                    | "graphicsPreset"
                    | "cutoutLeaves"
                    | "improvedTransparency"
                    | "guiScale"
                    | "fov"
            ) || EXTRA.contains(&key)
            {
                continue;
            }
        }
        lines.push(line.to_string());
    }
    for (k, v) in chosen {
        lines.push(format!("{k}:{v}"));
    }
    format!("{}\n", lines.join("\n"))
}
pub fn apply(dir: &Path, p: &Profile) -> Result<()> {
    let path = dir.join("options.txt");
    let receipt = dir.join("config/votex/game-options.json");
    let desired = desired(p);
    if path.exists() {
        if crate::storage::read_json(&receipt).ok().as_ref() == Some(&desired) {
            return Ok(());
        }
        if !receipt.exists() && p.game_options_revision == 0 {
            return atomic(
                &receipt,
                &serde_json::to_vec(&desired).context("Save preserved game-option baseline")?,
            );
        }
    }
    let old = if path.exists() {
        std::fs::read_to_string(&path).context("Read Minecraft options")?
    } else {
        String::new()
    };
    atomic(&path, merge(&old, p).as_bytes())?;
    let iris = dir.join("config/iris.properties");
    if iris.exists() || p.shader_enabled {
        let mut lines: Vec<_> = std::fs::read_to_string(&iris)
            .unwrap_or_default()
            .lines()
            .filter(|line| !line.starts_with("enableShaders="))
            .map(str::to_owned)
            .collect();
        lines.push(format!("enableShaders={}", p.shader_enabled));
        atomic(&iris, format!("{}\n", lines.join("\n")).as_bytes())?;
    }
    if let Some(settings) = &p.engine_settings {
        crate::performance::validate(settings)?;
        atomic(
            &dir.join("config/votex/performance.json"),
            &serde_json::to_vec_pretty(settings).context("Apply saved performance class")?,
        )?;
    }
    atomic(
        &receipt,
        &serde_json::to_vec(&desired).context("Save applied game-option baseline")?,
    )
}
fn desired(p: &Profile) -> serde_json::Value {
    serde_json::json!({"renderDistance":p.render_distance,"simulationDistance":p.simulation_distance,"maxFps":p.max_fps,"graphics":p.graphics,"gameOptions":p.game_options,"vsync":p.vsync,"guiScale":p.gui_scale,"fov":p.fov,"shaderEnabled":p.shader_enabled,"engineSettings":p.engine_settings,"revision":p.game_options_revision})
}
/// Only import a game's saved values when no launcher edit is waiting to be applied.
/// The launcher owns its JSON; Minecraft owns options.txt. Never rewrite options.txt here.
pub fn sync(store: &crate::storage::Store, id: &str) -> Result<bool> {
    sync_inner(store, id, false)
}
pub fn sync_engine(store: &crate::storage::Store, id: &str) -> Result<bool> {
    sync_inner(store, id, true)
}
fn sync_inner(store: &crate::storage::Store, id: &str, force_engine: bool) -> Result<bool> {
    let _guard = store
        .lock
        .lock()
        .map_err(|_| "Configuration lock unavailable")?;
    let mut state = store.state()?;
    let index = state["profiles"]
        .as_array()
        .ok_or("Missing profiles")?
        .iter()
        .position(|p| p["id"] == id)
        .ok_or("Unknown profile")?;
    let mut p: Profile = serde_json::from_value(state["profiles"][index].clone())
        .context("Read profile for option sync")?;
    let root = store.instance(id)?;
    let path = root.join("options.txt");
    if path.exists()
        && std::fs::metadata(&path)
            .context("Check Minecraft options")?
            .len()
            > 256_000
    {
        return Err("Minecraft options exceed limit".into());
    }
    let receipt_path = root.join("config/votex/game-options.json");
    let baseline = crate::storage::read_json(&receipt_path).unwrap_or_default();
    let want = desired(&p);
    let game_allowed = if baseline.is_null() {
        p.game_options_revision == 0
    } else {
        baseline
            .as_object()
            .map(|o| o.iter().all(|(k, v)| want.get(k) == Some(v)))
            .unwrap_or(false)
    };
    if !game_allowed && !force_engine {
        return Ok(false);
    }
    let old = serde_json::to_value(&p).context("Read prior game values")?;
    if game_allowed && path.exists() {
        import(
            &std::fs::read_to_string(path).context("Read game settings")?,
            &mut p,
        );
    }
    let iris = root.join("config/iris.properties");
    if game_allowed
        && iris.exists()
        && std::fs::metadata(&iris)
            .context("Check shader settings")?
            .len()
            < 64_000
    {
        if let Some(value) = std::fs::read_to_string(iris)
            .context("Read shader settings")?
            .lines()
            .filter_map(|line| line.strip_prefix("enableShaders="))
            .last()
            .and_then(|v| v.parse::<bool>().ok())
        {
            p.shader_enabled = value;
        }
    }
    let engine = root.join("config/votex/performance.json");
    if engine.exists() {
        if std::fs::metadata(&engine)
            .context("Check engine settings")?
            .len()
            > 16_000
        {
            return Err("Performance settings exceed limit".into());
        }
        let value = crate::storage::read_json(&engine)?;
        crate::performance::validate(&value)?;
        p.engine_settings = Some(value);
    }
    let next = serde_json::to_value(&p).context("Encode synchronized game values")?;
    if next == old {
        return Ok(false);
    }
    if p.preset != "custom" {
        let values = crate::storage::PresetValues {
            memory: p.memory,
            performance_mods: Some(p.performance_mods),
            render_distance: p.render_distance,
            simulation_distance: p.simulation_distance,
            max_fps: p.max_fps,
            graphics: p.graphics.clone(),
            game_options: p.game_options.clone(),
            vsync: p.vsync,
            gui_scale: p.gui_scale,
            fov: p.fov,
            engine_settings: p.engine_settings.clone(),
            shader_enabled: p.shader_enabled,
        };
        let value = serde_json::to_value(&values).context("Encode shared class settings")?;
        if !state["gamePresetValues"].is_object() {
            state["gamePresetValues"] = serde_json::json!({});
        }
        state["gamePresetValues"][&p.preset] = value;
        p.preset_values.insert(p.preset.clone(), values);
    }
    state["profiles"][index] = serde_json::to_value(&p).context("Encode game settings")?;
    crate::storage::validate_state(&state)?;
    atomic(
        &store.root.join("config/launcher.json"),
        &serde_json::to_vec_pretty(&state).context("Save game settings in launcher")?,
    )?;
    if game_allowed {
        atomic(
            &receipt_path,
            &serde_json::to_vec(&desired(&p)).context("Save synchronized baseline")?,
        )?;
    } else if baseline.is_object() {
        let mut patched = baseline;
        patched["engineSettings"] = serde_json::json!(p.engine_settings);
        atomic(
            &receipt_path,
            &serde_json::to_vec(&patched).context("Save engine baseline")?,
        )?;
    }
    Ok(true)
}
fn import(text: &str, p: &mut Profile) {
    let options: BTreeMap<_, _> = text
        .lines()
        .filter_map(|line| line.split_once(':'))
        .collect();
    let integer = |key: &str, min: u32, max: u32| {
        options
            .get(key)
            .and_then(|v| v.parse::<u32>().ok())
            .filter(|n| *n >= min && *n <= max)
    };
    if let Some(v) = integer("renderDistance", 2, 32) {
        p.render_distance = v;
    }
    if let Some(v) = integer("simulationDistance", 2, 32) {
        p.simulation_distance = v;
    }
    if let Some(v) = integer("maxFps", 10, 260) {
        p.max_fps = if v >= 260 { 0 } else { v.min(250) };
    }
    if let Some(v) = integer("guiScale", 0, 8) {
        p.gui_scale = v;
    }
    if let Some(v) = options
        .get("fov")
        .and_then(|v| v.parse::<f64>().ok())
        .filter(|v| v.is_finite() && (-1.0..=1.0).contains(v))
    {
        p.fov = ((v * 40.0 + 70.0) * 100.0).round() / 100.0;
    }
    if let Some(v) = options
        .get("enableVsync")
        .and_then(|v| v.parse::<bool>().ok())
    {
        p.vsync = v;
    }
    for key in EXTRA {
        if let Some(value) = options.get(key).filter(|v| v.len() <= 128) {
            p.game_options.insert(key.to_string(), value.to_string());
        }
    }
    if let Some(v) = options
        .get("graphicsPreset")
        .map(|v| v.trim_matches('"'))
        .filter(|v| ["fast", "fancy", "fabulous", "custom"].contains(v))
    {
        p.graphics = v.into();
    } else if let Some(v) = integer("graphicsMode", 0, 2) {
        p.graphics = ["fast", "fancy", "fabulous"][v as usize].into();
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn native_engine_preferences_are_inherited_without_consuming_pending_game_changes() {
        let temp = tempfile::tempdir().unwrap();
        let store = crate::storage::Store::new(temp.path().join("votex")).unwrap();
        let mut state: serde_json::Value = serde_json::from_slice(include_bytes!(
            "../../../../packages/shared-config/defaults.json"
        ))
        .unwrap();
        state["profiles"][0]["gameOptionsRevision"] = serde_json::json!(1);
        state["profiles"][0]["maxFps"] = serde_json::json!(165);
        atomic(
            &store.root.join("config/launcher.json"),
            &serde_json::to_vec(&state).unwrap(),
        )
        .unwrap();
        let engine = serde_json::json!({"schemaVersion":1,"textCache":false,"reusableMotion":true,"adaptive":"BALANCED","targetFps":144});
        crate::performance::save(&store, "main", engine.clone()).unwrap();
        assert_eq!(
            store.state().unwrap()["gamePresetValues"]["balanced"]["engineSettings"],
            engine
        );
        let p = store.profile("main").unwrap();
        assert_eq!(p.game_options_revision, 1);
        assert_eq!(p.max_fps, 165);
        apply(&store.instance("main").unwrap(), &p).unwrap();
        assert_eq!(
            crate::storage::read_json(
                &store
                    .instance("main")
                    .unwrap()
                    .join("config/votex/performance.json")
            )
            .unwrap(),
            engine
        );
    }
    #[test]
    fn shader_disable_roundtrip_keeps_the_users_selected_shader() {
        let temp = tempfile::tempdir().unwrap();
        let mut p: Profile = serde_json::from_value(
            serde_json::from_slice::<serde_json::Value>(include_bytes!(
                "../../../../packages/shared-config/defaults.json"
            ))
            .unwrap()["profiles"][0]
                .clone(),
        )
        .unwrap();
        p.game_options_revision = 1;
        p.shader_enabled = true;
        let iris = temp.path().join("config/iris.properties");
        atomic(&iris, b"shaderPack=My-Shader.zip\nenableShaders=true\n").unwrap();
        apply(temp.path(), &p).unwrap();
        p.shader_enabled = false;
        p.game_options_revision = 2;
        apply(temp.path(), &p).unwrap();
        let result = std::fs::read_to_string(iris).unwrap();
        assert!(result.contains("shaderPack=My-Shader.zip"));
        assert!(result.contains("enableShaders=false"));
    }
    #[test]
    fn custom_graphics_and_saved_visual_options_roundtrip_without_keybind_copying() {
        let mut p: Profile = serde_json::from_value(
            serde_json::from_slice::<serde_json::Value>(include_bytes!(
                "../../../../packages/shared-config/defaults.json"
            ))
            .unwrap()["profiles"][0]
                .clone(),
        )
        .unwrap();
        import("graphicsPreset:\"custom\"\nmipmapLevels:2\nentityShadows:false\ngamma:0.7\nkey_key.forward:key.keyboard.z\n",&mut p);
        assert_eq!(p.graphics, "custom");
        assert_eq!(p.game_options["mipmapLevels"], "2");
        assert!(!p.game_options.contains_key("key_key.forward"));
        let copied = merge("mipmapLevels:4\nmipmapLevels:3\n", &p);
        assert_eq!(copied.matches("mipmapLevels:").count(), 1);
        assert!(copied.contains("mipmapLevels:2"));
        assert!(copied.contains("graphicsPreset:\"custom\""));
    }
    #[test]
    fn game_changes_update_launcher_and_class_without_rewriting_game_or_losing_other_profiles() {
        let temp = tempfile::tempdir().unwrap();
        let store = crate::storage::Store::new(temp.path().join("votex")).unwrap();
        let mut state: serde_json::Value = serde_json::from_slice(include_bytes!(
            "../../../../packages/shared-config/defaults.json"
        ))
        .unwrap();
        let mut other = state["profiles"][0].clone();
        other["id"] = serde_json::json!("other");
        state["profiles"]
            .as_array_mut()
            .unwrap()
            .push(other.clone());
        atomic(
            &store.root.join("config/launcher.json"),
            &serde_json::to_vec(&state).unwrap(),
        )
        .unwrap();
        let root = store.instance("main").unwrap();
        let mut p = store.profile("main").unwrap();
        p.game_options_revision = 1;
        state["profiles"][0] = serde_json::to_value(&p).unwrap();
        atomic(
            &store.root.join("config/launcher.json"),
            &serde_json::to_vec(&state).unwrap(),
        )
        .unwrap();
        apply(&root, &p).unwrap();
        let game=b"guiScale:3\nfov:0.5\nmaxFps:165\nrenderDistance:25\nsimulationDistance:12\nenableVsync:true\ngraphicsPreset:\"fabulous\"\nkey_key.forward:key.keyboard.z\n";
        atomic(&root.join("options.txt"), game).unwrap();
        assert!(sync(&store, "main").unwrap());
        let after = store.state().unwrap();
        assert_eq!(after["profiles"][1], other);
        assert_eq!(after["profiles"][0]["guiScale"], 3);
        assert_eq!(after["profiles"][0]["fov"], 90.0);
        assert_eq!(after["gamePresetValues"]["balanced"]["maxFps"], 165);
        assert_eq!(std::fs::read(root.join("options.txt")).unwrap(), game);
        assert!(!sync(&store, "main").unwrap());
        let mut pending = after.clone();
        pending["profiles"][0]["gameOptionsRevision"] = serde_json::json!(2);
        pending["profiles"][0]["maxFps"] = serde_json::json!(120);
        atomic(
            &store.root.join("config/launcher.json"),
            &serde_json::to_vec(&pending).unwrap(),
        )
        .unwrap();
        assert!(!sync(&store, "main").unwrap());
        assert_eq!(store.profile("main").unwrap().max_fps, 120);
    }
    #[test]
    fn invalid_and_partial_options_preserve_valid_values() {
        let mut p: Profile = serde_json::from_value(
            serde_json::from_slice::<serde_json::Value>(include_bytes!(
                "../../../../packages/shared-config/defaults.json"
            ))
            .unwrap()["profiles"][0]
                .clone(),
        )
        .unwrap();
        import(
            "fov:NaN\nguiScale:9999\nrenderDistance:-1\nmaxFps:260\nenableVsync:garbage\n",
            &mut p,
        );
        assert_eq!(p.fov, 70.0);
        assert_eq!(p.gui_scale, 0);
        assert_eq!(p.render_distance, 12);
        assert_eq!(p.max_fps, 0);
        assert!(!p.vsync);
    }
    #[test]
    fn migration_and_in_game_tuning_are_preserved_until_launcher_changes() {
        let temp = tempfile::tempdir().unwrap();
        let mut p: Profile = serde_json::from_value(
            serde_json::from_slice::<serde_json::Value>(include_bytes!(
                "../../../../packages/shared-config/defaults.json"
            ))
            .unwrap()["profiles"][0]
                .clone(),
        )
        .unwrap();
        let path = temp.path().join("options.txt");
        atomic(&path, b"renderDistance:25\nvolume:0.3\n").unwrap();
        apply(temp.path(), &p).unwrap();
        assert!(std::fs::read_to_string(&path)
            .unwrap()
            .contains("renderDistance:25"));
        p.game_options_revision = 1;
        p.render_distance = 10;
        apply(temp.path(), &p).unwrap();
        assert!(std::fs::read_to_string(&path)
            .unwrap()
            .contains("renderDistance:10"));
        atomic(&path, b"renderDistance:18\nvolume:0.3\n").unwrap();
        apply(temp.path(), &p).unwrap();
        assert!(std::fs::read_to_string(&path)
            .unwrap()
            .contains("renderDistance:18"));
        p.game_options_revision = 2;
        apply(temp.path(), &p).unwrap();
        assert!(std::fs::read_to_string(&path)
            .unwrap()
            .contains("renderDistance:10"));
    }
    #[test]
    fn preserves_unrelated_options() {
        let p: Profile = serde_json::from_value(
            serde_json::from_slice::<serde_json::Value>(include_bytes!(
                "../../../../packages/shared-config/defaults.json"
            ))
            .unwrap()["profiles"][0]
                .clone(),
        )
        .unwrap();
        let s = merge(
            "volume:0.4\nkey_key.forward:key.keyboard.w\nrenderDistance:25\nrenderDistance:7\n",
            &p,
        );
        assert!(s.contains("volume:0.4"));
        assert!(s.contains("key_key.forward:key.keyboard.w"));
        assert_eq!(s.matches("renderDistance:").count(), 1);
        assert!(s.contains("renderDistance:12"));
    }
}
