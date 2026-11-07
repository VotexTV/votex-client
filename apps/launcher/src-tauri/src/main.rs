#![cfg_attr(target_os = "windows", windows_subsystem = "windows")]
mod auth;
mod catalog;
mod community;
mod core_install;
mod curseforge;
mod diagnostics;
mod performance;
mod game_options;
mod horizon;
mod loaders;
mod minecraft;
mod modpacks;
mod modrinth;
mod native_client;
mod network;
mod pending_addons;
mod play_history;
mod prepare;
mod recovery;
mod runtime;
mod storage;
mod studio;
mod starter;
mod transfers;
use serde_json::{json, Value};
use std::{ffi::OsString, path::PathBuf, sync::Arc};
use storage::{Context, Result, Store};
use tauri::{Manager, State};
type Db<'a> = State<'a, Arc<Store>>;
pub(crate) struct Busy(Arc<Store>);
impl Drop for Busy {
    fn drop(&mut self) {
        if let Ok(mut b) = self.0.busy.lock() {
            *b = false;
        }
    }
}
fn begin(s: &Arc<Store>) -> Result<Busy> {
    let mut b = s.busy.lock().map_err(|_| "Operation lock unavailable")?;
    if *b {
        return Err("Minecraft or another operation is active".into());
    }
    *b = true;
    Ok(Busy(s.clone()))
}
#[tauri::command]
fn performance_status(store: Db, profile_id: String) -> Result<Value> { performance::inspect(&store, &profile_id) }
#[tauri::command]
fn performance_save(store: Db, app: tauri::AppHandle, profile_id: String, value: Value) -> Result<()> { use tauri::Emitter;performance::save(&store, &profile_id, value)?;let _=app.emit("votex:game-options",json!({"profile":profile_id}));Ok(()) }
#[tauri::command]
fn load_state(store: Db) -> Result<Option<Value>> {
    let p = store.root.join("config/launcher.json");
    if !p.exists() {
        return Ok(None);
    }
    let ids: Vec<_> = store.state()?["profiles"].as_array().ok_or("Missing profiles")?.iter().filter_map(|p|p["id"].as_str().map(str::to_owned)).collect();
    for id in ids { game_options::sync(&store,&id)?; }
    let s = store.state()?;
    storage::validate_state(&s)?;
    Ok(Some(s))
}
#[tauri::command]
fn save_state(store: Db, value: Value) -> Result<()> {
    storage::validate_state(&value)?;
    let _g = store
        .lock
        .lock()
        .map_err(|_| "Configuration lock unavailable")?;
    if let Ok(old) = store.state() {
        let old_profiles = old["profiles"].as_array().ok_or("Missing profiles")?;
        let new_profiles = value["profiles"].as_array().ok_or("Missing profiles")?;
        for old_value in old_profiles {
            let old_profile: storage::Profile = serde_json::from_value(old_value.clone())
                .map_err(|error| format!("Invalid existing profile: {error}"))?;
            if let Some(new_value) = new_profiles.iter().find(|p| p["id"] == old_profile.id) {
                let new_profile: storage::Profile = serde_json::from_value(new_value.clone())
                    .map_err(|error| format!("Invalid updated profile: {error}"))?;
                if old_profile.engine != new_profile.engine {
                    return Err("Die Spiel-Engine eines Profils kann nicht nachträglich gewechselt werden. Erstelle dafür ein getrenntes Profil, damit Mods und Spielstände sauber getrennt bleiben.".into());
                }
            }
        }
    }
    if *store
        .busy
        .lock()
        .map_err(|_| "Operation lock unavailable")?
    {
        if let Ok(old) = store.state() {
            if old["profiles"] != value["profiles"] {
                return Err("Profile changes require Minecraft and downloads to be stopped".into());
            }
        }
    }
    let mut removed_profile_leases = Vec::new();
    if let Ok(old) = store.state() {
        for profile in old["profiles"].as_array().into_iter().flatten() {
            if !value["profiles"].as_array().unwrap().iter().any(|p| p["id"] == profile["id"]) {
                if let Some(id) = profile["id"].as_str() {
                    removed_profile_leases.push(store.instance_lease(id)?);
                }
            }
        }
        if old.get("interfaceRevision").and_then(Value::as_u64) != Some(2)
            && value["interfaceRevision"] == 2
        {
            let backup = store.root.join("config").join(format!(
                "launcher-before-0.2-{}.json",
                chrono::Utc::now().format("%Y%m%d-%H%M%S")
            ));
            storage::atomic(
                &backup,
                &serde_json::to_vec_pretty(&old)
                    .context("Back up previous launcher configuration")?,
            )?;
        }
    }
    storage::atomic(
        &store.root.join("config/launcher.json"),
        &serde_json::to_vec_pretty(&value).context("Serialize settings")?,
    )?;
    export_presets(&store, &value)
}
pub(crate) fn export_presets(store: &Store, value: &Value) -> Result<()> {
    // Single-writer split: launcher owns theme.json; the mod owns modules.json.
    for p in value["profiles"].as_array().unwrap() {
        let id = p["id"].as_str().unwrap();
        let theme_id = if value["syncDesign"] == true {
            &value["themeId"]
        } else {
            &value["gameThemeId"]
        };
        let theme = value["themes"]
            .as_array()
            .unwrap()
            .iter()
            .find(|t| &t["id"] == theme_id)
            .ok_or("Unknown game theme")?;
        storage::atomic(
            &store.instance(id)?.join("config/votex/theme.json"),
            &serde_json::to_vec(&game_theme(theme)).context("Serialize shared theme")?,
        )?;
        let skins = value["skins"]
            .as_array()
            .ok_or("Missing skin presets")?
            .iter()
            .map(|s| json!({"id":s["id"],"name":s["name"],"model":s["model"]}))
            .collect::<Vec<_>>();
        let assets = store.instance(id)?.join("config/votex/assets");
        for skin in value["skins"].as_array().unwrap() {
            let name = skin["id"].as_str().ok_or("Missing skin ID")?;
            storage::atomic(
                &storage::relative(&assets, &format!("skin-{name}.png"))?,
                &storage::png_data(skin["data"].as_str().ok_or("Missing skin image")?, false)?,
            )?;
        }
        for look in value["cosmetics"].as_array().unwrap() {
            if let Some(data) = look["capeData"].as_str().filter(|d| !d.is_empty()) {
                let name = look["id"].as_str().ok_or("Missing cosmetic ID")?;
                storage::atomic(
                    &storage::relative(&assets, &format!("cape-{name}.png"))?,
                    &storage::png_data(data, true)?,
                )?;
            }
        }
        let looks: Vec<Value> = value["cosmetics"]
            .as_array()
            .unwrap()
            .iter()
            .map(|look| {
                let mut l = look.clone();
                l["customCape"] = json!(!look["capeData"].as_str().unwrap_or("").is_empty());
                l.as_object_mut().unwrap().remove("capeData");
                l
            })
            .collect();
        let custom_models = value["customCosmetics"]
            .as_array()
            .into_iter()
            .flatten()
            .map(|item| item["model"].clone())
            .collect::<Vec<_>>();
        let custom_emotes = value["customEmotes"]
            .as_array()
            .cloned()
            .unwrap_or_default();
        storage::atomic(&store.instance(id)?.join("config/votex/presets.json"),&serde_json::to_vec(&json!({"schemaVersion":1,"skins":skins,"selectedSkin":value["selectedSkin"],"cosmetics":looks,"selectedCosmetic":value["selectedCosmetic"],"customModels":custom_models,"customEmotes":custom_emotes,"emoteWheel":value["emoteWheel"]})).context("Serialize preset metadata")?)?;
    }
    Ok(())
}
fn game_theme(theme: &Value) -> Value {
    let mut result = theme.clone();
    if let Some(fields) = result.as_object_mut() {
        fields.remove("backgroundImage");
        fields.remove("backgroundVideo");
    }
    result
}
#[tauri::command]
async fn plan_mod_transfer(
    store: Db<'_>,
    source: String,
    target: storage::Profile,
    create: bool,
) -> Result<Value> {
    let _g = begin(&store)?;
    transfers::plan(&store, &source, &target, create).await
}
#[tauri::command]
async fn commit_mod_transfer(store: Db<'_>, token: String) -> Result<Value> {
    let _g = begin(&store)?;
    transfers::commit(&store, &token).await
}
#[tauri::command]
fn profile_mod_counts(store: Db) -> Result<Value> {
    let mut counts = serde_json::Map::new();
    for p in store.state()?["profiles"]
        .as_array()
        .ok_or("Missing profiles")?
    {
        let id = p["id"].as_str().ok_or("Missing ID")?;
        counts.insert(
            id.into(),
            json!(modrinth::installed(&store, id)?
                .as_array()
                .map(|a| a.len())
                .unwrap_or(0)),
        );
    }
    Ok(Value::Object(counts))
}
#[tauri::command]
fn profile_covers(store: Db) -> Result<Value> {
    use base64::Engine;
    let mut covers = serde_json::Map::new();
    for profile in store.state()?["profiles"]
        .as_array()
        .ok_or("Missing profiles")?
    {
        let id = profile["id"].as_str().ok_or("Missing ID")?;
        let path = storage::relative(
            &store.instance(id)?,
            "config/votex/profile-cover.png",
        )?;
        let Ok(bytes) = std::fs::read(path) else { continue };
        if bytes.len() <= 2_097_152 && bytes.starts_with(b"\x89PNG\r\n\x1a\n") {
            covers.insert(
                id.into(),
                json!(format!(
                    "data:image/png;base64,{}",
                    base64::engine::general_purpose::STANDARD.encode(bytes)
                )),
            );
        }
    }
    Ok(Value::Object(covers))
}
#[tauri::command]
async fn list_versions(store: Db<'_>) -> Result<Value> {
    minecraft::versions(&store).await
}
#[tauri::command]
async fn loader_choices(minecraft: String) -> Result<Value> {
    loaders::choices(&minecraft).await
}
#[tauri::command]
async fn search_catalog(
    store: Db<'_>,
    id: String,
    provider: String,
    kind: String,
    query: String,
    offset: u32,
    sort: String,
    category: String,
) -> Result<Value> {
    catalog::search(
        &store, &id, &provider, &kind, &query, offset, &sort, &category,
    )
    .await
}
#[tauri::command]
fn curseforge_status() -> bool {
    curseforge::configured()
}
#[tauri::command]
fn save_curseforge_key(key: String) -> Result<()> {
    curseforge::save(&key)
}
#[tauri::command]
fn open_catalog(provider: String, kind: String, slug: String) -> Result<()> {
    if provider == "curseforge-console" {
        return open::that("https://console.curseforge.com/").context("Open CurseForge console");
    }
    storage::safe_id(&slug)?;
    if !matches!(kind.as_str(), "mod" | "modpack" | "shader" | "resourcepack") {
        return Err("Unknown content type".into());
    }
    let url = if provider == "modrinth" {
        format!("https://modrinth.com/{kind}/{slug}")
    } else if provider == "curseforge" {
        format!(
            "https://www.curseforge.com/minecraft/{}/{slug}",
            match kind.as_str() {
                "mod" => "mc-mods",
                "modpack" => "modpacks",
                "shader" => "shaders",
                _ => "texture-packs",
            }
        )
    } else {
        return Err("Unknown catalog provider".into());
    };
    open::that(url).context("Open content page")
}
#[tauri::command]
async fn install_content(
    store: Db<'_>,
    app: tauri::AppHandle,
    id: String,
    provider: String,
    kind: String,
    project: String,
) -> Result<Value> {
    let _g = begin(&store)?;
    let result = catalog::install(&store, &id, &provider, &kind, &project, &app).await;
    match &result {
        Ok(_) => minecraft::event(&app, "idle", "Inhalte installiert", 0, 0),
        Err(e) => minecraft::event(&app, "error", e, 0, 0),
    }
    result
}
#[tauri::command]
async fn import_modpack(
    store: Db<'_>,
    app: tauri::AppHandle,
    id: String,
    data: String,
) -> Result<Value> {
    use base64::Engine;
    if data.len() > 90_000_000 {
        return Err("Modpack überschreitet 64 MiB".into());
    }
    let _g = begin(&store)?;
    let bytes = base64::engine::general_purpose::STANDARD
        .decode(data.split_once(',').map(|(_, s)| s).unwrap_or(&data))
        .map_err(|_| "Invalid modpack encoding")?;
    let result = modpacks::import(&store, &id, bytes, &app).await;
    match &result {
        Ok(_) => minecraft::event(&app, "idle", "Modpack importiert", 0, 0),
        Err(e) => minecraft::event(&app, "error", e, 0, 0),
    }
    result
}
#[tauri::command]
fn profile_downloads(store: Db, id:String)->Result<Value>{starter::status(&store,&id)}
#[tauri::command]
async fn prepare_profile(store: Db<'_>, app: tauri::AppHandle, id: String) -> Result<()> {
    let _g = begin(&store)?;
    let _lease = store.instance_lease(&id)?;
    let r = prepare::profile(&store, &id, &app).await;
    match &r {
        Ok(_) => minecraft::event(&app, "idle", "Optionale Pakete vorbereitet", 0, 0),
        Err(e) => minecraft::event(&app, "error", e, 0, 0),
    }
    r
}
#[tauri::command]
async fn check_java(path: String) -> Result<Value> {
    minecraft::java(&path).await
}
#[tauri::command]
fn get_accounts(store: Db) -> Result<Value> {
    auth::accounts(&store)
}
#[tauri::command]
fn select_account(store: Db, id: String) -> Result<()> {
    let _busy = begin(&store)?;
    auth::select(&store, &id)
}
#[tauri::command]
fn remove_account(store: Db, id: String) -> Result<()> {
    let _busy = begin(&store)?;
    auth::remove(&store, &id)
}
#[tauri::command]
async fn start_login(store: Db<'_>, auth: State<'_, auth::Auth>) -> Result<Value> {
    auth::start(&store, &auth).await
}
#[tauri::command]
async fn finish_login(
    store: Db<'_>,
    auth: State<'_, auth::Auth>,
    session_id: String,
) -> Result<auth::Account> {
    auth::finish(&store, &auth, &session_id).await
}
#[tauri::command]
fn cancel_login(auth: State<'_, auth::Auth>, session_id: String) -> Result<bool> {
    auth::cancel(&auth, &session_id)
}
#[tauri::command]
fn open_login(auth: State<'_, auth::Auth>, session_id: String) -> Result<()> {
    auth::open_login(&auth, &session_id)
}
#[tauri::command]
fn open_microsoft_setup() -> Result<()> {
    open::that("https://entra.microsoft.com/").context("Open Microsoft app registration")
}
#[tauri::command]
async fn account_skin(store: Db<'_>) -> Result<Option<String>> {
    use base64::Engine;
    let a = auth::accounts(&store)?;
    let account = a["items"]
        .as_array()
        .and_then(|items| items.iter().find(|i| i["id"] == a["selected"]));
    let Some(url) = account
        .and_then(|a| a["skin"].as_str())
        .filter(|s| !s.is_empty())
    else {
        return Ok(None);
    };
    network::allowed(url)?;
    let bytes = network::client()?
        .get(url)
        .send()
        .await
        .context("Download account skin")?
        .error_for_status()
        .context("Skin service error")?
        .bytes()
        .await
        .context("Read skin")?;
    if bytes.len() > 1_048_576 || !bytes.starts_with(b"\x89PNG\r\n\x1a\n") {
        return Err("Invalid Minecraft skin image".into());
    }
    Ok(Some(format!(
        "data:image/png;base64,{}",
        base64::engine::general_purpose::STANDARD.encode(bytes)
    )))
}
#[tauri::command]
fn run_game(
    store: Db,
    app: tauri::AppHandle,
    id: String,
    action: String,
    auth_mode: String,
    offline_name: String,
    server_address: Option<String>,
    runtime: String,
) -> Result<()> {
    let guard = begin(&store)?;
    let s = store.inner().clone();
    let profile = s.profile(&id)?;
    if !["install", "launch", "safe"].contains(&action.as_str()) {
        return Err("Unknown launch action".into());
    }
    if !matches!(runtime.as_str(), "java" | "pomme" | "horizon") {
        return Err("Unknown Minecraft runtime".into());
    }
    if profile.engine == storage::GameEngine::Horizon {
        if runtime != "horizon" || action != "launch" {
            return Err("Dieses Profil verwendet Horizon (Rust). Starte es über Spielen; Spielinstallation und Safe Mode sind hier nicht verfügbar.".into());
        }
        if profile.minecraft != "26.3" || profile.loader != "vanilla" {
            return Err("Horizon benötigt ein Vanilla-Profil für Minecraft 26.3.".into());
        }
    } else if runtime == "horizon" {
        return Err("Wähle ein Horizon-Profil, um die Rust-Engine zu starten.".into());
    }
    if runtime == "pomme" && action != "launch" {
        return Err("Minecraft installation and Safe Mode are available through the Java runtime. Pomme manages its own native assets.".into());
    }
    if runtime == "pomme" && auth_mode != "microsoft" {
        return Err("The official Pomme release requires a Microsoft sign-in inside the native client; Votex offline identity is not supported there.".into());
    }
    let server_address = server_address.map(|address| play_history::server_address(&address)).transpose()?;
    if server_address.is_some() && action != "launch" { return Err("Direktbeitritt ist nur beim normalen Spielstart möglich.".into()); }
    let offline_name = if action == "install" {
        None
    } else if auth_mode == "offline" {
        Some(offline_name)
    } else if auth_mode == "microsoft" {
        None
    } else {
        return Err("Unknown Minecraft sign-in mode".into());
    };
    let lease = s.instance_lease(&id)?;
    tauri::async_runtime::spawn(async move {
        let _lease = lease;
        let result = if runtime == "horizon" {
            horizon::launch(s.clone(), profile, offline_name, server_address, app.clone(), guard).await
        } else if runtime == "pomme" {
            native_client::launch(s.clone(), profile, server_address, app.clone(), guard).await
        } else if action == "install" {
            let _guard = guard;
            minecraft::install(&s, &profile, &app).await.map(|_| ())
        } else {
            minecraft::launch(s.clone(), profile, app.clone(), action == "safe", offline_name, server_address, guard).await
        };
        if let Err(e) = result {
            if !s.busy.lock().map(|busy| *busy).unwrap_or(true) {
                minecraft::event(&app, "error", &e, 0, 0);
            }
        }
    });
    Ok(())
}
#[tauri::command]
fn native_client_status(store: Db) -> Result<Value> {
    native_client::status(&store)
}
#[tauri::command]
async fn install_native_client(store: Db<'_>, app: tauri::AppHandle) -> Result<Value> {
    let _guard = begin(&store)?;
    native_client::install(&store, &app).await
}
#[tauri::command]
fn horizon_runtime_status(store: Db) -> Result<Value> {
    horizon::status(&store)
}
#[tauri::command]
async fn install_horizon_runtime(store: Db<'_>, app: tauri::AppHandle) -> Result<Value> {
    let _guard = begin(&store)?;
    horizon::install(&store, &app).await
}
#[tauri::command]
fn get_play_history(store: Db) -> Result<Value> { play_history::inspect(&store) }
#[tauri::command]
async fn search_mods(store: Db<'_>, id: String, query: String) -> Result<Value> {
    modrinth::search(&store, &id, &query).await
}
#[tauri::command]
fn installed_mods(store: Db, id: String) -> Result<Value> {
    modrinth::installed(&store, &id)
}
#[tauri::command]
async fn install_mod(
    store: Db<'_>,
    app: tauri::AppHandle,
    id: String,
    project: String,
) -> Result<Value> {
    let _g = begin(&store)?;
    let _lease = store.instance_lease(&id)?;
    let r = modrinth::install(&store, &id, &project, &app).await;
    match &r {
        Ok(_) => minecraft::event(
            &app,
            "idle",
            "Mods installed. Changes apply at next launch.",
            0,
            0,
        ),
        Err(e) => minecraft::event(&app, "error", e, 0, 0),
    }
    r
}
#[tauri::command]
fn toggle_mod(store: Db, id: String, filename: String) -> Result<Value> {
    let _g = begin(&store)?;
    let _lease = store.instance_lease(&id)?;
    modrinth::toggle(&store, &id, &filename)
}
#[tauri::command]
fn remove_mod(store: Db, id: String, filename: String) -> Result<Value> {
    let _g = begin(&store)?;
    let _lease = store.instance_lease(&id)?;
    modrinth::remove(&store, &id, &filename)
}
#[tauri::command]
fn list_snapshots(store: Db, id: String) -> Result<Vec<recovery::Snapshot>> {
    recovery::list(&store, &id)
}
#[tauri::command]
fn create_snapshot(store: Db, id: String) -> Result<recovery::Snapshot> {
    let _g = begin(&store)?;
    recovery::create(&store, &id, "manual")
}
#[tauri::command]
fn preview_restore(store: Db, id: String, snapshot: String) -> Result<Vec<String>> {
    let _g = begin(&store)?;
    recovery::compare(&store, &recovery::get(&store, &id, &snapshot)?)
}
#[tauri::command]
fn restore_snapshot(store: Db, id: String, snapshot: String) -> Result<()> {
    let _g = begin(&store)?;
    let _lease = store.instance_lease(&id)?;
    let _lock = store.lock.lock().map_err(|_| "Configuration lock failed")?;
    recovery::restore(&store, &id, &snapshot).map(|_| ())
}
#[tauri::command]
fn delete_snapshot(store: Db, id: String, snapshot: String) -> Result<()> {
    let _g = begin(&store)?;
    recovery::delete(&store, &id, &snapshot)
}
#[tauri::command]
fn backup_usage(store: Db) -> Result<Value> {
    recovery::usage(&store)
}
#[tauri::command]
fn cleanup_backups(store: Db) -> Result<u64> {
    let _g = begin(&store)?;
    recovery::gc(&store)
}
#[tauri::command]
fn disable_recent(store: Db, id: String) -> Result<Value> {
    let _g = begin(&store)?;
    let _lease = store.instance_lease(&id)?;
    let working =
        recovery::latest_working(&store, &id)?.ok_or("No confirmed working snapshot exists")?;
    let changes = recovery::compare(&store, &working)?;
    recovery::create(&store, &id, "pre-disable-recent")?;
    let dir = store.instance(&id)?.join("mods");
    for name in changes {
        if name.starts_with("mods/") && name.ends_with(".jar") && !working.files.contains_key(&name)
        {
            let filename = name.strip_prefix("mods/").unwrap();
            let src = storage::relative(&dir, filename)?;
            let dst = storage::relative(&dir, &format!("{filename}.disabled"))?;
            if src.exists() {
                if dst.exists() {
                    return Err("A disabled duplicate exists. No files were overwritten.".into());
                }
                std::fs::rename(src, dst).context("Disable recently added mod")?;
            }
        }
    }
    modrinth::installed(&store, &id)
}
#[tauri::command]
fn launch_log(store: Db, id: String) -> Result<String> {
    storage::safe_id(&id)?;
    let p = store.root.join("logs").join(format!("{id}-latest.log"));
    if !p.exists() {
        return Ok("No game launch log exists yet.".into());
    }
    let s = std::fs::read_to_string(p).context("Read launch log")?;
    Ok(s.chars()
        .rev()
        .take(32_000)
        .collect::<String>()
        .chars()
        .rev()
        .collect())
}
#[tauri::command]
fn instance_status(store: Db, id: String) -> Result<Value> {
    let dir = store.instance(&id)?;
    let receipt = dir.join("installation.json");
    let p = store.profile(&id)?;
    let data = if receipt.exists() {
        storage::read_json(&receipt)?
    } else {
        Value::Null
    };
    let installed = data["minecraft"] == p.minecraft
        && data["loader"] == p.loader
        && (p.loader_version.is_empty() || data["loaderVersion"] == p.loader_version);
    Ok(
        json!({"installed":installed,"receipt":data,"dataPath":dir.to_string_lossy(),"busy":*store.busy.lock().map_err(|_|"Status lock failed")?}),
    )
}
#[tauri::command]
fn open_data(store: Db, id: String) -> Result<()> {
    let path = store.instance(&id)?;
    std::fs::create_dir_all(&path).context("Create instance folder")?;
    open::that(path).context("Open instance folder")
}
#[tauri::command]
fn open_content_folder(store: Db, id: String, folder: String) -> Result<()> {
    if !matches!(
        folder.as_str(),
        "mods" | "resourcepacks" | "shaderpacks" | "screenshots" | "logs" | "saves"
    ) {
        return Err("Unknown instance folder".into());
    }
    store.profile(&id)?;
    let path = storage::relative(&store.instance(&id)?, &folder)?;
    std::fs::create_dir_all(&path).context("Create instance content folder")?;
    open::that(path).context("Open instance content folder")
}
#[tauri::command]
fn save_studio_image(store: Db, data: String) -> Result<String> {
    studio::save(&store, &data)
}
#[tauri::command]
fn save_skin_image(store: Db, data: String) -> Result<String> {
    studio::save_skin(&store, &data)
}
#[tauri::command]
fn open_skin_exports(store: Db) -> Result<()> {
    let path = storage::relative(&store.root, "skin-exports")?;
    std::fs::create_dir_all(&path).context("Create skin export folder")?;
    open::that(path).context("Open skin export folder")
}
#[tauri::command]
fn open_studio_folder(store: Db) -> Result<()> {
    let path = storage::relative(&store.root, "studio-shots")?;
    std::fs::create_dir_all(&path).context("Create studio folder")?;
    open::that(path).context("Open studio folder")
}
#[tauri::command]
fn system_memory() -> Result<Value> {
    #[cfg(windows)]
    {
        #[repr(C)]
        struct MemoryStatus {
            length: u32,
            load: u32,
            total_physical: u64,
            available_physical: u64,
            total_page: u64,
            available_page: u64,
            total_virtual: u64,
            available_virtual: u64,
            available_extended: u64,
        }
        #[link(name = "kernel32")]
        extern "system" {
            fn GlobalMemoryStatusEx(status: *mut MemoryStatus) -> i32;
        }
        let mut status = MemoryStatus {
            length: std::mem::size_of::<MemoryStatus>() as u32,
            load: 0,
            total_physical: 0,
            available_physical: 0,
            total_page: 0,
            available_page: 0,
            total_virtual: 0,
            available_virtual: 0,
            available_extended: 0,
        };
        if unsafe { GlobalMemoryStatusEx(&mut status) } == 0 {
            return Err("Windows memory information unavailable".into());
        }
        Ok(
            json!({"totalMB":status.total_physical/1_048_576,"availableMB":status.available_physical/1_048_576}),
        )
    }
    #[cfg(not(windows))]
    {
        Err("Memory information is available in the Windows app".into())
    }
}
#[tauri::command]
fn install_core(store: Db, app: tauri::AppHandle, id: String) -> Result<()> {
    let _g = begin(&store)?;
    let _lease = store.instance_lease(&id)?;
    core_install::bundled(&store, &app, &id, true)
}
fn data_dir_override(
    args: impl IntoIterator<Item = OsString>,
) -> std::result::Result<Option<PathBuf>, String> {
    let mut args = args.into_iter();
    let _executable = args.next();
    let mut root = None;
    while let Some(argument) = args.next() {
        let text = argument.to_string_lossy();
        let value = if text == "--votex-data-dir" {
            args.next()
                .ok_or("Missing path after --votex-data-dir")?
                .into_string()
                .map_err(|_| "The Votex data path is not valid Unicode")?
        } else if let Some(value) = text.strip_prefix("--votex-data-dir=") {
            OsString::from(value)
                .into_string()
                .map_err(|_| "The Votex data path is not valid Unicode")?
        } else {
            continue;
        };
        if root.is_some() {
            return Err("Specify --votex-data-dir only once".into());
        }
        let path = PathBuf::from(value);
        if !path.is_absolute() {
            return Err("The Votex data path must be absolute".into());
        }
        root = Some(path);
    }
    Ok(root)
}
fn main() {
    tauri::Builder::default()
        .manage(auth::Auth::default())
        .setup(|app| {
            let root =
                match data_dir_override(std::env::args_os()).map_err(std::io::Error::other)? {
                    Some(root) => root,
                    None => {
                        let local = app.path().app_local_data_dir()?;
                        let parent = local
                            .parent()
                            .ok_or("Application data directory has no parent")?;
                        parent.join("VotexClient")
                    }
                };
            let store = Store::new(root).map_err(std::io::Error::other)?;
            app.manage(Arc::new(store));
            Ok(())
        })
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                if let Some(store) = window.try_state::<Arc<Store>>() {
                    if store.busy.lock().map(|b| *b).unwrap_or(true)
                        || store.running_games.load(std::sync::atomic::Ordering::Relaxed) != 0 {
                        api.prevent_close();
                        let _ = window.minimize();
                    }
                }
            }
        })
        .invoke_handler(tauri::generate_handler![
            community::community_list,
            community::community_share,
            community::community_mine,
            community::community_publish,
            community::community_visibility,
            community::community_delete,
            community::community_report,
            plan_mod_transfer,
            commit_mod_transfer,
            profile_mod_counts,
            profile_covers,
            load_state,
            save_state,
            list_versions,
            loader_choices,
            search_catalog,
            curseforge_status,
            save_curseforge_key,
            open_catalog,
            install_content,
            import_modpack,
            prepare_profile,
            profile_downloads,
            check_java,
            get_accounts,
            select_account,
            remove_account,
            start_login,
            finish_login,
            cancel_login,
            open_login,
            open_microsoft_setup,
            account_skin,
            run_game,
            native_client_status,
            install_native_client,
            horizon_runtime_status,
            install_horizon_runtime,
            get_play_history,
            search_mods,
            installed_mods,
            install_mod,
            toggle_mod,
            remove_mod,
            list_snapshots,
            create_snapshot,
            preview_restore,
            restore_snapshot,
            delete_snapshot,
            backup_usage,
            cleanup_backups,
            disable_recent,
            launch_log,
            instance_status,
            open_data,
            open_content_folder,
            system_memory,
            performance_status,
            performance_save,
            save_studio_image,
            save_skin_image,
            open_skin_exports,
            open_studio_folder,
            install_core
        ])
        .run(tauri::generate_context!())
        .expect("Votex desktop initialization failed");
}

#[cfg(all(test, windows))]
mod desktop_tests {
    #[test]
    fn game_theme_excludes_launcher_only_background_media() {
        let theme = serde_json::json!({
            "accent": "#3478ff",
            "hover": false, "proximityEffect": true, "proximity": 90,
            "glowEffect": false, "reflectionEffect": false, "glassEffect": false,
            "reducedMotion": true, "wobbleEffect": false,
            "speed": 45, "stiffness": 42, "damping": 55, "lift": 84, "tilt": 55,
            "backgroundImage": "data:image/png;base64,large-local-image",
            "backgroundVideo": "data:video/mp4;base64,large-local-video"
        });
        let exported = super::game_theme(&theme);
        assert_eq!(exported["accent"], "#3478ff");
        for key in ["hover", "proximityEffect", "proximity", "glowEffect", "reflectionEffect", "glassEffect", "reducedMotion", "wobbleEffect", "speed", "stiffness", "damping", "lift", "tilt"] {
            assert_eq!(exported[key], theme[key], "Shared interaction setting lost: {key}");
        }
        assert!(exported.get("backgroundImage").is_none());
        assert!(exported.get("backgroundVideo").is_none());
    }

    #[test]
    fn windows_memory_ffi_returns_consistent_physical_values() {
        let value = super::system_memory().expect("Read Windows physical memory");
        let total = value["totalMB"].as_u64().unwrap();
        let available = value["availableMB"].as_u64().unwrap();
        assert!(total > 0);
        assert!(available <= total);
    }
}

#[cfg(test)]
mod data_dir_tests {
    use std::{ffi::OsString, path::PathBuf};

    fn args<'a>(values: &'a [&'a str]) -> impl Iterator<Item = OsString> + 'a {
        values.iter().map(OsString::from)
    }

    #[test]
    fn uses_the_default_when_no_override_is_given() {
        assert_eq!(
            super::data_dir_override(args(&["votex-launcher.exe"])).unwrap(),
            None
        );
    }

    #[test]
    fn accepts_equals_and_separate_path_forms() {
        let path = std::env::temp_dir().join("votex-isolated-test");
        let path_text = path.to_string_lossy();
        assert_eq!(
            super::data_dir_override(args(&[
                "votex-launcher.exe",
                &format!("--votex-data-dir={path_text}"),
            ]))
            .unwrap(),
            Some(path.clone())
        );
        assert_eq!(
            super::data_dir_override(args(&[
                "votex-launcher.exe",
                "--votex-data-dir",
                &path_text,
            ]))
            .unwrap(),
            Some(path)
        );
    }

    #[test]
    fn rejects_relative_or_duplicate_overrides() {
        assert!(super::data_dir_override(args(&[
            "votex-launcher.exe",
            "--votex-data-dir=relative",
        ]))
        .is_err());
        assert!(super::data_dir_override(args(&[
            "votex-launcher.exe",
            "--votex-data-dir=C:\\votex-a",
            "--votex-data-dir=C:\\votex-b",
        ]))
        .is_err());
    }

    #[test]
    fn returned_path_is_exact() {
        let path = PathBuf::from(std::env::temp_dir()).join("votex-isolated-test");
        let path_text = path.to_string_lossy();
        assert_eq!(
            super::data_dir_override(args(&[
                "votex-launcher.exe",
                &format!("--votex-data-dir={path_text}"),
            ]))
            .unwrap(),
            Some(path)
        );
    }
}
