//! Optional, unmodified Pomme runtime for Minecraft Java Edition.
//!
//! Pomme is kept as a separate process and installed per user from the
//! upstream GitHub release. No Pomme or Mojang code is linked into Votex.

use crate::{
    minecraft,
    network,
    play_history,
    storage::{self, Context, Profile, Result, Store},
};
use serde_json::{json, Value};
use std::{
    fs::{self, File},
    io::{Read, Write},
    path::PathBuf,
    process::Stdio,
    sync::{
        atomic::Ordering,
        Arc,
    },
    time::Duration,
};
use tauri::{Emitter, Manager};
use tokio::process::Command;
use zip::ZipArchive;

const RELEASE_API: &str = "https://api.github.com/repos/PommeMC/Client/releases/latest";
const ASSET_NAME: &str = "pomme-client-windows-x64.zip";
const EXECUTABLE_NAME: &str = "pomme-client.exe";
const MAX_ARCHIVE_SIZE: u64 = 128 * 1024 * 1024;
const MAX_EXECUTABLE_SIZE: u64 = 128 * 1024 * 1024;
const LICENSE_NOTICE: &str = "GPL-3.0-or-later";
const SOURCE_URL: &str = "https://github.com/PommeMC/Client";

#[derive(Debug)]
struct Release {
    tag: String,
    version: String,
    url: String,
    sha256: String,
    size: u64,
}

fn emit_job(app: &tauri::AppHandle, phase: &str, message: &str, completed: u64, total: u64) {
    let _ = app.emit(
        "votex:job",
        json!({
            "phase": phase,
            "message": message,
            "completed": completed,
            "total": total,
        }),
    );
}

fn release_version(tag: &str) -> Result<String> {
    let version = tag
        .strip_prefix("client-v")
        .ok_or("Pomme release tag has an unexpected format")?;
    if version.is_empty()
        || version.len() > 64
        || !version
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || matches!(b, b'.' | b'+' | b'-'))
    {
        return Err("Pomme release tag contains an unsafe version".into());
    }
    Ok(version.to_owned())
}

async fn latest_release(client: &reqwest::Client) -> Result<Release> {
    let json = network::json(client, RELEASE_API).await?;
    let tag = json["tag_name"]
        .as_str()
        .ok_or("Pomme release response has no version tag")?
        .to_owned();
    let version = release_version(&tag)?;
    let asset = json["assets"]
        .as_array()
        .and_then(|assets| assets.iter().find(|item| item["name"] == ASSET_NAME))
        .ok_or("Pomme's latest release has no Windows x64 archive")?;
    let url = asset["browser_download_url"]
        .as_str()
        .ok_or("Pomme Windows archive has no download URL")?
        .to_owned();
    let parsed = reqwest::Url::parse(&url).context("Parse Pomme download URL")?;
    // GitHub percent-encodes the '+' in version tags in the asset URL.
    let encoded_tag = tag.replace('+', "%2B");
    let expected_prefix = format!("/PommeMC/Client/releases/download/{encoded_tag}/");
    if parsed.scheme() != "https"
        || parsed.host_str() != Some("github.com")
        || parsed.path() != format!("{expected_prefix}{ASSET_NAME}")
    {
        return Err("Pomme release URL is outside the official GitHub release".into());
    }
    network::allowed(&url)?;
    let digest = asset["digest"]
        .as_str()
        .and_then(|value| value.strip_prefix("sha256:"))
        .ok_or("Pomme's GitHub release does not publish a SHA-256 asset digest")?
        .to_ascii_lowercase();
    if digest.len() != 64 || !digest.bytes().all(|b| b.is_ascii_hexdigit()) {
        return Err("Pomme's published SHA-256 digest is malformed".into());
    }
    let size = asset["size"]
        .as_u64()
        .filter(|size| *size > 0 && *size <= MAX_ARCHIVE_SIZE)
        .ok_or("Pomme Windows archive exceeds the allowed download size")?;
    Ok(Release {
        tag,
        version,
        url,
        sha256: digest,
        size,
    })
}

fn no_runtime(reason: Option<&str>) -> Value {
    json!({
        "installed": false,
        "version": null,
        "releaseTag": null,
        "license": LICENSE_NOTICE,
        "sourceUrl": SOURCE_URL,
        "reason": reason,
    })
}

fn receipt_path(store: &Store) -> PathBuf {
    store.root.join("config/native-runtime-pomme.json")
}

fn runtime_folder(store: &Store, component: &str) -> Result<PathBuf> {
    storage::safe_id(component)?;
    storage::relative(&store.root, &format!("runtimes/pomme/{component}"))
}

fn status_inner(store: &Store) -> Result<Value> {
    let receipt = receipt_path(store);
    if !receipt.exists() {
        return Ok(no_runtime(None));
    }
    if fs::symlink_metadata(&receipt)
        .map(|meta| meta.file_type().is_symlink() || !meta.is_file())
        .unwrap_or(true)
    {
        return Ok(no_runtime(Some("Pomme receipt is not a regular file; reinstall the runtime.")));
    }
    let record = match storage::read_json(&receipt) {
        Ok(value) => value,
        Err(_) => return Ok(no_runtime(Some("Pomme receipt is unreadable; reinstall the runtime."))),
    };
    let Some(version) = record["version"].as_str() else {
        return Ok(no_runtime(Some("Pomme receipt is missing its version; reinstall the runtime.")));
    };
    let Some(tag) = record["releaseTag"].as_str() else {
        return Ok(no_runtime(Some("Pomme receipt is missing its release tag; reinstall the runtime.")));
    };
    let Some(folder) = record["runtimeFolder"].as_str() else {
        return Ok(no_runtime(Some("Pomme receipt is missing its runtime folder; reinstall the runtime.")));
    };
    let Some(expected_hash) = record["executableSha256"].as_str() else {
        return Ok(no_runtime(Some("Pomme receipt is missing its executable hash; reinstall the runtime.")));
    };
    if !matches!(release_version(tag), Ok(ref parsed) if parsed == version)
        || expected_hash.len() != 64
        || !expected_hash.bytes().all(|b| b.is_ascii_hexdigit())
    {
        return Ok(no_runtime(Some("Pomme receipt failed validation; reinstall the runtime.")));
    }
    let dir = match runtime_folder(store, folder) {
        Ok(path) => path,
        Err(_) => return Ok(no_runtime(Some("Pomme runtime path is unsafe; reinstall the runtime."))),
    };
    let exe = dir.join(EXECUTABLE_NAME);
    let meta = match fs::symlink_metadata(&exe) {
        Ok(meta) if meta.is_file() && !meta.file_type().is_symlink() && meta.len() <= MAX_EXECUTABLE_SIZE => meta,
        _ => return Ok(no_runtime(Some("Pomme executable is missing or invalid; reinstall the runtime."))),
    };
    let _ = meta;
    let bytes = match fs::read(&exe) {
        Ok(bytes) => bytes,
        Err(_) => return Ok(no_runtime(Some("Pomme executable could not be read; reinstall the runtime."))),
    };
    if validate_pe(&bytes).is_err()
        || network::hash(&bytes, "sha256")?.eq_ignore_ascii_case(expected_hash) == false
    {
        return Ok(no_runtime(Some("Pomme executable failed its integrity check; reinstall it from the upstream release.")));
    }
    Ok(json!({
        "installed": true,
        "version": version,
        "releaseTag": tag,
        "license": LICENSE_NOTICE,
        "sourceUrl": SOURCE_URL,
        "reason": null,
    }))
}

pub fn status(store: &Store) -> Result<Value> {
    status_inner(store)
}

fn validate_pe(bytes: &[u8]) -> Result<()> {
    if bytes.len() < 0x40 || bytes.len() as u64 > MAX_EXECUTABLE_SIZE || &bytes[..2] != b"MZ" {
        return Err("Pomme archive did not contain a valid Windows executable".into());
    }
    let pe_offset = u32::from_le_bytes(bytes[0x3c..0x40].try_into().unwrap()) as usize;
    if pe_offset.checked_add(6).is_none_or(|end| end > bytes.len())
        || &bytes[pe_offset..pe_offset + 4] != b"PE\0\0"
        || u16::from_le_bytes(bytes[pe_offset + 4..pe_offset + 6].try_into().unwrap()) != 0x8664
    {
        return Err("Pomme runtime is not a Windows x64 executable".into());
    }
    Ok(())
}

pub async fn install(store: &Store, app: &tauri::AppHandle) -> Result<Value> {
    let client = network::client()?;
    emit_job(app, "installing", "Prüfe die neueste offizielle Pomme-Version …", 0, 0);
    let release = latest_release(&client).await?;
    let current = status_inner(store)?;
    if current["installed"] == true && current["releaseTag"] == release.tag {
        emit_job(app, "installed", "Pomme ist bereits aktuell und die EXE-Prüfsumme stimmt.", 1, 1);
        return Ok(current);
    }

    emit_job(
        app,
        "installing",
        &format!("Lade Pomme {} direkt von GitHub …", release.version),
        0,
        release.size,
    );
    let archive_path = store.root.join("cache/pomme-client-windows-x64.zip");
    network::download(
        &client,
        &release.url,
        &archive_path,
        Some(("sha256", &release.sha256)),
    )
    .await?;
    emit_job(
        app,
        "installing",
        "SHA-256 stimmt. Prüfe den Inhalt der Windows x64 ZIP-Datei …",
        release.size,
        release.size,
    );

    let mut archive = ZipArchive::new(File::open(&archive_path).context("Open verified Pomme archive")?)
        .context("Pomme release archive is invalid")?;
    if archive.len() != 1 {
        return Err("Pomme Windows ZIP has an unexpected file layout".into());
    }
    let mut entry = archive.by_index(0).context("Read Pomme executable from ZIP")?;
    if entry.name() != EXECUTABLE_NAME || entry.size() == 0 || entry.size() > MAX_EXECUTABLE_SIZE {
        return Err("Pomme ZIP does not contain the expected Windows x64 executable".into());
    }
    let mut bytes = Vec::with_capacity(entry.size() as usize);
    entry.read_to_end(&mut bytes).context("Extract Pomme executable")?;
    validate_pe(&bytes)?;
    let executable_hash = network::hash(&bytes, "sha256")?;
    drop(entry);
    drop(archive);

    let runtime_root = store.root.join("runtimes/pomme");
    fs::create_dir_all(&runtime_root).context("Create Pomme runtime folder")?;
    let readable_version: String = release
        .version
        .chars()
        .map(|c| if c.is_ascii_alphanumeric() { c.to_ascii_lowercase() } else { '-' })
        .collect();
    let mut folder = format!("{}-{}", readable_version, &release.sha256[..12]);
    let mut destination = runtime_folder(store, &folder)?;
    if destination.exists() {
        folder.push_str(&format!("-{}", &uuid::Uuid::new_v4().simple().to_string()[..8]));
        destination = runtime_folder(store, &folder)?;
    }
    let stage = runtime_root.join(format!(".stage-{}", uuid::Uuid::new_v4().simple()));
    fs::create_dir(&stage).context("Create isolated Pomme staging folder")?;
    let stage_exe = stage.join(EXECUTABLE_NAME);
    let mut output = File::options()
        .write(true)
        .create_new(true)
        .open(&stage_exe)
        .context("Create staged Pomme executable")?;
    output.write_all(&bytes).context("Write staged Pomme executable")?;
    output.sync_all().context("Flush staged Pomme executable")?;
    drop(output);
    if let Err(error) = fs::rename(&stage, &destination) {
        let _ = fs::remove_file(&stage_exe);
        let _ = fs::remove_dir(&stage);
        return Err(format!("Commit verified Pomme runtime: {error}"));
    }
    let receipt = json!({
        "schemaVersion": 1,
        "version": release.version,
        "releaseTag": release.tag,
        "runtimeFolder": folder,
        "assetUrl": release.url,
        "assetSha256": release.sha256,
        "executableSha256": executable_hash,
        "license": LICENSE_NOTICE,
        "sourceUrl": SOURCE_URL,
        "installedAt": chrono::Utc::now().to_rfc3339(),
    });
    storage::atomic(
        &receipt_path(store),
        &serde_json::to_vec_pretty(&receipt).context("Serialize Pomme receipt")?,
    )?;
    emit_job(app, "installed", "Pomme ist installiert und die native EXE wurde geprüft.", release.size, release.size);
    status_inner(store)
}

fn reject_java_mods(store: &Store, profile: &Profile) -> Result<()> {
    if profile.loader != "vanilla" {
        return Err("Pomme ist ein nativer Client und lädt keine Fabric-, Forge-, Quilt- oder NeoForge-Mods. Wähle für Rust ein Vanilla-Profil; Java-Profile bleiben unverändert.".into());
    }
    let mods = store.instance(&profile.id)?.join("mods");
    match fs::symlink_metadata(&mods) {
        Ok(meta) if meta.file_type().is_symlink() => return Err("Pomme kann keinen Mods-Ordner verwenden, der ein Link ist.".into()),
        Ok(meta) if meta.is_dir() => {
            for entry in fs::read_dir(&mods).context("Read profile mods folder")? {
                let entry = entry.context("Read profile mod entry")?;
                let meta = fs::symlink_metadata(entry.path()).context("Inspect profile mod entry")?;
                if meta.file_type().is_symlink() {
                    return Err("Pomme kann keinen verlinkten Java-Mod-Ordner verwenden.".into());
                }
                if meta.is_file()
                    && entry
                        .path()
                        .extension()
                        .is_some_and(|ext| ext.to_string_lossy().eq_ignore_ascii_case("jar"))
                {
                    return Err("Dieses Profil enthält Java-Mods, die in Pomme nicht geladen werden. Starte es über Java oder entferne die Mods aus einem separaten Vanilla-Profil.".into());
                }
            }
        }
        Ok(_) => {}
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(error) => return Err(format!("Inspect profile mods folder: {error}")),
    }
    Ok(())
}

pub async fn launch(
    store: Arc<Store>,
    profile: Profile,
    server_address: Option<String>,
    app: tauri::AppHandle,
    preparation: crate::Busy,
) -> Result<()> {
    reject_java_mods(&store, &profile)?;
    let status = status_inner(&store)?;
    if status["installed"] != true {
        return Err("Der native Rust-Client ist noch nicht installiert oder seine Integritätsprüfung schlug fehl. Öffne Startoptionen und installiere/aktualisiere Pomme.".into());
    }
    let record = storage::read_json(&receipt_path(&store))?;
    let folder = record["runtimeFolder"].as_str().ok_or("Pomme receipt has no runtime folder")?;
    let exe = runtime_folder(&store, folder)?.join(EXECUTABLE_NAME);
    let profile_root = storage::relative(&store.root, &format!("native-data/pomme/{}", profile.id))?;
    let game_dir = profile_root.join("game");
    let assets_dir = profile_root.join("assets");
    let versions_dir = profile_root.join("versions");
    for dir in [&game_dir, &assets_dir, &versions_dir] {
        fs::create_dir_all(dir).context("Create isolated Pomme profile data")?;
    }

    let mut command = Command::new(&exe);
    command
        .arg("--version")
        .arg(&profile.minecraft)
        .arg("--launch-token")
        .arg("votex")
        .arg("--game-dir")
        .arg(&game_dir)
        .arg("--assets-dir")
        .arg(&assets_dir)
        .arg("--versions-dir")
        .arg(&versions_dir)
        .current_dir(&game_dir)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());
    if let Some(address) = server_address.as_deref() {
        let address = play_history::server_address(address)?;
        command.arg("--quick-access-multiplayer").arg(address);
    }
    minecraft::event(&app, "starting", "Starte den nativen Rust/Vulkan-Client Pomme …", 0, 0);
    let mut child = command.spawn().map_err(|error| {
        format!("Pomme konnte nicht gestartet werden: {error}. Falls Windows die EXE blockiert, bitte den Windows-Administrator die eng begrenzte Freigabe prüfen lassen.")
    })?;
    if let Err(error) = play_history::record_launched(&store, &profile.id) {
        eprintln!("Could not save last native profile: {error}");
    }
    store.running_games.fetch_add(1, Ordering::Relaxed);
    struct RunningGame(Arc<Store>);
    impl Drop for RunningGame {
        fn drop(&mut self) {
            self.0.running_games.fetch_sub(1, Ordering::Relaxed);
        }
    }
    let running = RunningGame(store.clone());
    let pid = child.id().unwrap_or_default();
    minecraft::event(&app, "running", &format!("Pomme native client process {pid} is running."), 0, 0);
    drop(preparation);
    if store.state().map(|state| state["minimizeOnLaunch"] == true).unwrap_or(false) {
        if let Some(window) = app.get_webview_window("main") {
            let _ = window.minimize();
        }
    }
    let started = std::time::Instant::now();
    let status = loop {
        if let Some(status) = child.try_wait().context("Monitor Pomme process")? {
            break status;
        }
        tokio::time::sleep(Duration::from_secs(1)).await;
    };
    drop(running);
    if let Some(window) = app.get_webview_window("main") {
        if window.is_minimized().unwrap_or(false) {
            let _ = window.unminimize();
            let _ = window.set_focus();
        }
    }
    if status.success() {
        minecraft::event(
            &app,
            "exited",
            &format!("Pomme exited normally after {} seconds.", started.elapsed().as_secs()),
            0,
            0,
        );
        Ok(())
    } else {
        Err(format!("Pomme exited with code {:?}. Review Pomme's log under {}.", status.code(), game_dir.join("logs").display()))
    }
}
