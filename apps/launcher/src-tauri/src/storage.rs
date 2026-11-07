use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::{
    fs,
    path::{Component, Path, PathBuf},
    sync::Mutex,
};
pub type Result<T> = std::result::Result<T, String>;
pub trait Context<T> {
    fn context(self, message: &str) -> Result<T>;
}
impl<T, E: std::fmt::Display> Context<T> for std::result::Result<T, E> {
    fn context(self, m: &str) -> Result<T> {
        self.map_err(|e| format!("{m}: {e}"))
    }
}
pub struct Store {
    pub root: PathBuf,
    pub lock: Mutex<()>,
    pub busy: Mutex<bool>,
    pub running_games: std::sync::atomic::AtomicUsize,
    pub(crate) recovery_pins: Mutex<std::collections::HashSet<(String, String)>>,
}
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct Profile {
    pub id: String,
    pub name: String,
    #[serde(default)]
    pub engine: GameEngine,
    pub minecraft: String,
    pub loader: String,
    pub memory: u32,
    pub java: String,
    pub preset: String,
    pub loader_version: String,
    #[serde(default = "default_render_distance")]
    pub render_distance: u32,
    #[serde(default = "default_simulation_distance")]
    pub simulation_distance: u32,
    #[serde(default = "default_max_fps")]
    pub max_fps: u32,
    #[serde(default = "default_graphics")]
    pub graphics: String,
    #[serde(default)]
    pub game_options: std::collections::BTreeMap<String,String>,
    #[serde(default)]
    pub vsync: bool,
    #[serde(default)]
    pub gui_scale: u32,
    #[serde(default = "default_fov")]
    pub fov: f64,
    #[serde(default, skip_serializing_if="Option::is_none")]
    pub engine_settings: Option<Value>,
    #[serde(default, skip_serializing_if="Option::is_none")]
    pub starter_mods: Option<StarterMods>,
    #[serde(default)]
    pub performance_mods: bool,
    #[serde(default)]
    pub shader_enabled: bool,
    #[serde(default)]
    pub game_options_revision: u32,
    #[serde(default)]
    pub preset_values: std::collections::BTreeMap<String, PresetValues>,
}
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "lowercase")]
pub enum GameEngine {
    #[default]
    Java,
    Horizon,
}
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct PresetValues {
    pub memory: u32,
    #[serde(default, skip_serializing_if="Option::is_none")]
    pub performance_mods: Option<bool>,
    pub render_distance: u32,
    pub simulation_distance: u32,
    pub max_fps: u32,
    pub graphics: String,
    #[serde(default)]
    pub game_options: std::collections::BTreeMap<String,String>,
    pub vsync: bool,
    #[serde(default)]
    pub gui_scale: u32,
    #[serde(default = "default_fov")]
    pub fov: f64,
    #[serde(default, skip_serializing_if="Option::is_none")]
    pub engine_settings: Option<Value>,
    pub shader_enabled: bool,
}
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all="camelCase", deny_unknown_fields)]
pub struct StarterMods {
    pub sodium: bool, pub iris: bool, pub modmenu: bool, pub zoomify: bool,
    pub more: bool, pub extras: Vec<String>, pub shaders: Vec<String>,
}
fn default_fov() -> f64 { 70.0 }
fn default_render_distance() -> u32 {
    12
}
fn default_simulation_distance() -> u32 {
    8
}
fn default_max_fps() -> u32 {
    144
}
fn default_graphics() -> String {
    "fancy".into()
}
pub fn safe_id(s: &str) -> Result<()> {
    if s.is_empty()
        || s.len() > 80
        || !s
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
    {
        return Err("Invalid identifier".into());
    }
    Ok(())
}
pub fn relative(root: &Path, s: &str) -> Result<PathBuf> {
    if s.is_empty()
        || s.contains('\\')
        || s.contains(':')
        || s.split('/').any(|p| p.is_empty() || p == "." || p == "..")
    {
        return Err("Unsafe relative path".into());
    }
    let path = Path::new(s);
    for part in s.split('/') {
        let stem = part.split('.').next().unwrap_or("").to_ascii_uppercase();
        if part.ends_with('.')
            || part.ends_with(' ')
            || part.chars().any(|c| c < ' ' || "<>\"|?*".contains(c))
            || matches!(stem.as_str(), "CON" | "PRN" | "AUX" | "NUL")
            || ((stem.starts_with("COM") || stem.starts_with("LPT"))
                && stem.len() == 4
                && matches!(stem.as_bytes()[3], b'1'..=b'9'))
        {
            return Err("Unsafe Windows filename".into());
        }
    }
    if !path.components().all(|c| matches!(c, Component::Normal(_))) {
        return Err("Unsafe path".into());
    }
    let target = root.join(path);
    // Refuse symlinks/junctions in existing ancestors, including the destination.
    let mut check = target.as_path();
    loop {
        if let Ok(meta) = fs::symlink_metadata(check) {
            if meta.file_type().is_symlink() {
                return Err("Linked paths are not supported".into());
            }
        }
        if check == root {
            break;
        }
        check = check.parent().ok_or("Path has no parent")?;
    }
    Ok(target)
}
pub fn atomic(path: &Path, bytes: &[u8]) -> Result<()> {
    fs::create_dir_all(path.parent().ok_or("Missing parent")?).context("Create directory")?;
    let temp = path.with_extension(format!("tmp-{}", uuid::Uuid::new_v4()));
    {
        use std::io::Write;
        let mut file = fs::File::create(&temp).context("Create temporary file")?;
        file.write_all(bytes).context("Write file")?;
        file.sync_all().context("Flush file")?;
    }
    // MoveFileExW replaces an existing destination atomically on Windows.
    #[cfg(windows)]
    {
        use std::os::windows::ffi::OsStrExt;
        #[link(name = "kernel32")]
        extern "system" {
            fn MoveFileExW(from: *const u16, to: *const u16, flags: u32) -> i32;
        }
        // std::fs handles long Windows paths, but raw Win32 APIs require the extended
        // prefix explicitly. Canonicalizing the existing parent supplies that prefix
        // even when the destination does not exist yet.
        let parent = fs::canonicalize(path.parent().ok_or("Missing parent")?)
            .context("Resolve atomic destination")?;
        let source = parent.join(temp.file_name().ok_or("Missing temporary filename")?);
        let destination = parent.join(path.file_name().ok_or("Missing destination filename")?);
        let a: Vec<u16> = source.as_os_str().encode_wide().chain(Some(0)).collect();
        let b: Vec<u16> = destination.as_os_str().encode_wide().chain(Some(0)).collect();
        if unsafe { MoveFileExW(a.as_ptr(), b.as_ptr(), 1 | 8) } == 0 {
            let error = std::io::Error::last_os_error();
            let _ = fs::remove_file(temp);
            return Err(format!("Atomic replacement: {error}"));
        }
    }
    #[cfg(not(windows))]
    fs::rename(&temp, path).context("Atomic replacement")?;
    Ok(())
}
pub fn read_json(path: &Path) -> Result<Value> {
    serde_json::from_slice(&fs::read(path).context("Read configuration")?).context("Invalid JSON")
}
pub fn physical_path(path: &Path) -> Result<PathBuf> {
    let resolved = fs::canonicalize(path).context("Resolve physical application path")?;
    #[cfg(windows)]
    {
        use std::os::windows::ffi::{OsStrExt, OsStringExt};
        let wide: Vec<u16> = resolved.as_os_str().encode_wide().collect();
        if wide.starts_with(&[92, 92, 63, 92, 85, 78, 67, 92]) {
            let mut normal = vec![92, 92];
            normal.extend_from_slice(&wide[8..]);
            return Ok(PathBuf::from(std::ffi::OsString::from_wide(&normal)));
        }
        if wide.starts_with(&[92, 92, 63, 92]) {
            return Ok(PathBuf::from(std::ffi::OsString::from_wide(&wide[4..])));
        }
    }
    Ok(resolved)
}
pub fn png_data(data: &str, cape: bool) -> Result<Vec<u8>> {
    use base64::Engine;
    if data.len() > 1_500_000 {
        return Err("PNG exceeds the local image limit".into());
    }
    let encoded = data
        .strip_prefix("data:image/png;base64,")
        .ok_or("Expected a PNG image")?;
    let bytes = base64::engine::general_purpose::STANDARD
        .decode(encoded)
        .context("Invalid PNG encoding")?;
    if bytes.len() < 33 || !bytes.starts_with(b"\x89PNG\r\n\x1a\n") || &bytes[12..16] != b"IHDR" {
        return Err("Invalid PNG header".into());
    }
    let w = u32::from_be_bytes(bytes[16..20].try_into().unwrap());
    let h = u32::from_be_bytes(bytes[20..24].try_into().unwrap());
    if w != 64 || if cape { h != 32 } else { h != 32 && h != 64 } {
        return Err("Invalid skin or cape dimensions".into());
    }
    Ok(bytes)
}
fn validate_background_video(data: &str) -> Result<()> {
    use base64::Engine;
    if data.len() > 7_000_000 {
        return Err("Background video exceeds the encoded size limit".into());
    }
    let (mime, encoded) = data.split_once(',').ok_or("Invalid background video")?;
    if !matches!(mime, "data:video/mp4;base64" | "data:video/webm;base64") {
        return Err("Unsupported background video type".into());
    }
    let bytes = base64::engine::general_purpose::STANDARD
        .decode(encoded)
        .map_err(|_| "Invalid background video encoding")?;
    if bytes.is_empty() || bytes.len() > 5_000_000 {
        return Err("Background video exceeds the local 5 MB limit".into());
    }
    let valid = match mime {
        "data:video/mp4;base64" => bytes.len() >= 12 && &bytes[4..8] == b"ftyp",
        "data:video/webm;base64" => bytes.starts_with(&[0x1a, 0x45, 0xdf, 0xa3]),
        _ => false,
    };
    if !valid {
        return Err("Background video header does not match its media type".into());
    }
    Ok(())
}
fn validate_effects(t: &Value, optional: bool) -> Result<()> {
    for k in [
        "transparency",
        "blur",
        "wobble",
        "speed",
        "saturation",
        "glow",
        "reflection",
        "shadow",
        "stiffness",
        "damping",
        "proximity",
        "lift",
        "tilt",
    ] {
        if optional && t.get(k).is_none() {
            continue;
        }
        let n = t[k].as_f64().ok_or("Invalid visual effect value")?;
        if !(0.0..=100.0).contains(&n) {
            return Err("Visual effect out of range".into());
        }
    }
    for k in ["hover", "reducedMotion", "ambientMotion"] {
        if (!optional || t.get(k).is_some()) && !t[k].is_boolean() {
            return Err("Invalid visual effect switch".into());
        }
    }
    Ok(())
}
impl Store {
    pub fn new(root: PathBuf) -> Result<Self> {
        for d in [
            "config",
            "accounts",
            "profiles",
            "instances",
            "skins",
            "cosmetics",
            "cache",
            "logs",
            "backups",
        ] {
            fs::create_dir_all(root.join(d)).context("Create application data")?;
        }
          // Windows packaged-app filesystem redirection may expose one directory via
          // two names. Fabric compares CodeSource URLs with Java's toRealPath(), which
          // does not resolve that redirection. Give Java one physical path everywhere.
          let root = physical_path(&root)?;
          Ok(Self {
              root,
            lock: Mutex::new(()),
            busy: Mutex::new(false),
            running_games: std::sync::atomic::AtomicUsize::new(0),
            recovery_pins: Mutex::new(std::collections::HashSet::new()),
        })
    }
    pub fn state(&self) -> Result<Value> {
        read_json(&self.root.join("config/launcher.json"))
    }
    pub fn profile(&self, id: &str) -> Result<Profile> {
        safe_id(id)?;
        let state = self.state()?;
        let p = state["profiles"]
            .as_array()
            .ok_or("Missing profiles")?
            .iter()
            .find(|p| p["id"] == id)
            .ok_or("Unknown profile")?;
        serde_json::from_value(p.clone()).context("Invalid profile")
    }
    pub fn instance_lease(&self, id: &str) -> Result<fs::File> {
        safe_id(id)?;
        let path = self.root.join("profiles").join(format!("{id}.launch.lock"));
        let file = fs::OpenOptions::new().read(true).write(true).create(true)
            .truncate(false).open(path).context("Open profile lock")?;
        match file.try_lock() {
            Ok(()) => Ok(file),
            Err(std::fs::TryLockError::WouldBlock) => Err("Dieses Profil läuft bereits oder wird gerade geändert.".into()),
            Err(std::fs::TryLockError::Error(error)) => Err(format!("Profilsperre: {error}")),
        }
    }
    pub fn instance(&self, id: &str) -> Result<PathBuf> {
        safe_id(id)?;
        relative(&self.root, &format!("instances/{id}"))
    }
}
pub fn validate_state(s: &Value) -> Result<()> {
    let profiles: Vec<Profile> = serde_json::from_value(
        s.get("profiles").cloned().ok_or("Missing profiles")?,
    )
    .context("Invalid profile")?;
    for profile in profiles {
        if profile.engine == GameEngine::Horizon
            && (profile.minecraft != "26.3" || profile.loader != "vanilla")
        {
            return Err("Horizon-Profile müssen Minecraft 26.3 mit Vanilla verwenden.".into());
        }
    }
    if let Some(bank)=s.get("gamePresetValues") {
        let values:std::collections::BTreeMap<String,PresetValues>=serde_json::from_value(bank.clone()).context("Invalid shared game classes")?;
        if values.len()>4 {return Err("Too many game classes".into());}
        for (name,v) in values {
            if !["balanced","performance","extreme","cinematic"].contains(&name.as_str()) || !(1024..=32768).contains(&v.memory) || !(2..=32).contains(&v.render_distance) || !(2..=32).contains(&v.simulation_distance) || (v.max_fps!=0&&!(10..=250).contains(&v.max_fps)) || !["fast","fancy","fabulous","custom"].contains(&v.graphics.as_str()) || v.gui_scale>8 || !v.fov.is_finite() || !(30.0..=110.0).contains(&v.fov) {return Err("Invalid shared game class".into());}
            crate::game_options::validate_extra(&v.game_options)?;
            if let Some(engine)=&v.engine_settings {crate::performance::validate(engine)?;}
        }
    }
    if serde_json::to_vec(s)
        .context("Measure launcher configuration")?
        .len()
        > 8_000_000
    {
        return Err("Launcher configuration is too large".into());
    }
    if s["schemaVersion"] != 1 {
        return Err("Unsupported configuration version".into());
    }
    for key in ["syncDesign", "minimizeOnLaunch", "idleSkin"] {
        if !s[key].is_boolean() {
            return Err(format!("Missing boolean {key}"));
        }
    }
    if !matches!(s["language"].as_str(), Some("en" | "de")) {
        return Err("Unsupported language".into());
    }
    if s.get("gameRuntime")
        .is_some_and(|runtime| !matches!(runtime.as_str(), Some("java" | "pomme")))
    {
        return Err("Unsupported Minecraft runtime".into());
    }
    // Older launchers have no characterView. New values remain launcher-only.
    if let Some(view) = s.get("characterView") {
        if !view.is_object()
            || !matches!(view["mode"].as_str(), Some("showcase" | "scene"))
            || !matches!(view["scene"].as_str(), Some("cloud-island" | "studio" | "night-garden"))
            || !matches!(view["lighting"].as_str(), Some("studio" | "daylight" | "moonlight"))
        {
            return Err("Invalid character view settings".into());
        }
    }
    if s["clientId"].as_str().map(|v| v.len() > 80).unwrap_or(true) {
        return Err("Invalid Microsoft Application ID value".into());
    }
    for key in ["skins", "cosmetics"] {
        let items = s[key].as_array().ok_or(format!("Missing {key}"))?;
        if items.len() > 100 {
            return Err("Too many presets".into());
        }
        let mut ids = std::collections::HashSet::new();
        for item in items {
            let id = item["id"].as_str().ok_or("Missing preset ID")?;
            safe_id(id)?;
            if !ids.insert(id) {
                return Err("Duplicate preset IDs".into());
            }
            let name = item["name"].as_str().ok_or("Missing preset name")?;
            if name.is_empty() || name.chars().count() > 60 {
                return Err("Invalid preset name".into());
            }
        }
    }
    for skin in s["skins"].as_array().unwrap() {
        let data = skin["data"].as_str().ok_or("Missing skin PNG")?;
        if data.len() > 1_500_000
            || !data.starts_with("data:image/png;base64,")
            || !data[22..]
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || b"+/=".contains(&b))
            || !matches!(skin["model"].as_str(), Some("default" | "slim"))
        {
            return Err("Invalid skin preset".into());
        }
    }
    for cosmetic in s["cosmetics"].as_array().unwrap() {
        if !cosmetic["cape"].is_boolean()
            || cosmetic["skin"]
                .as_str()
                .map(|v| v.len() > 80)
                .unwrap_or(true)
        {
            return Err("Invalid cosmetic preset".into());
        }
        if let Some(e) = cosmetic.get("equipment") {
            let e = e.as_object().ok_or("Invalid equipment")?;
            if e.len() > 9 {
                return Err("Too many equipment slots".into());
            }
            for (slot, id) in e {
                if !matches!(
                    slot.as_str(),
                    "wings"
                        | "head"
                        | "face"
                        | "pet"
                        | "back"
                        | "aura"
                        | "overlay"
                        | "feet"
                        | "cape"
                ) {
                    return Err("Unknown equipment slot".into());
                }
                let id = id.as_str().ok_or("Invalid equipment ID")?;
                if id.len() > 60
                    || !id
                        .bytes()
                        .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == b'-')
                {
                    return Err("Invalid equipment ID".into());
                }
            }
        }
        if let Some(data) = cosmetic.get("capeData") {
            let data = data.as_str().ok_or("Invalid cape")?;
            if !data.is_empty() {
                png_data(data, true)?;
            }
        }
        for k in [
            "halo",
            "horns",
            "ears",
            "crown",
            "headphones",
            "backpack",
            "scarf",
            "shoes",
            "tail",
            "shoulderCloud",
        ] {
            if cosmetic.get(k).is_some() && !cosmetic[k].is_boolean() {
                return Err("Invalid wearable switch".into());
            }
        }
        if let Some(w) = cosmetic.get("wings") {
            if !matches!(w.as_str(), Some("none" | "angel" | "dragon" | "crystal")) {
                return Err("Invalid wing style".into());
            }
        }
        for k in ["color", "secondary"] {
            if let Some(c) = cosmetic.get(k) {
                let c = c.as_str().ok_or("Invalid wearable color")?;
                if c.len() != 7
                    || !c.starts_with('#')
                    || !c[1..].bytes().all(|b| b.is_ascii_hexdigit())
                {
                    return Err("Invalid wearable color".into());
                }
            }
        }
        if let Some(v) = cosmetic.get("scale") {
            if !v
                .as_f64()
                .map(|v| (50.0..=150.0).contains(&v))
                .unwrap_or(false)
            {
                return Err("Invalid wearable scale".into());
            }
        }
    }
    let mut custom_emotes = std::collections::HashSet::new();
    if let Some(items) = s.get("customEmotes") {
        let items = items.as_array().ok_or("Invalid custom emotes")?;
        if items.len() > 50 {
            return Err("Too many custom emotes".into());
        }
        for item in items {
            let id = item["id"].as_str().ok_or("Missing custom emote ID")?;
            if !id.starts_with("custom-emote-")
                || id.len() <= 13
                || id.len() > 53
                || !id[13..].bytes().all(|b| b.is_ascii_hexdigit() || b == b'-')
                || !custom_emotes.insert(id.to_string())
            {
                return Err("Invalid custom emote ID".into());
            }
            let name = item["name"].as_str().ok_or("Missing custom emote name")?;
            if name.trim().is_empty() || name.chars().count() > 48 {
                return Err("Invalid custom emote name".into());
            }
            let duration = item["duration"]
                .as_f64()
                .filter(|v| (1.0..=10.0).contains(v))
                .ok_or("Invalid custom emote duration")?;
            let frames = item["keyframes"]
                .as_array()
                .filter(|a| (2..=24).contains(&a.len()))
                .ok_or("Invalid custom emote timeline")?;
            let mut previous = -1.0f64;
            for frame in frames {
                let time = frame["time"]
                    .as_f64()
                    .filter(|t| (0.0..=1.0).contains(t) && *t > previous)
                    .ok_or("Invalid custom emote keyframe time")?;
                previous = time;
                let pose = frame["pose"]
                    .as_array()
                    .filter(|a| a.len() == 19)
                    .ok_or("Invalid custom emote pose")?;
                if pose.iter().any(|v| {
                    v.as_f64()
                        .map(|n| !n.is_finite() || !(-3.0..=3.0).contains(&n))
                        .unwrap_or(true)
                }) {
                    return Err("Invalid custom emote pose".into());
                }
            }
            if frames[0]["time"] != 0.0
                || frames.last().unwrap()["time"] != 1.0
                || !duration.is_finite()
            {
                return Err("Custom emote must cover its full duration".into());
            }
            if item.get("published").is_some_and(|v| !v.is_boolean()) {
                return Err("Invalid emote visibility".into());
            }
            if item.get("communityOwned").is_some_and(|v| !v.is_boolean()) {
                return Err("Invalid emote ownership".into());
            }
        }
    }
    let mut custom_models = std::collections::HashSet::new();
    if let Some(items) = s.get("customCosmetics") {
        let items = items.as_array().ok_or("Invalid custom cosmetics")?;
        if items.len() > 50 {
            return Err("Too many custom cosmetics".into());
        }
        for item in items {
            let id = item["id"].as_str().ok_or("Missing custom cosmetic ID")?;
            if !id.starts_with("custom-")
                || id.starts_with("custom-emote-")
                || id.len() <= 7
                || id.len() > 47
                || !id[7..].bytes().all(|b| b.is_ascii_hexdigit() || b == b'-')
                || !custom_models.insert(id.to_string())
            {
                return Err("Invalid custom cosmetic ID".into());
            }
            let name = item["name"]
                .as_str()
                .ok_or("Missing custom cosmetic name")?;
            if name.trim().is_empty() || name.chars().count() > 60 {
                return Err("Invalid custom cosmetic name".into());
            }
            let slot = item["slot"]
                .as_str()
                .filter(|slot| {
                    matches!(
                        *slot,
                        "wings" | "head" | "face" | "pet" | "back" | "aura" | "overlay" | "feet"
                    )
                })
                .ok_or("Invalid custom cosmetic slot")?;
            let model = &item["model"];
            if model["id"] != id || model["slot"] != slot || model["schemaVersion"] != 2 {
                return Err("Custom model metadata mismatch".into());
            }
            let primitives = item["primitives"]
                .as_array()
                .filter(|a| (1..=24).contains(&a.len()))
                .ok_or("Invalid custom primitive list")?;
            for primitive in primitives {
                if !matches!(
                    primitive["type"].as_str(),
                    Some("cube" | "sphere" | "plane" | "pixel")
                ) || !matches!(
                    primitive["joint"].as_str(),
                    Some("head" | "body" | "leftArm" | "rightArm" | "leftLeg" | "rightLeg")
                ) {
                    return Err("Invalid custom primitive".into());
                }
                for (key, bound) in [("position", 32.0), ("size", 32.0), ("rotation", 180.0)] {
                    let values = primitive[key]
                        .as_array()
                        .filter(|a| a.len() == 3)
                        .ok_or("Invalid primitive geometry")?;
                    if values.iter().any(|v| {
                        v.as_f64()
                            .map(|n| !n.is_finite() || n.abs() > bound)
                            .unwrap_or(true)
                    }) {
                        return Err("Invalid primitive geometry".into());
                    }
                }
                if primitive["size"]
                    .as_array()
                    .unwrap()
                    .iter()
                    .any(|v| v.as_f64().unwrap_or(0.0) < 0.2)
                {
                    return Err("Invalid primitive size".into());
                }
                let color = primitive["color"]
                    .as_str()
                    .ok_or("Invalid primitive color")?;
                if color.len() != 7
                    || !color.starts_with('#')
                    || !color[1..].bytes().all(|b| b.is_ascii_hexdigit())
                {
                    return Err("Invalid primitive color".into());
                }
                if !primitive["animated"].is_boolean() {
                    return Err("Invalid primitive animation toggle".into());
                }
                let valid_color = |v: &Value| v.as_str().is_some_and(|s| s.len()==7 && s.starts_with('#') && s[1..].bytes().all(|b|b.is_ascii_hexdigit()));
                if let Some(gradient)=primitive.get("gradient") {
                    if !valid_color(&gradient["color"]) || !matches!(gradient["axis"].as_str(),Some("x"|"y"|"z")) {return Err("Invalid primitive gradient".into());}
                }
                if let Some(paint)=primitive.get("paint") {
                    if !paint.as_array().is_some_and(|pixels|pixels.len()==64 && pixels.iter().all(valid_color)) {return Err("Invalid primitive texture".into());}
                }
                if primitive["animated"].as_bool().unwrap_or(false) {
                    let animation = &primitive["animation"];
                    if let Some(duration)=animation.get("duration") {if !duration.as_f64().is_some_and(|n|(1.0..=10.0).contains(&n)){return Err("Invalid animation duration".into());}}
                    if let Some(frames)=animation.get("keyframes") {
                        let frames=frames.as_array().filter(|frames|(2..=24).contains(&frames.len())).ok_or("Invalid part keyframes")?;
                        let mut previous=-1.0;
                        for frame in frames {let time=frame["time"].as_f64().ok_or("Missing keyframe time")?;let angle=frame["angle"].as_f64().ok_or("Missing keyframe angle")?;if !(0.0..=1.0).contains(&time)||time<=previous||!(-3.0..=3.0).contains(&angle){return Err("Invalid part keyframe".into());}previous=time;}
                        if frames[0]["time"].as_f64()!=Some(0.0)||frames.last().unwrap()["time"].as_f64()!=Some(1.0){return Err("Incomplete part timeline".into());}
                    }
                    if !matches!(animation["axis"].as_str(), Some("x" | "y" | "z"))
                        || animation["amplitude"]
                            .as_f64()
                            .filter(|v| (0.0..=1.2).contains(v))
                            .is_none()
                        || animation["speed"]
                            .as_f64()
                            .filter(|v| (0.1..=8.0).contains(v))
                            .is_none()
                        || animation["phase"]
                            .as_f64()
                            .filter(|v| (-6.3..=6.3).contains(v))
                            .is_none()
                    {
                        return Err("Invalid safe cosmetic motion".into());
                    }
                }
            }
            let parts = model["parts"]
                .as_array()
                .filter(|a| (1..=24).contains(&a.len()))
                .ok_or("Invalid custom model parts")?;
            let mut vertices = 0usize;
            for part in parts {
                let positions = part["positions"]
                    .as_array()
                    .filter(|a| a.len() >= 12 && a.len() <= 6000 && a.len() % 3 == 0)
                    .ok_or("Invalid model vertices")?;
                vertices += positions.len() / 3;
                let normals = part["normals"].as_array().ok_or("Invalid model normals")?;
                let colors = part["colors"].as_array().ok_or("Invalid model colors")?;
                let quads = part["quads"]
                    .as_array()
                    .filter(|a| a.len() >= 4 && a.len() <= 2400 && a.len() % 4 == 0)
                    .ok_or("Invalid model faces")?;
                if normals.len() != positions.len()
                    || colors.len() != positions.len()
                    || vertices > 12000
                    || !matches!(
                        part["joint"].as_str(),
                        Some("head" | "body" | "leftArm" | "rightArm" | "leftLeg" | "rightLeg")
                    )
                {
                    return Err("Invalid model layout".into());
                }
                if positions.iter().any(|v| {
                    v.as_f64()
                        .map(|n| !n.is_finite() || n.abs() > 128.0)
                        .unwrap_or(true)
                }) || normals.iter().any(|v| {
                    v.as_f64()
                        .map(|n| !n.is_finite() || n.abs() > 1.01)
                        .unwrap_or(true)
                }) || colors.iter().any(|v| {
                    v.as_f64()
                        .map(|n| !n.is_finite() || !(0.0..=1.0).contains(&n))
                        .unwrap_or(true)
                }) {
                    return Err("Invalid model values".into());
                }
                if quads.iter().any(|i| {
                    i.as_u64()
                        .map(|n| n as usize >= positions.len() / 3)
                        .unwrap_or(true)
                }) {
                    return Err("Invalid model index".into());
                }
                let material = &part["material"];
                if material["color"]
                    .as_str()
                    .map(|c| {
                        c.len() != 7
                            || !c.starts_with('#')
                            || !c[1..].bytes().all(|b| b.is_ascii_hexdigit())
                    })
                    .unwrap_or(true)
                    || ["metalness", "roughness", "opacity"].iter().any(|key| {
                        material[*key]
                            .as_f64()
                            .filter(|v| (0.0..=1.0).contains(v))
                            .is_none()
                    })
                {
                    return Err("Invalid model material".into());
                }
            }
            if item.get("published").is_some_and(|v| !v.is_boolean()) {
                return Err("Invalid cosmetic visibility".into());
            }
            if item.get("communityOwned").is_some_and(|v| !v.is_boolean()) {
                return Err("Invalid cosmetic ownership".into());
            }
        }
    }
    if let Some(wheel) = s.get("emoteWheel") {
        let a = wheel.as_array().ok_or("Invalid emote wheel")?;
        if a.len() != 8
            || a.iter().any(|v| {
                !matches!(
                    v.as_str(),
                    Some(
                        "wave"
                            | "sit"
                            | "dance"
                            | "point"
                            | "clap"
                            | "salute"
                            | "celebrate"
                            | "bow"
                    )
                ) && !v
                    .as_str()
                    .map(|id| custom_emotes.contains(id))
                    .unwrap_or(false)
            })
        {
            return Err("Invalid emote wheel".into());
        }
    }
    for (selected, items) in [("selectedSkin", "skins"), ("selectedCosmetic", "cosmetics")] {
        let id = s[selected].as_str().ok_or("Missing selected preset")?;
        if !id.is_empty() && !s[items].as_array().unwrap().iter().any(|v| v["id"] == id) {
            return Err("Unknown selected preset".into());
        }
    }
    for key in ["profiles", "themes"] {
        let a = s[key].as_array().ok_or(format!("Missing {key}"))?;
        if a.is_empty() || a.len() > 100 {
            return Err(format!("Invalid {key} count"));
        }
        let mut ids = std::collections::HashSet::new();
        for value in a {
            let id = value["id"].as_str().ok_or("Missing ID")?;
            safe_id(id)?;
            if !ids.insert(id) {
                return Err("Duplicate IDs".into());
            }
        }
    }
    for p in s["profiles"].as_array().unwrap() {
        let p: Profile = serde_json::from_value(p.clone()).context("Invalid profile")?;
        crate::game_options::validate_extra(&p.game_options)?;
        if let Some(settings)=&p.engine_settings {crate::performance::validate(settings)?;}
        if let Some(mods) = &p.starter_mods {
            if mods.extras.len()>6 || mods.shaders.len()>2
                || mods.extras.iter().any(|x| !["xaeros-minimap","xaeros-world-map","appleskin","3dskinlayers","voicechat","mousetweaks"].contains(&x.as_str()))
                || mods.shaders.iter().any(|x| !["complementary-reimagined","bsl-shaders"].contains(&x.as_str())) { return Err("Invalid starter selection".into()); }
        }
        for (name, values) in &p.preset_values {
            crate::game_options::validate_extra(&values.game_options)?;
            if let Some(settings)=&values.engine_settings {crate::performance::validate(settings)?;}
            if !matches!(name.as_str(), "balanced" | "performance" | "extreme" | "cinematic")
                || !(1024..=32768).contains(&values.memory)
                || !(2..=32).contains(&values.render_distance)
                || !(2..=32).contains(&values.simulation_distance)
                || (values.max_fps != 0 && !(10..=250).contains(&values.max_fps))
                || !matches!(values.graphics.as_str(), "fast" | "fancy" | "fabulous" | "custom")
                || values.gui_scale > 8 || !values.fov.is_finite() || !(30.0..=110.0).contains(&values.fov)
            { return Err("Invalid saved game preset".into()); }
        }
        if !(1024..=32768).contains(&p.memory)
            || !matches!(
                p.loader.as_str(),
                "fabric" | "vanilla" | "quilt" | "forge" | "neoforge"
            )
            || !(2..=32).contains(&p.render_distance)
            || !(2..=32).contains(&p.simulation_distance)
            || p.max_fps > 250
            || p.gui_scale > 8 || !p.fov.is_finite() || !(30.0..=110.0).contains(&p.fov)
            || (p.max_fps > 0 && p.max_fps < 10)
            || !matches!(p.graphics.as_str(), "fast" | "fancy" | "fabulous" | "custom")
            || p.name.trim().is_empty()
            || p.name.chars().count() > 60
            || p.minecraft.is_empty()
            || !p
                .minecraft
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || b"._-".contains(&b))
            || p.minecraft.len() > 64
            || p.java.len() > 1000
            || p.loader_version.len() > 64
            || !matches!(
                p.preset.as_str(),
                "balanced" | "performance" | "extreme" | "cinematic" | "custom"
            )
            || !p
                .loader_version
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || b"._+-".contains(&b))
        {
            return Err("Invalid profile values".into());
        }
    }
    for t in s["themes"].as_array().unwrap() {
        validate_effects(t, true)?;
        if let Some(p) = t.get("renderProfile") {
            if !matches!(
                p.as_str(),
                Some("ultra-performance" | "performance" | "high" | "ultra")
            ) {
                return Err("Unknown design quality".into());
            }
        }
        if let Some(profiles) = t.get("effectProfiles") {
            for (id, p) in profiles.as_object().ok_or("Invalid design profiles")? {
                if !matches!(
                    id.as_str(),
                    "ultra-performance" | "performance" | "high" | "ultra"
                ) {
                    return Err("Unknown design profile".into());
                }
                validate_effects(p, false)?;
            }
        }
        if t["schemaVersion"] != 1 {
            return Err("Unsupported theme version".into());
        }
        let name = t["name"].as_str().ok_or("Missing theme name")?;
        if name.is_empty() || name.chars().count() > 60 {
            return Err("Invalid theme name".into());
        }
        for (key, min, max) in [
            ("scale", 75.0, 150.0),
            ("sidebar", 160.0, 260.0),
            ("spacing", 8.0, 32.0),
        ] {
            let n = t[key].as_f64().ok_or("Invalid theme layout value")?;
            if !(min..=max).contains(&n) {
                return Err("Theme layout value out of bounds".into());
            }
        }
        for key in ["compact", "hover", "reducedMotion"] {
            if !t[key].is_boolean() {
                return Err("Invalid theme motion/layout switch".into());
            }
        }
        if !matches!(
            t["backgroundMode"].as_str(),
            Some("landscape" | "gradient" | "solid" | "image" | "video" | "clouds")
        ) {
            return Err("Unknown background mode".into());
        }
        let mode = t["backgroundMode"].as_str().unwrap();
        let video = t.get("backgroundVideo").and_then(Value::as_str).unwrap_or("");
        if !video.is_empty() {
            validate_background_video(video)?;
        }
        if mode == "video" && video.is_empty() {
            return Err("Video background is selected without a local video".into());
        }
        for (key, min, max) in [
            ("backgroundBlur", 0.0, 24.0),
            ("backgroundPositionX", 0.0, 100.0),
            ("backgroundPositionY", 0.0, 100.0),
            ("backgroundZoom", 100.0, 160.0),
        ] {
            if let Some(value) = t.get(key) {
                let value = value.as_f64().ok_or("Invalid background framing value")?;
                if !(min..=max).contains(&value) {
                    return Err("Background framing value out of range".into());
                }
            }
        }
        for k in [
            "transparency",
            "blur",
            "wobble",
            "speed",
            "saturation",
            "glow",
            "radius",
            "brightness",
            "stiffness",
            "damping",
            "reflection",
            "shadow",
        ] {
            let v = t[k].as_f64().ok_or(format!("Invalid theme {k}"))?;
            if !(0.0..=100.0).contains(&v) {
                return Err(format!("Theme {k} out of range"));
            }
        }
        for k in ["accent", "secondary", "tint", "background", "text", "muted"] {
            let c = t[k].as_str().ok_or("Missing color")?;
            if c.len() != 7 || !c.starts_with('#') || !c[1..].bytes().all(|b| b.is_ascii_hexdigit())
            {
                return Err("Invalid theme color".into());
            }
        }
        let image = t["backgroundImage"]
            .as_str()
            .ok_or("Missing background image")?;
        if !image.is_empty()
            && (![
                "data:image/png;base64,",
                "data:image/jpeg;base64,",
                "data:image/webp;base64,",
            ]
            .iter()
            .any(|p| image.starts_with(p))
                || image.len() > 8_000_000)
        {
            return Err("Invalid background image".into());
        }
    }
    let active = s["activeProfile"]
        .as_str()
        .ok_or("Missing active profile")?;
    if !s["profiles"]
        .as_array()
        .unwrap()
        .iter()
        .any(|p| p["id"] == active)
    {
        return Err("Unknown active profile".into());
    }
    for key in ["themeId", "gameThemeId"] {
        if !s["themes"]
            .as_array()
            .unwrap()
            .iter()
            .any(|t| t["id"] == s[key])
        {
            return Err("Unknown theme".into());
        }
    }
    let retention = s["retention"].as_u64().ok_or("Missing retention")?;
    if !(2..=100).contains(&retention) {
        return Err("Invalid retention".into());
    }
    Ok(())
}
#[cfg(test)]
mod tests {
    #[test]
    fn legacy_null_starter_recipe_round_trip_omits_absent_recipe_and_keeps_opt_outs() {
        let mut state: Value = serde_json::from_str(include_str!(
            "../../../../packages/shared-config/defaults.json"
        )).unwrap();
        state["profiles"][0]["starterMods"] = serde_json::Value::Null;
        validate_state(&state).unwrap();
        let legacy: Profile = serde_json::from_value(state["profiles"][0].clone()).unwrap();
        assert!(legacy.starter_mods.is_none());
        let normalized = serde_json::to_value(&legacy).unwrap();
        assert!(normalized.get("starterMods").is_none());
        let mut expected = state["profiles"][0].clone();
        expected.as_object_mut().unwrap().remove("starterMods");
        assert_eq!(normalized, serde_json::to_value(serde_json::from_value::<Profile>(expected).unwrap()).unwrap());
        let choices = serde_json::json!({"sodium":false,"iris":false,"modmenu":true,"zoomify":false,"more":true,"extras":["appleskin","voicechat"],"shaders":[]});
        state["profiles"][0]["starterMods"] = choices.clone();
        validate_state(&state).unwrap();
        let selected: Profile = serde_json::from_value(state["profiles"][0].clone()).unwrap();
        assert_eq!(serde_json::to_value(selected).unwrap()["starterMods"], choices);
    }
    #[test]
    fn atomic_replacement_supports_long_paths_and_replaces_existing_content() {
        let root = tempfile::tempdir().unwrap();
        let parent = root.path().join("nested-".repeat(18)).join("profile-".repeat(16));
        let target = parent.join("fabric-api-test.jar");
        assert!(target.to_string_lossy().len() > 260);
        super::atomic(&target, b"first").unwrap();
        super::atomic(&target, b"replacement").unwrap();
        assert_eq!(std::fs::read(&target).unwrap(), b"replacement");
        assert_eq!(std::fs::read_dir(&parent).unwrap().count(), 1);
    }
    #[test]
    fn atomic_failure_keeps_the_real_error_and_existing_destination() {
        let root = tempfile::tempdir().unwrap();
        let target = root.path().join("existing-directory");
        std::fs::create_dir(&target).unwrap();
        let error = super::atomic(&target, b"should not replace a directory").unwrap_err();
        assert!(!error.contains("os error 0"), "{error}");
        assert!(target.is_dir());
        assert_eq!(std::fs::read_dir(root.path()).unwrap().count(), 1);
    }
    use super::*;
    #[test]
    fn profile_leases_block_only_the_same_profile_across_stores() {
        let directory = tempfile::tempdir().unwrap();
        let first = Store::new(directory.path().to_path_buf()).unwrap();
        let second = Store::new(directory.path().to_path_buf()).unwrap();
        let main = first.instance_lease("main").unwrap();
        assert!(second.instance_lease("main").is_err());
        let other = second.instance_lease("other").unwrap();
        drop(main);
        assert!(second.instance_lease("main").is_ok());
        drop(other);
    }
    #[test]
    fn blocks_traversal() {
        let t = tempfile::tempdir().unwrap();
        for p in [
            "../world",
            "C:/Windows",
            "a\\b",
            "/absolute",
            "a/../b",
            "a//b",
        ] {
            assert!(relative(t.path(), p).is_err(), "{p}");
        }
        assert!(relative(t.path(), "mods/safe.jar").is_ok());
    }
    #[test]
    fn validates_all_settings_before_saving() {
        let good: Value = serde_json::from_str(include_str!(
            "../../../../packages/shared-config/defaults.json"
        ))
        .unwrap();
        validate_state(&good).unwrap();
        for (key, bad) in [
            ("skins", serde_json::json!(null)),
            ("language", serde_json::json!("script")),
            ("idleSkin", serde_json::json!("true")),
        ] {
            let mut s = good.clone();
            s[key] = bad;
            assert!(validate_state(&s).is_err());
        }
        let mut s = good.clone();
        s["themes"][0]["scale"] = serde_json::json!(999);
        assert!(validate_state(&s).is_err());
    }
    #[test]
    fn saved_named_game_values_round_trip_through_native_profiles() {
        let mut state:Value=serde_json::from_str(include_str!("../../../../packages/shared-config/defaults.json")).unwrap();
        state["profiles"][0]["presetValues"]=serde_json::json!({"cinematic":{"memory":8192,"renderDistance":32,"simulationDistance":12,"maxFps":165,"graphics":"fabulous","vsync":false,"shaderEnabled":false}});
        validate_state(&state).unwrap();
        let profile:Profile=serde_json::from_value(state["profiles"][0].clone()).unwrap();
        assert_eq!(serde_json::to_value(profile).unwrap()["presetValues"]["cinematic"]["maxFps"],165);
        state["profiles"][0]["presetValues"]["cinematic"]["maxFps"]=serde_json::json!(9999);
        assert!(validate_state(&state).is_err());
    }
    #[test]
    fn accepts_legacy_themes_and_checks_video_backgrounds() {
        let good: Value = serde_json::from_str(include_str!(
            "../../../../packages/shared-config/defaults.json"
        ))
        .unwrap();
        let mut legacy = good.clone();
        for theme in legacy["themes"].as_array_mut().unwrap() {
            for key in [
                "backgroundVideo",
                "backgroundBlur",
                "backgroundPositionX",
                "backgroundPositionY",
                "backgroundZoom",
            ] {
                theme.as_object_mut().unwrap().remove(key);
            }
        }
        validate_state(&legacy).unwrap();
        let mut video = good.clone();
        video["themes"][0]["backgroundMode"] = serde_json::json!("video");
        video["themes"][0]["backgroundVideo"] =
            serde_json::json!("data:video/mp4;base64,AAAABGZ0eXBpc29t");
        validate_state(&video).unwrap();
        video["themes"][0]["backgroundVideo"] =
            serde_json::json!("data:video/mp4;base64,PHNjcmlwdD4=");
        assert!(validate_state(&video).is_err());
        video["themes"][0]["backgroundVideo"] = serde_json::json!("");
        assert!(validate_state(&video).is_err());
    }
    #[test]
    fn validates_character_view_and_keeps_legacy_states_readable() {
        let good: Value = serde_json::from_str(include_str!(
            "../../../../packages/shared-config/defaults.json"
        )).unwrap();
        validate_state(&good).unwrap();
        let mut legacy = good.clone();
        legacy.as_object_mut().unwrap().remove("characterView");
        validate_state(&legacy).unwrap();
        let mut scene=good.clone();
        scene["characterView"]=serde_json::json!({"mode":"scene","scene":"night-garden","lighting":"moonlight"});
        validate_state(&scene).unwrap();
        for invalid in [serde_json::json!(null),serde_json::json!({}),serde_json::json!({"mode":"scene","scene":"remote.glb","lighting":"studio"})] {
            let mut bad=good.clone(); bad["characterView"]=invalid;
            assert!(validate_state(&bad).is_err());
        }
    }
}
