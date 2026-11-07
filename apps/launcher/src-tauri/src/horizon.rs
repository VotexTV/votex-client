//! Optional, signed Horizon (Rust) client integration.
//!
//! Horizon stays an upstream executable in its own per-user runtime folder. Votex
//! supplies only the supported Launch API session and the owner's verified game
//! files; Java profile mods and game data are never reused as Horizon content.

use crate::{
    auth, minecraft, network, play_history,
    storage::{self, Context, GameEngine, Profile, Result, Store},
};
use ring::signature::{ED25519, UnparsedPublicKey};
use serde_json::{json, Value};
use std::{
    fs::{self, File},
    io::{Read, Write},
    path::{Path, PathBuf},
    process::Stdio,
    sync::{
        atomic::Ordering,
        Arc,
    },
    time::Duration,
};
use tauri::{Emitter, Manager};
use tokio::{
    io::{AsyncBufReadExt, AsyncWriteExt, BufReader},
    process::Command,
};
use zip::ZipArchive;

// Horizon's current external-launch contract is published on the rolling nightly
// channel. The stable `latest` endpoint points at the Horizon launcher, not
// the game build that exposes --launch-api.
const RELEASE_API: &str = "https://api.github.com/repos/horizon-rust/horizon/releases/tags/nightly";
const SOURCE_URL: &str = "https://github.com/horizon-rust/horizon";
const LICENSE: &str = "MIT";
const ASSET: &str = "horizon-windows-x64.zip";
const MAX_ARCHIVE_SIZE: u64 = 128 * 1024 * 1024;
const MAX_UNPACKED_SIZE: u64 = 768 * 1024 * 1024;
const MAX_FILE_SIZE: u64 = 256 * 1024 * 1024;
const HORIZON_VERSION: &str = "26.3";
const RELEASE_PUBLIC_KEY: &str = "MU4oiIQcCxkZvbwSjjXs7SySCuM6ywRQ3nGaJYZG0gw=";

#[derive(Debug)]
struct ReleaseAsset {
    url: String,
    size: u64,
}

#[derive(Debug)]
struct Release {
    tag: String,
    version: String,
    archive: ReleaseAsset,
    sums: ReleaseAsset,
    signature: ReleaseAsset,
}

fn emit(app: &tauri::AppHandle, phase: &str, message: &str, completed: u64, total: u64) {
    let _ = app.emit(
        "votex:job",
        json!({ "phase": phase, "message": message, "completed": completed, "total": total }),
    );
}

fn receipt_path(store: &Store) -> PathBuf {
    store.root.join("config/native-runtime-horizon.json")
}

fn runtime_path(store: &Store, relative: &str) -> Result<PathBuf> {
    storage::relative(&store.root.join("native-data/horizon"), relative)
}

fn nightly_version(title: &str) -> Result<String> {
    let title = title.strip_prefix("Nightly ").ok_or("Horizon nightly title is invalid")?;
    let (date, commit) = title.split_once(" · ").ok_or("Horizon nightly title has no commit")?;
    let date_parts = date.split('-').collect::<Vec<_>>();
    if date_parts.len() != 3
        || date_parts[0].len() != 4
        || date_parts[1].len() != 2
        || date_parts[2].len() != 2
        || !date.bytes().all(|byte| byte.is_ascii_digit() || byte == b'-')
        || !(7..=40).contains(&commit.len())
        || !commit.bytes().all(|byte| byte.is_ascii_hexdigit())
    {
        return Err("Horizon nightly title does not contain a valid date and commit".into());
    }
    Ok(format!("nightly-{date}-{}", commit.to_ascii_lowercase()))
}

fn valid_nightly_version(version: &str) -> bool {
    let Some(value) = version.strip_prefix("nightly-") else { return false };
    let Some((date, commit)) = value.rsplit_once('-') else { return false };
    nightly_version(&format!("Nightly {date} · {commit}")).is_ok_and(|parsed| parsed == version)
}

fn asset(value: &Value, tag: &str, expected_name: &str, max_size: u64) -> Result<ReleaseAsset> {
    let item = value["assets"]
        .as_array()
        .and_then(|items| items.iter().find(|item| item["name"] == expected_name))
        .ok_or_else(|| format!("Horizon release has no {expected_name}"))?;
    let url = item["browser_download_url"]
        .as_str()
        .ok_or("Horizon release asset URL is missing")?;
    let parsed = reqwest::Url::parse(url).context("Parse Horizon release asset URL")?;
    let expected_path = format!("/horizon-rust/horizon/releases/download/{tag}/{expected_name}");
    if parsed.scheme() != "https"
        || parsed.host_str() != Some("github.com")
        || parsed.path() != expected_path
        || !parsed.username().is_empty()
        || parsed.password().is_some()
        || parsed.query().is_some()
        || parsed.fragment().is_some()
    {
        return Err("Horizon release asset is not hosted at its official GitHub release".into());
    }
    network::allowed(url)?;
    let size = item["size"]
        .as_u64()
        .filter(|size| *size > 0 && *size <= max_size)
        .ok_or_else(|| format!("Horizon {expected_name} exceeds its size limit"))?;
    Ok(ReleaseAsset {
        url: url.to_owned(),
        size,
    })
}

async fn latest_release() -> Result<Release> {
    let client = network::client()?;
    let data = network::json(&client, RELEASE_API).await?;
    if data["tag_name"] != "nightly" || data["prerelease"] != true {
        return Err("Horizon did not return its official rolling nightly build".into());
    }
    let tag = "nightly".to_owned();
    let version = nightly_version(data["name"].as_str().ok_or("Horizon nightly title is missing")?)?;
    Ok(Release {
        archive: asset(&data, &tag, ASSET, MAX_ARCHIVE_SIZE)?,
        sums: asset(&data, &tag, "SHA256SUMS", 1024 * 1024)?,
        // Version-bound Nightly signature prevents an older build being replayed
        // through the rolling `nightly` download URL.
        signature: asset(&data, &tag, "SHA256SUMS.v2.sig", 16 * 1024)?,
        tag,
        version,
    })
}

async fn verified_download(store: &Store, app: &tauri::AppHandle, release: &Release) -> Result<PathBuf> {
    let cache = store.root.join("cache/horizon");
    fs::create_dir_all(&cache).context("Create Horizon download cache")?;
    let client = network::client()?;
    let sums_path = cache.join(format!("{}-SHA256SUMS", release.version));
    let sig_path = cache.join(format!("{}-SHA256SUMS.v2.sig", release.version));
    let archive_path = cache.join(format!("{}-{ASSET}", release.version));
    emit(app, "installing", "Lade Horizons signierte Prüfsumme …", 0, release.sums.size);
    let valid_cached_metadata = read_limited(&sums_path, release.sums.size)
        .and_then(|sums| {
            let signatures = read_limited(&sig_path, release.signature.size)?;
            verify_release_signature(&release.version, &sums, &signatures)
        })
        .is_ok();
    if !valid_cached_metadata {
        download_asset(&client, &release.sums, &sums_path, None).await?;
        download_asset(&client, &release.signature, &sig_path, None).await?;
    }
    let sums = read_limited(&sums_path, 1024 * 1024)?;
    let signatures = read_limited(&sig_path, 16 * 1024)?;
    if sums.len() as u64 != release.sums.size || signatures.len() as u64 != release.signature.size {
        return Err("Horizon signed release metadata size does not match GitHub's asset record".into());
    }
    verify_release_signature(&release.version, &sums, &signatures)?;
    let expected = checksum_for(&sums, ASSET)?;
    emit(
        app,
        "installing",
        &format!("Lade Horizon {} aus dem offiziellen Release …", release.version),
        0,
        release.archive.size,
    );
    download_asset(&client, &release.archive, &archive_path, Some(("sha256", &expected))).await?;
    if fs::metadata(&archive_path).context("Read Horizon archive size")?.len() != release.archive.size {
        return Err("Horizon archive size does not match GitHub release metadata".into());
    }
    Ok(archive_path)
}

async fn download_asset(
    client: &reqwest::Client,
    asset: &ReleaseAsset,
    path: &Path,
    expected: Option<(&str, &str)>,
) -> Result<()> {
    for attempt in 0..4 {
        let url = if attempt == 0 {
            asset.url.clone()
        } else {
            format!("{}?votex={}", asset.url, uuid::Uuid::new_v4())
        };
        match network::download_limited(client, &url, path, expected, asset.size).await {
            Ok(()) => return Ok(()),
            Err(error) if attempt == 3 => return Err(error),
            Err(_) => tokio::time::sleep(Duration::from_millis(250)).await,
        }
    }
    Err("Horizon download failed".into())
}

fn read_limited(path: &Path, max: u64) -> Result<Vec<u8>> {
    let metadata = fs::symlink_metadata(path).context("Inspect downloaded Horizon asset")?;
    if !metadata.is_file() || metadata.file_type().is_symlink() || metadata.len() > max {
        return Err("Downloaded Horizon release metadata is invalid or too large".into());
    }
    fs::read(path).context("Read downloaded Horizon release metadata")
}

fn verify_release_signature(tag: &str, sums: &[u8], signatures: &[u8]) -> Result<()> {
    let key_bytes = base64::Engine::decode(&base64::engine::general_purpose::STANDARD, RELEASE_PUBLIC_KEY)
        .context("Decode Horizon release public key")?;
    let key_array: [u8; 32] = key_bytes
        .try_into()
        .map_err(|_| "Horizon release public key has the wrong size")?;
    let key = UnparsedPublicKey::new(&ED25519, &key_array);
    let mut message = format!("horizon release {tag}\n").into_bytes();
    message.extend_from_slice(sums);
    for line in signatures.split(|byte| *byte == b'\n') {
        let line = line.strip_suffix(b"\r").unwrap_or(line);
        if line.is_empty() || line.starts_with(b"untrusted comment:") {
            continue;
        }
        let Ok(bytes) = base64::Engine::decode(&base64::engine::general_purpose::STANDARD, line) else {
            continue;
        };
        if key.verify(&message, &bytes).is_ok() {
            return Ok(());
        }
    }
    Err("Horizons SHA256SUMS-Signatur stimmt nicht mit dem eingebauten Release-Schlüssel überein. Das Archiv wurde nicht installiert.".into())
}

fn checksum_for(sums: &[u8], name: &str) -> Result<String> {
    let text = std::str::from_utf8(sums).context("Horizon SHA256SUMS is not UTF-8")?;
    let mut found = None;
    for line in text.lines() {
        let mut fields = line.split_whitespace();
        let Some(hash) = fields.next() else { continue };
        let Some(file) = fields.next() else { continue };
        let file = file.strip_prefix('*').unwrap_or(file);
        if file == name {
            if hash.len() != 64 || !hash.bytes().all(|byte| byte.is_ascii_hexdigit()) || found.is_some() {
                return Err("Horizon SHA256SUMS has an invalid or duplicate archive entry".into());
            }
            found = Some(hash.to_ascii_lowercase());
        }
    }
    found.ok_or_else(|| "Signed Horizon SHA256SUMS does not list the Windows x64 archive".into())
}

fn validate_pe(bytes: &[u8]) -> Result<()> {
    if bytes.len() < 0x40 || bytes.len() as u64 > MAX_FILE_SIZE || &bytes[..2] != b"MZ" {
        return Err("Horizon release is missing a valid Windows executable".into());
    }
    let offset = u32::from_le_bytes(bytes[0x3c..0x40].try_into().unwrap()) as usize;
    if offset.checked_add(6).is_none_or(|end| end > bytes.len())
        || &bytes[offset..offset + 4] != b"PE\0\0"
        || u16::from_le_bytes(bytes[offset + 4..offset + 6].try_into().unwrap()) != 0x8664
    {
        return Err("Horizon release executable is not Windows x64".into());
    }
    Ok(())
}

fn find_file(root: &Path, wanted: &str) -> Result<PathBuf> {
    let mut found = None;
    for entry in walkdir::WalkDir::new(root).follow_links(false).max_depth(8) {
        let entry = entry.map_err(|error| format!("Inspect Horizon archive: {error}"))?;
        if entry.file_type().is_file() && entry.file_name().to_string_lossy().eq_ignore_ascii_case(wanted) {
            if found.is_some() {
                return Err(format!("Horizon archive contains more than one {wanted}"));
            }
            found = Some(entry.path().strip_prefix(root).context("Resolve Horizon file")?.to_path_buf());
        }
    }
    found.ok_or_else(|| format!("Horizon release archive does not contain {wanted}"))
}

fn install_archive(store: &Store, release: &Release, archive_path: &Path) -> Result<Value> {
    let runtime_root = store.root.join("native-data/horizon");
    let releases_root = runtime_root.join("releases");
    fs::create_dir_all(&releases_root).context("Create Horizon runtime folder")?;
    let archive_bytes = fs::read(archive_path).context("Read verified Horizon archive")?;
    let checksum = network::hash(&archive_bytes, "sha256")?;
    let mut folder = format!("{}-{}", release.tag.replace('.', "_").replace('-', "_"), &checksum[..12]);
    storage::safe_id(&folder)?;
    let mut destination = releases_root.join(&folder);
    if destination.exists() {
        folder.push_str(&format!("-{}", &uuid::Uuid::new_v4().simple().to_string()[..8]));
        storage::safe_id(&folder)?;
        destination = releases_root.join(&folder);
    }
    let stage = runtime_root.join(format!(".stage-{}", uuid::Uuid::new_v4().simple()));
    fs::create_dir_all(&stage).context("Create Horizon staging folder")?;
    let result = (|| -> Result<(String, String, String, String, std::collections::BTreeMap<String, String>)> {
        let mut zip = ZipArchive::new(std::io::Cursor::new(archive_bytes)).context("Open verified Horizon ZIP")?;
        if zip.len() == 0 || zip.len() > 50_000 {
            return Err("Horizon archive has an unexpected entry count".into());
        }
        let mut unpacked = 0u64;
        let mut names = std::collections::HashSet::new();
        let mut manifest = std::collections::BTreeMap::new();
        for index in 0..zip.len() {
            let mut entry = zip.by_index(index).context("Read Horizon ZIP entry")?;
            if entry.is_dir() { continue; }
            let name = entry.name().to_owned();
            if entry.enclosed_name().is_none()
                || name.contains('\\')
                || name == ".local"
                || name.starts_with(".local/")
                || entry.unix_mode().is_some_and(|mode| mode & 0o170000 == 0o120000)
                || !names.insert(name.to_ascii_lowercase())
            {
                return Err("Horizon ZIP contains an unsafe or duplicate path".into());
            }
            if entry.size() > MAX_FILE_SIZE {
                return Err("Horizon ZIP contains an oversized file".into());
            }
            unpacked = unpacked.saturating_add(entry.size());
            if unpacked > MAX_UNPACKED_SIZE {
                return Err("Horizon ZIP exceeds its extraction limit".into());
            }
            let path = storage::relative(&stage, &name)?;
            if let Some(parent) = path.parent() { fs::create_dir_all(parent).context("Create Horizon file folder")?; }
            let mut bytes = Vec::with_capacity(entry.size().min(8 * 1024 * 1024) as usize);
            entry.by_ref().take(MAX_FILE_SIZE + 1).read_to_end(&mut bytes).context("Extract Horizon file")?;
            if bytes.len() as u64 != entry.size() { return Err("Horizon ZIP entry size is invalid".into()); }
            manifest.insert(name.replace('\\', "/"), network::hash(&bytes, "sha256")?);
            let mut file = File::options().write(true).create_new(true).open(&path).context("Write Horizon file")?;
            file.write_all(&bytes).context("Write Horizon file")?;
            file.sync_all().context("Flush Horizon file")?;
        }
        let client_rel = find_file(&stage, "horizon-client.exe")?;
        let prepare_rel = find_file(&stage, "horizon-prepare.exe")?;
        if client_rel.parent() != prepare_rel.parent() {
            return Err("Horizon client and preparer are not in the same release folder".into());
        }
        let client_bytes = fs::read(stage.join(&client_rel)).context("Read Horizon client")?;
        let prepare_bytes = fs::read(stage.join(&prepare_rel)).context("Read Horizon preparer")?;
        validate_pe(&client_bytes)?;
        validate_pe(&prepare_bytes)?;
        Ok((
            client_rel.to_string_lossy().replace('\\', "/"),
            prepare_rel.to_string_lossy().replace('\\', "/"),
            network::hash(&client_bytes, "sha256")?,
            network::hash(&prepare_bytes, "sha256")?,
            manifest,
        ))
    })();
    let (client_file, prepare_file, client_hash, prepare_hash, manifest) = match result {
        Ok(value) => value,
        Err(error) => { let _ = fs::remove_dir_all(&stage); return Err(error); }
    };
    fs::rename(&stage, &destination).context("Commit verified Horizon runtime")?;
    let receipt = json!({
        "schemaVersion": 1,
        "version": release.version,
        "releaseTag": release.tag,
        "runtimeFolder": format!("releases/{folder}"),
        "clientFile": client_file,
        "prepareFile": prepare_file,
        "clientSha256": client_hash,
        "prepareSha256": prepare_hash,
        "archiveSha256": checksum,
        "files": manifest,
        "license": LICENSE,
        "sourceUrl": SOURCE_URL,
        "installedAt": chrono::Utc::now().to_rfc3339(),
    });
    storage::atomic(&receipt_path(store), &serde_json::to_vec_pretty(&receipt).context("Serialize Horizon receipt")?)?;
    status_inner(store)
}

fn status_inner(store: &Store) -> Result<Value> {
    let receipt = receipt_path(store);
    if !receipt.exists() { return Ok(status_value(None)); }
    if fs::symlink_metadata(&receipt).map(|meta| meta.file_type().is_symlink() || !meta.is_file()).unwrap_or(true) {
        return Ok(status_value(Some("Horizon runtime record is not a regular file; install the runtime again.")));
    }
    let record = match storage::read_json(&receipt) {
        Ok(value) => value,
        Err(_) => return Ok(status_value(Some("Horizon runtime record could not be read; install the runtime again."))),
    };
    let (Some(version), Some(tag), Some(folder), Some(client_file), Some(prepare_file), Some(client_hash), Some(prepare_hash), Some(manifest)) = (
        record["version"].as_str(), record["releaseTag"].as_str(), record["runtimeFolder"].as_str(),
        record["clientFile"].as_str(), record["prepareFile"].as_str(), record["clientSha256"].as_str(), record["prepareSha256"].as_str(),
        record["files"].as_object(),
    ) else { return Ok(status_value(Some("Horizon runtime record is incomplete; install the runtime again."))); };
    if tag != "nightly"
        || !valid_nightly_version(version)
        || [client_hash, prepare_hash].iter().any(|hash| hash.len() != 64 || !hash.bytes().all(|byte| byte.is_ascii_hexdigit()))
    { return Ok(status_value(Some("Horizon runtime record failed validation; install the runtime again."))); }
    let folder = match runtime_path(store, folder) { Ok(path) => path, Err(_) => return Ok(status_value(Some("Horizon runtime path is invalid; install the runtime again."))) };
    if manifest.is_empty() || manifest.len() > 50_000
        || manifest.get(client_file).and_then(Value::as_str).is_none_or(|hash| !hash.eq_ignore_ascii_case(client_hash))
        || manifest.get(prepare_file).and_then(Value::as_str).is_none_or(|hash| !hash.eq_ignore_ascii_case(prepare_hash))
        || ![client_hash, prepare_hash].iter().all(|hash| hash.len() == 64 && hash.bytes().all(|byte| byte.is_ascii_hexdigit()))
    { return Ok(status_value(Some("Horizon runtime file manifest is invalid; install the runtime again."))); }
    let mut seen = 0usize;
    for entry in walkdir::WalkDir::new(&folder).follow_links(false).max_depth(16) {
        let entry = match entry { Ok(entry) => entry, Err(_) => return Ok(status_value(Some("Horizon runtime files could not be inspected; install the runtime again."))) };
        if entry.file_type().is_symlink() {
            return Ok(status_value(Some("Horizon runtime contains a linked file; install the runtime again.")));
        }
        if !entry.file_type().is_file() { continue; }
        let relative = match entry.path().strip_prefix(&folder) { Ok(path) => path.to_string_lossy().replace('\\', "/"), Err(_) => return Ok(status_value(Some("Horizon runtime path is invalid; install the runtime again."))) };
        // The upstream preparer deliberately writes transient import/exporter
        // work under this directory. The signed game bundle itself stays intact.
        if relative == ".local" || relative.starts_with(".local/") { continue; }
        let Some(expected) = manifest.get(&relative).and_then(Value::as_str) else {
            return Ok(status_value(Some("Horizon runtime contains an unexpected file; install the runtime again.")));
        };
        let metadata = match fs::symlink_metadata(entry.path()) { Ok(meta) if meta.is_file() && !meta.file_type().is_symlink() && meta.len() <= MAX_FILE_SIZE => meta, _ => return Ok(status_value(Some("A Horizon runtime file is missing or invalid; install the runtime again."))) };
        if metadata.len() > MAX_FILE_SIZE { return Ok(status_value(Some("A Horizon runtime file exceeds its size limit; install it again."))); }
        let bytes = match fs::read(entry.path()) { Ok(bytes) => bytes, Err(_) => return Ok(status_value(Some("A Horizon runtime file could not be read; install it again."))) };
        if !network::hash(&bytes, "sha256")?.eq_ignore_ascii_case(expected) {
            return Ok(status_value(Some("A Horizon runtime file failed its signed integrity check; install it again.")));
        }
        if relative == client_file || relative == prepare_file {
            if validate_pe(&bytes).is_err() { return Ok(status_value(Some("A Horizon executable is not valid Windows x64; install the runtime again."))); }
        }
        seen += 1;
    }
    if seen != manifest.len() {
        return Ok(status_value(Some("Horizon runtime is missing files from its signed archive; install it again.")));
    }
    Ok(json!({ "installed": true, "version": version, "releaseTag": tag, "license": LICENSE, "sourceUrl": SOURCE_URL, "reason": null }))
}

fn status_value(reason: Option<&str>) -> Value {
    json!({ "installed": false, "version": null, "releaseTag": null, "license": LICENSE, "sourceUrl": SOURCE_URL, "reason": reason })
}

pub fn status(store: &Store) -> Result<Value> { status_inner(store) }

pub async fn install(store: &Store, app: &tauri::AppHandle) -> Result<Value> {
    emit(app, "installing", "Prüfe Horizons offiziellen Windows-x64-Release …", 0, 0);
    let release = latest_release().await?;
    let installed = status_inner(store)?;
    if installed["installed"] == true && installed["version"] == release.version {
        emit(app, "installed", "Horizon ist bereits aktuell; die Dateien wurden geprüft.", 1, 1);
        return status_inner(store);
    }
    let archive = verified_download(store, app, &release).await?;
    emit(app, "installing", "Ed25519-Signatur und SHA-256 stimmen. Prüfe den Archivinhalt …", release.archive.size, release.archive.size);
    let status = install_archive(store, &release, &archive)?;
    emit(app, "installed", &format!("Horizon {} wurde signiert geprüft und installiert.", release.version), 1, 1);
    Ok(status)
}

fn windows_hidden(command: &mut Command) {
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        command.as_std_mut().creation_flags(0x08000000);
    }
}

async fn query_api(exe: &Path, cwd: &Path) -> Result<Value> {
    let mut command = Command::new(exe);
    command.arg("--launch-api").current_dir(cwd).stdin(Stdio::null()).stdout(Stdio::piped()).stderr(Stdio::null());
    windows_hidden(&mut command);
    let output = command.output().await.context("Read Horizon Launch API")?;
    if !output.status.success() { return Err("Horizon did not provide its documented Launch API.".into()); }
    serde_json::from_slice(&output.stdout).context("Horizon Launch API did not return valid JSON")
}

// Local preview launchers pin the Votex Engine executable at build time.
// The signed upstream preparer remains responsible for the Minecraft resources.
fn preview_client() -> Result<Option<PathBuf>> {
    let Some(expected) = option_env!("VOTEX_ENGINE_PREVIEW_SHA256") else {
        return Ok(None);
    };
    let launcher = std::env::current_exe().context("Resolve Votex executable")?;
    let folder = launcher.parent().ok_or("Missing Votex executable directory")?;
    let client = storage::relative(folder, "votex-engine.exe")?;
    let bytes = read_limited(&client, MAX_FILE_SIZE)?;
    if !network::hash(&bytes, "sha256")?.eq_ignore_ascii_case(expected) {
        return Err("Die Prüfsumme der lokalen Votex Engine stimmt nicht.".into());
    }
    validate_pe(&bytes)?;
    Ok(Some(client))
}

fn api_has_option(api: &Value, option: &str) -> bool {
    api["options"].as_array().is_some_and(|options| options.iter().any(|item| item["name"] == option))
}

async fn prepare_resources(
    store: &Store,
    app: &tauri::AppHandle,
    profile: &Profile,
    prepare_exe: &Path,
    runtime_dir: &Path,
    release_version: &str,
    api: &Value,
) -> Result<PathBuf> {
    if api["minecraft"] != HORIZON_VERSION {
        return Err(format!("This Horizon build supports Minecraft {}, while this profile requires {HORIZON_VERSION}.", api["minecraft"].as_str().unwrap_or("an unknown version")));
    }
    let java_major = api["java_major"].as_u64().ok_or("Horizon did not declare its required Java version")?;
    for option in ["--prepare", "--output", "--root", "--server-jar", "--version-json", "--client-jar", "--libraries-dir", "--assets-dir", "--java-home", "--complete", "--progress"] {
        if !api_has_option(api, option) { return Err(format!("This Horizon preparer does not support {option}; update the signed runtime.")); }
    }
    let mut vanilla_profile = profile.clone();
    vanilla_profile.engine = GameEngine::Java;
    vanilla_profile.minecraft = HORIZON_VERSION.into();
    vanilla_profile.loader = "vanilla".into();
    vanilla_profile.loader_version.clear();
    emit(app, "installing", "Prüfe und bereite Minecraft 26.3 aus den Originaldateien vor …", 0, 0);
    let installation = minecraft::install(store, &vanilla_profile, app).await?;
    let game_version_java = installation.vanilla["javaVersion"]["majorVersion"].as_u64().unwrap_or(0);
    if game_version_java != java_major {
        return Err(format!("Horizon requires Java {java_major}, but Minecraft 26.3 metadata specifies Java {game_version_java}."));
    }
    let version_json = store.root.join(format!("cache/minecraft-{HORIZON_VERSION}.json"));
    let client_jar = store.root.join(format!("cache/versions/{HORIZON_VERSION}.jar"));
    let libraries_dir = store.root.join("cache/libraries");
    let assets_dir = store.root.join("cache/assets");
    let server = &installation.vanilla["downloads"]["server"];
    let server_url = server["url"].as_str().ok_or("Mojang's version metadata has no official server jar URL")?;
    let server_hash = server["sha1"].as_str().filter(|hash| hash.len() == 40 && hash.bytes().all(|byte| byte.is_ascii_hexdigit())).ok_or("Mojang's server jar metadata has no SHA-1")?;
    let server_size = server["size"].as_u64().filter(|size| *size > 0 && *size <= 256 * 1024 * 1024).ok_or("Mojang's server jar exceeds its download size limit")?;
    let server_jar = store.root.join(format!("cache/horizon/minecraft-{HORIZON_VERSION}-server.jar"));
    let client = network::client()?;
    network::download_limited(&client, server_url, &server_jar, Some(("sha1", server_hash)), server_size).await?;
    if fs::metadata(&server_jar).context("Read Minecraft server jar")?.len() != server_size { return Err("Minecraft server jar size did not match Mojang's metadata".into()); }
    let java_exe = crate::runtime::resolve(store, &profile.java, java_major).await?;
    let java_home = java_exe.parent().and_then(Path::parent).ok_or("Could not find Horizon Java home")?;
    let resources = storage::relative(
        &store.root,
        &format!("native-data/horizon/prepared/{}/{}", profile.id, release_version),
    )?;
    fs::create_dir_all(&resources).context("Create separate Horizon prepared-resource folder")?;
    let mut command = Command::new(prepare_exe);
    command.current_dir(runtime_dir)
        .arg("--prepare")
        .arg("--root").arg(storage::physical_path(runtime_dir)?)
        .arg("--server-jar").arg(storage::physical_path(&server_jar)?)
        .arg("--version-json").arg(storage::physical_path(&version_json)?)
        .arg("--client-jar").arg(storage::physical_path(&client_jar)?)
        .arg("--libraries-dir").arg(storage::physical_path(&libraries_dir)?)
        .arg("--assets-dir").arg(storage::physical_path(&assets_dir)?)
        .arg("--java-home").arg(storage::physical_path(java_home)?)
        .arg("--output").arg(storage::physical_path(&resources)?)
        .arg("--complete").arg("--progress").arg("json")
        .stdin(Stdio::null()).stdout(Stdio::piped()).stderr(Stdio::piped());
    windows_hidden(&mut command);
    let mut child = command.spawn().context("Start Horizon resource preparation")?;
    let stdout = child.stdout.take().ok_or("Horizon preparer output is unavailable")?;
    let stderr = child.stderr.take().ok_or("Horizon preparer errors are unavailable")?;
    let progress_app = app.clone();
    let progress = tokio::spawn(async move {
        let mut lines = BufReader::new(stdout).lines();
        while let Ok(Some(line)) = lines.next_line().await {
            if let Ok(event) = serde_json::from_str::<Value>(&line) {
                if let Some(message) = event["message"].as_str() {
                    emit(&progress_app, "installing", message, 0, 0);
                } else if let Some(fraction) = event["fraction"].as_f64() {
                    emit(&progress_app, "installing", "Horizon bereitet Spielressourcen vor …", (fraction.clamp(0.0, 1.0) * 1000.0) as u64, 1000);
                } else if let Some(error) = event["error"].as_str() {
                    emit(&progress_app, "installing", &format!("Horizon: {error}"), 0, 0);
                }
            }
        }
    });
    let errors = tokio::spawn(async move {
        let mut lines = BufReader::new(stderr).lines();
        let mut last = String::new();
        while let Ok(Some(line)) = lines.next_line().await { last = line.chars().take(400).collect(); }
        last
    });
    let status = child.wait().await.context("Wait for Horizon resource preparation")?;
    let _ = progress.await;
    let stderr = errors.await.unwrap_or_default();
    if !status.success() {
        return Err(if stderr.is_empty() {
            format!("Horizon konnte die Spielressourcen nicht vorbereiten (Exit-Code {:?}).", status.code())
        } else {
            format!("Horizon konnte die Spielressourcen nicht vorbereiten: {}", stderr)
        });
    }
    Ok(resources)
}

pub async fn launch(
    store: Arc<Store>,
    profile: Profile,
    offline_name: Option<String>,
    server_address: Option<String>,
    app: tauri::AppHandle,
    preparation: crate::Busy,
) -> Result<()> {
    if profile.engine != GameEngine::Horizon || profile.minecraft != HORIZON_VERSION || profile.loader != "vanilla" {
        return Err("Dieses Profil ist nicht als Horizon für Minecraft 26.3 eingerichtet.".into());
    }
    let (identity, offline_name) = match offline_name {
        Some(name) => (None, Some(minecraft::offline_player(&name)?.0)),
        None => (Some(auth::identity(&store).await?), None),
    };
    let mut status = status_inner(&store)?;
    if status["installed"] != true {
        status = install(&store, &app).await?;
    }
    let installed_version = status["version"].as_str().ok_or("Horizon runtime version is missing")?.to_owned();
    let record = storage::read_json(&receipt_path(&store))?;
    let folder = record["runtimeFolder"].as_str().ok_or("Horizon receipt has no runtime folder")?;
    let folder = runtime_path(&store, folder)?;
    let client_exe = match preview_client()? {
        Some(client) => client,
        None => storage::relative(&folder, record["clientFile"].as_str().ok_or("Horizon receipt has no client path")?)?,
    };
    let prepare_exe = storage::relative(&folder, record["prepareFile"].as_str().ok_or("Horizon receipt has no preparer path")?)?;
    let client_dir = client_exe.parent().ok_or("Horizon client has no release folder")?;
    emit(&app, "starting", "[VOTEX ENGINE] Prüfe die Startschnittstelle …", 0, 0);
    let client_api = query_api(&client_exe, client_dir).await?;
    if client_api["launch_api"].as_str().is_none_or(|version| !version.starts_with("0.1."))
        || !api_has_option(&client_api, "--resources")
        || !api_has_option(&client_api, "--game-dir")
        || !api_has_option(&client_api, "--home")
        || (identity.is_some() && !api_has_option(&client_api, "--session-stdin"))
    {
        return Err("Diese Horizon-Version bietet nicht die unterstützte Votex-Start-API. Bitte aktualisiere den Rust-Client.".into());
    }
    let bundle_dir = prepare_exe.parent().ok_or("Horizon preparer has no release folder")?;
    emit(&app, "installing", "[VOTEX ENGINE] Lade und prüfe Spielressourcen …", 0, 0);
    let prepare_api = query_api(&prepare_exe, bundle_dir).await?;
    let resources = prepare_resources(
        &store,
        &app,
        &profile,
        &prepare_exe,
        bundle_dir,
        &installed_version,
        &prepare_api,
    ).await?;
    let profile_root = storage::relative(&store.root, &format!("native-data/horizon/profiles/{}", profile.id))?;
    let game_dir = profile_root.join("game");
    let home_dir = profile_root.join("home");
    fs::create_dir_all(&game_dir).context("Create separate Horizon profile data")?;
    fs::create_dir_all(&home_dir).context("Create separate Horizon logs folder")?;
    let mut command = Command::new(&client_exe);
    command.arg("--resources").arg(storage::physical_path(&resources)?)
        .arg("--game-dir").arg(storage::physical_path(&game_dir)?)
        .arg("--home").arg(storage::physical_path(&home_dir)?)
        .current_dir(storage::physical_path(client_dir)?)
        .stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null());
    if let Some(name) = offline_name {
        command.arg("--name").arg(name);
    } else {
        command.arg("--session-stdin").stdin(Stdio::piped());
    }
    if let Some(address) = server_address.as_deref() {
        if !api_has_option(&client_api, "--quickPlayMultiplayer") {
            return Err("Diese Horizon-Version unterstützt keinen direkten Serverbeitritt.".into());
        }
        command.arg("--quickPlayMultiplayer").arg(play_history::server_address(address)?);
    }
    windows_hidden(&mut command);
    emit(&app, "starting", "[VOTEX ENGINE] Starte Minecraft …", 0, 0);
    let mut child = command.spawn().map_err(|error| format!("Horizon konnte nicht gestartet werden: {error}. Die Windows-Sicherheitsmeldung muss über den normalen Administrator-/Publisher-Freigabeweg geprüft werden."))?;
    if let Some(identity) = identity {
    let mut input = child.stdin.take().ok_or("Horizon session input is unavailable")?;
    let session = json!({
        "name": identity.account.name,
        "uuid": identity.account.id,
        "access_token": identity.token,
        "user_type": "msa",
        "xuid": identity.xuid,
    });
    if let Err(error) = input.write_all(&serde_json::to_vec(&session).context("Serialize Horizon session")?).await {
        let _ = child.kill().await;
        return Err(format!("Pass Microsoft session to Horizon through standard input: {error}"));
    }
    drop(input);
    }
    if let Err(error) = play_history::record_launched(&store, &profile.id) {
        eprintln!("Could not save last Horizon profile: {error}");
    }
    store.running_games.fetch_add(1, Ordering::Relaxed);
    struct RunningGame(Arc<Store>);
    impl Drop for RunningGame { fn drop(&mut self) { self.0.running_games.fetch_sub(1, Ordering::Relaxed); } }
    let running = RunningGame(store.clone());
    let pid = child.id().unwrap_or_default();
    minecraft::event(&app, "running", &format!("[VOTEX ENGINE] Spielprozess {pid} läuft."), 0, 0);
    drop(preparation);
    if store.state().map(|state| state["minimizeOnLaunch"] == true).unwrap_or(false) {
        if let Some(window) = app.get_webview_window("main") { let _ = window.minimize(); }
    }
    let started = std::time::Instant::now();
    let status = loop {
        if let Some(status) = child.try_wait().context("Monitor Horizon process")? { break status; }
        tokio::time::sleep(Duration::from_secs(1)).await;
    };
    drop(running);
    if let Some(window) = app.get_webview_window("main") {
        if window.is_minimized().unwrap_or(false) { let _ = window.unminimize(); let _ = window.set_focus(); }
    }
    if status.success() {
        minecraft::event(&app, "exited", &format!("Horizon closed normally after {} seconds.", started.elapsed().as_secs()), 0, 0);
        Ok(())
    } else {
        Err(format!("Horizon exited with code {:?}. Check its per-profile logs at {}.", status.code(), home_dir.join("logs").display()))
    }
}
