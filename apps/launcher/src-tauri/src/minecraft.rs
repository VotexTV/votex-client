use crate::{
    auth, network, recovery,
    storage::{atomic, read_json, relative, Context, Profile, Result, Store},
};
use futures_util::{stream, StreamExt, TryStreamExt};
use md5::{Digest, Md5};
use serde_json::{json, Value};
use std::{
    collections::{BTreeMap, HashSet},
    path::{Path, PathBuf},
    process::Stdio,
    sync::{
        atomic::{AtomicUsize, Ordering},
        Arc,
    },
    time::{Duration, Instant},
};
use tauri::{Emitter, Manager};
use tokio::{
    io::{AsyncBufReadExt, BufReader},
    process::Command,
};
pub const MANIFEST: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
pub fn event(app: &tauri::AppHandle, phase: &str, message: &str, completed: usize, total: usize) {
    event_optional(Some(app), phase, message, completed, total);
}
pub(crate) fn event_optional(
    app: Option<&tauri::AppHandle>,
    phase: &str,
    message: &str,
    completed: usize,
    total: usize,
) {
    if let Some(app) = app {
        let _ = app.emit(
            "votex:job",
            json!({"phase":phase,"message":message,"completed":completed,"total":total}),
        );
    }
}
pub async fn versions(store: &Store) -> Result<Value> {
    let path = store.root.join("cache/versions.json");
    let c = network::client()?;
    match network::json(&c, MANIFEST).await {
        Ok(v) => {
            atomic(&path, &serde_json::to_vec(&v).context("Cache versions")?)?;
            Ok(v)
        }
        Err(e) => {
            if path.exists() {
                read_json(&path)
            } else {
                Err(e)
            }
        }
    }
}
fn str_field<'a>(v: &'a Value, k: &str) -> Result<&'a str> {
    v[k].as_str().ok_or(format!("Metadata missing {k}"))
}
pub(crate) fn maven(name: &str) -> Result<String> {
    let parts: Vec<_> = name.split(':').collect();
    if !(3..=4).contains(&parts.len())
        || parts.iter().any(|s| {
            s.is_empty()
                || !s
                    .bytes()
                    .all(|b| b.is_ascii_alphanumeric() || b"._-+".contains(&b))
        })
    {
        return Err("Invalid Maven coordinates".into());
    }
    let (g, a, v) = (parts[0].replace('.', "/"), parts[1], parts[2]);
    Ok(format!(
        "{g}/{a}/{v}/{a}-{v}{}.jar",
        if parts.len() == 4 {
            format!("-{}", parts[3])
        } else {
            String::new()
        }
    ))
}
fn library_key(name: &str) -> String {
    let parts: Vec<_> = name.split(':').collect();
    format!(
        "{}:{}:{}",
        parts.first().unwrap_or(&""),
        parts.get(1).unwrap_or(&""),
        parts.get(3).unwrap_or(&"")
    )
}
pub fn rules(value: &Value) -> bool {
    let Some(items) = value.get("rules").and_then(Value::as_array) else {
        return true;
    };
    let mut allowed = false;
    for r in items {
        if r.get("features").is_some() {
            continue;
        }
        let os = &r["os"];
        if let Some(n) = os["name"].as_str() {
            if n != "windows" {
                continue;
            }
        }
        if let Some(a) = os["arch"].as_str() {
            if a != "x86_64" && a != "amd64" {
                continue;
            }
        }
        if os.get("version").is_some() {
            continue;
        }
        allowed = r["action"] == "allow";
    }
    allowed
}
pub fn arguments(values: &Value, replacements: &BTreeMap<String, String>) -> Result<Vec<String>> {
    let mut result = vec![];
    if values.is_null() {
        return Ok(result);
    }
    for item in values
        .as_array()
        .ok_or("Unsupported legacy Minecraft arguments (modern versions required)")?
    {
        let selected = if item.is_string() {
            item
        } else if rules(item) {
            &item["value"]
        } else {
            continue;
        };
        let texts: Vec<&str> = if let Some(v) = selected.as_str() {
            vec![v]
        } else {
            selected
                .as_array()
                .ok_or("Invalid launch argument")?
                .iter()
                .map(|v| v.as_str().ok_or("Invalid argument value"))
                .collect::<std::result::Result<_, _>>()?
        };
        for text in texts {
            let mut arg = text.to_string();
            for (k, v) in replacements {
                arg = arg.replace(&format!("${{{k}}}"), v)
            }
            if arg.contains("${") {
                return Err(format!(
                    "Unsupported Minecraft argument placeholder: {text}"
                ));
            }
            result.push(arg);
        }
    }
    Ok(result)
}
#[derive(Clone)]
struct Artifact {
    url: String,
    path: PathBuf,
    hash: Option<String>,
}
pub struct Installation {
    pub vanilla: Value,
    pub fabric: Option<Value>,
    pub classpath: Vec<PathBuf>,
    pub loader_version: String,
}
pub async fn install(
    store: &Store,
    profile: &Profile,
    app: &tauri::AppHandle,
) -> Result<Installation> {
    install_internal(store, profile, Some(app), true, None).await
}
async fn install_internal(
    store: &Store,
    profile: &Profile,
    app: Option<&tauri::AppHandle>,
    prepare: bool,
    bundled_core: Option<&[u8]>,
) -> Result<Installation> {
    event_optional(
        app,
        "installing",
        "Reading official Minecraft metadata",
        0,
        0,
    );
    let c = network::client()?;
    let manifest = versions(store).await?;
    let entry = manifest["versions"]
        .as_array()
        .ok_or("Invalid version manifest")?
        .iter()
        .find(|v| v["id"] == profile.minecraft)
        .ok_or("Unknown Minecraft version")?;
    let metadata = store
        .root
        .join("cache")
        .join(format!("minecraft-{}.json", profile.minecraft));
    network::download(
        &c,
        str_field(entry, "url")?,
        &metadata,
        Some(("sha1", str_field(entry, "sha1")?)),
    )
    .await?;
    let vanilla = read_json(&metadata)?;
    event_optional(
        app,
        "installing",
        "Passende portable Java-Laufzeit wird geprüft und bei Bedarf installiert",
        0,
        0,
    );
    crate::runtime::resolve(
        store,
        &profile.java,
        vanilla["javaVersion"]["majorVersion"].as_u64().unwrap_or(8),
    )
    .await?;
    let mut fabric = None;
    let mut loader_version = String::new();
    if matches!(profile.loader.as_str(), "fabric" | "quilt") {
        let (data, v) = crate::loaders::lightweight(profile).await?;
        fabric = Some(data);
        loader_version = v;
    } else if matches!(profile.loader.as_str(), "forge" | "neoforge") {
        let client = &vanilla["downloads"]["client"];
        let path = store
            .root
            .join("cache/versions")
            .join(format!("{}.jar", profile.minecraft));
        network::download(
            &c,
            str_field(client, "url")?,
            &path,
            Some(("sha1", str_field(client, "sha1")?)),
        )
        .await?;
        let (data, v) = crate::loaders::installer(store, profile, &vanilla, &path).await?;
        fabric = Some(data);
        loader_version = v;
    }
    let mut artifacts = vec![];
    let mut classpath = vec![];
    let mut natives = vec![];
    let mut seen = HashSet::new();
    // Fabric overrides libraries with the same group/artifact. Process those first.
    let mut libraries = vec![];
    if let Some(f) = &fabric {
        libraries.extend(
            f["libraries"]
                .as_array()
                .ok_or("Invalid Fabric libraries")?
                .iter(),
        )
    }
    libraries.extend(
        vanilla["libraries"]
            .as_array()
            .ok_or("Invalid Minecraft libraries")?
            .iter(),
    );
    for library in libraries {
        if !rules(library) {
            continue;
        }
        let name = str_field(library, "name")?;
        let key = library_key(name);
        if !seen.insert(key) {
            continue;
        }
        let artifact = &library["downloads"]["artifact"];
        if !artifact.is_null() {
            let path = relative(
                &store.root.join("cache/libraries"),
                str_field(artifact, "path")?,
            )?;
            classpath.push(path.clone());
            artifacts.push(Artifact {
                url: str_field(artifact, "url")?.into(),
                path,
                hash: Some(str_field(artifact, "sha1")?.into()),
            });
        } else {
            let part = maven(name)?;
            let base = library["url"]
                .as_str()
                .unwrap_or("https://libraries.minecraft.net/");
            let url = format!("{}{part}", base.trim_end_matches('/').to_string() + "/");
            let path = relative(&store.root.join("cache/libraries"), &part)?;
            let sum = if let Some(s) = library["sha1"].as_str() {
                s.to_string()
            } else {
                network::allowed(&url)?;
                let r = c
                    .get(format!("{url}.sha1"))
                    .send()
                    .await
                    .context("Fabric checksum lookup")?
                    .error_for_status()
                    .context("Fabric checksum missing")?
                    .text()
                    .await
                    .context("Fabric checksum")?;
                r.split_whitespace().next().unwrap_or("").to_string()
            };
            if sum.len() != 40 || !sum.bytes().all(|b| b.is_ascii_hexdigit()) {
                return Err("Invalid Fabric library checksum".into());
            }
            classpath.push(path.clone());
            artifacts.push(Artifact {
                url,
                path,
                hash: Some(sum),
            });
        }
        if let Some(classifier) = library["natives"]["windows"].as_str() {
            let classifier = classifier.replace("${arch}", "64");
            let native = &library["downloads"]["classifiers"][classifier];
            let path = relative(
                &store.root.join("cache/libraries"),
                str_field(native, "path")?,
            )?;
            natives.push(path.clone());
            artifacts.push(Artifact {
                url: str_field(native, "url")?.into(),
                path,
                hash: Some(str_field(native, "sha1")?.into()),
            });
        }
    }
    let client = &vanilla["downloads"]["client"];
    let client_path = store
        .root
        .join("cache/versions")
        .join(format!("{}.jar", profile.minecraft));
    classpath.push(client_path.clone());
    artifacts.push(Artifact {
        url: str_field(client, "url")?.into(),
        path: client_path,
        hash: Some(str_field(client, "sha1")?.into()),
    });
    let index = &vanilla["assetIndex"];
    let index_path = relative(
        &store.root.join("cache/assets/indexes"),
        &format!("{}.json", str_field(index, "id")?),
    )?;
    network::download(
        &c,
        str_field(index, "url")?,
        &index_path,
        Some(("sha1", str_field(index, "sha1")?)),
    )
    .await?;
    let assets = read_json(&index_path)?;
    for object in assets["objects"]
        .as_object()
        .ok_or("Invalid asset index")?
        .values()
    {
        let sum = str_field(object, "hash")?;
        if sum.len() != 40 || !sum.bytes().all(|b| b.is_ascii_hexdigit()) {
            return Err("Invalid asset checksum".into());
        }
        let rel = format!("{}/{}", &sum[..2], sum);
        artifacts.push(Artifact {
            url: format!("https://resources.download.minecraft.net/{rel}"),
            path: store.root.join("cache/assets/objects").join(rel),
            hash: Some(sum.into()),
        });
    }
    if let Some(logging) = vanilla["logging"]["client"]["file"].as_object() {
        let id = logging
            .get("id")
            .and_then(Value::as_str)
            .ok_or("Invalid logging metadata")?;
        let l = &vanilla["logging"]["client"]["file"];
        artifacts.push(Artifact {
            url: str_field(l, "url")?.into(),
            path: relative(&store.root.join("cache/logging"), id)?,
            hash: Some(str_field(l, "sha1")?.into()),
        });
    }
    // Several named assets can share one content hash. Never race writes to a shared .part file.
    let mut destinations = HashSet::new();
    artifacts.retain(|a| destinations.insert(a.path.clone()));
    let total = artifacts.len();
    let done = AtomicUsize::new(0);
    stream::iter(artifacts.into_iter().map(|a| {
        let c = &c;
        let done = &done;
        async move {
            network::download(
                c,
                &a.url,
                &a.path,
                a.hash.as_ref().map(|s| ("sha1", s.as_str())),
            )
            .await?;
            let n = done.fetch_add(1, Ordering::Relaxed) + 1;
            if n % 20 == 0 || n == total {
                event_optional(app, "installing", "Verified game files", n, total);
            }
            Ok::<(), String>(())
        }
    }))
    .buffer_unordered(8)
    .try_collect::<Vec<_>>()
    .await?;
    let instance = store.instance(&profile.id)?;
    if assets["virtual"] == true || assets["map_to_resources"] == true {
        let virtual_root = store
            .root
            .join("cache/assets/virtual")
            .join(str_field(index, "id")?);
        for (name, object) in assets["objects"].as_object().unwrap() {
            let sum = str_field(object, "hash")?;
            let bytes = std::fs::read(
                store
                    .root
                    .join("cache/assets/objects")
                    .join(&sum[..2])
                    .join(sum),
            )
            .context("Read verified legacy asset")?;
            if assets["virtual"] == true {
                atomic(&relative(&virtual_root, name)?, &bytes)?;
            }
            if assets["map_to_resources"] == true {
                atomic(&relative(&instance.join("resources"), name)?, &bytes)?;
            }
        }
    }
    let native_dir = relative(&instance, "natives")?;
    std::fs::create_dir_all(&native_dir).context("Create native directory")?;
    for path in natives {
        let file = std::fs::File::open(path).context("Open native archive")?;
        let mut zip = zip::ZipArchive::new(file).context("Invalid native archive")?;
        for i in 0..zip.len() {
            let mut item = zip.by_index(i).context("Read native file")?;
            let Some(name) = item.enclosed_name() else {
                return Err("Unsafe native archive path".into());
            };
            let name = name.to_string_lossy().replace('\\', "/");
            if !name.ends_with(".dll") {
                continue;
            }
            let target = relative(&native_dir, &name)?;
            let mut bytes = vec![];
            std::io::Read::read_to_end(&mut item, &mut bytes).context("Read native library")?;
            atomic(&target, &bytes)?;
        }
    }
    for directory in ["mods", "config/votex"] {
        std::fs::create_dir_all(relative(&instance, directory)?).context("Initialize instance")?;
    }
    atomic(&instance.join("installation.json"),&serde_json::to_vec(&json!({"minecraft":profile.minecraft,"loader":profile.loader,"loaderVersion":loader_version,"verifiedAt":chrono::Utc::now().to_rfc3339()})).context("Installation receipt")?)?;
    if prepare {
        crate::prepare::profile_with_progress(store, &profile.id, app, bundled_core).await?;
    }
    event_optional(app, "installed", "Installation verified", total, total);
    Ok(Installation {
        vanilla,
        fabric,
        classpath,
        loader_version,
    })
}
pub async fn java(path: &str) -> Result<Value> {
    let chosen = if path.is_empty() { "java" } else { path };
    if !path.is_empty() {
        if !Path::new(path).is_absolute() || path.starts_with("\\\\") || path.starts_with("//") {
            return Err(
                "Choose an absolute local Java path; network runtimes are not supported".into(),
            );
        }
        let file = Path::new(path)
            .file_name()
            .and_then(|s| s.to_str())
            .unwrap_or("");
        if !matches!(
            file.to_lowercase().as_str(),
            "java.exe" | "javaw.exe" | "java"
        ) {
            return Err("Choose a Java runtime executable (java.exe)".into());
        }
    }
    let mut command = Command::new(chosen);
    #[cfg(windows)]
    command.creation_flags(0x08000000);
    let output =
        command.arg("-version").output().await.context(
            "INVALID_JAVA_RUNTIME: Java not found. Set a Java executable in Performance.",
        )?;
    if !output.status.success() {
        return Err("Java runtime could not run".into());
    }
    let version = String::from_utf8_lossy(&output.stderr).to_string();
    let number = version
        .split('"')
        .nth(1)
        .and_then(|v| {
            if let Some(v) = v.strip_prefix("1.") {
                v.split('.').next()
            } else {
                v.split('.').next()
            }
        })
        .and_then(|n| n.parse::<u32>().ok())
        .ok_or("Cannot determine Java version")?;
    Ok(json!({"path":chosen,"major":number,"version":version}))
}
fn redact(s: &str, token: &str) -> String {
    if token.is_empty() {
        s.to_string()
    } else {
        s.replace(token, "[REDACTED]")
    }
}
pub(crate) fn offline_player(name: &str) -> Result<(String, String)> {
    if !(1..=16).contains(&name.len())
        || !name
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || byte == b'_')
    {
        return Err(
            "Offline-Namen müssen 1–16 Zeichen haben und nur Buchstaben, Zahlen oder _ enthalten."
                .into(),
        );
    }
    let digest = Md5::digest(format!("OfflinePlayer:{name}").as_bytes());
    let mut bytes = [0u8; 16];
    bytes.copy_from_slice(&digest);
    bytes[6] = (bytes[6] & 0x0f) | 0x30;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    Ok((
        name.to_owned(),
        uuid::Uuid::from_bytes(bytes).simple().to_string(),
    ))
}
pub async fn launch(
    store: Arc<Store>,
    profile: Profile,
    app: tauri::AppHandle,
    safe: bool,
    offline_name: Option<String>,
    server_address: Option<String>,
    preparation: crate::Busy,
) -> Result<()> {
    launch_inner(
        store,
        profile,
        Some(&app),
        safe,
        offline_name,
        server_address,
        None,
        false,
        Some(preparation),
    )
    .await
}

async fn launch_inner(
    store: Arc<Store>,
    profile: Profile,
    app: Option<&tauri::AppHandle>,
    safe: bool,
    offline_name: Option<String>,
    server_address: Option<String>,
    bundled_core: Option<&[u8]>,
    startup_smoke: bool,
    preparation: Option<crate::Busy>,
) -> Result<()> {
    let (player_name, player_id, token, xuid, user_type) = if let Some(name) = offline_name {
        let (name, id) = offline_player(&name)?;
        event_optional(
            app,
            "starting",
            "Starting with a local offline profile",
            0,
            0,
        );
        (name, id, String::new(), String::new(), "legacy")
    } else {
        // Refresh and verify entitlement immediately before launch; never accept frontend tokens.
        event_optional(app, "authenticating", "Verifying Minecraft account", 0, 0);
        let identity = auth::identity(&store).await?;
        (
            identity.account.name,
            identity.account.id,
            identity.token,
            identity.xuid,
            "msa",
        )
    };
    let _before_launch = recovery::hold(&store, &profile.id, "automatic")?;
    if !safe {
        crate::pending_addons::apply(&store.instance(&profile.id)?)?;
    }
    let installation = install_internal(&store, &profile, app, !safe, bundled_core).await?;
    let required = installation.vanilla["javaVersion"]["majorVersion"]
        .as_u64()
        .unwrap_or(8);
    event_optional(
        app,
        "installing",
        "Passende Java-Laufzeit wird geprüft und bei Bedarf kostenlos geladen",
        0,
        0,
    );
    let runtime = crate::runtime::resolve(&store, &profile.java, required).await?;
    let java_info = java(&runtime.to_string_lossy()).await?;
    if java_info["major"].as_u64().unwrap_or(0) != required {
        return Err(format!("INVALID_JAVA_RUNTIME: This Minecraft version requires Java {required}. Select that major version in Performance."));
    }
    let mut selected = profile.clone();
    selected.loader_version = installation.loader_version.clone();
    {
        let _g = store.lock.lock().map_err(|_| "Profile lock failed")?;
        let mut state = store.state()?;
        let p = state["profiles"]
            .as_array_mut()
            .unwrap()
            .iter_mut()
            .find(|p| p["id"] == profile.id)
            .ok_or("Profile no longer exists")?;
        *p = serde_json::to_value(&selected).context("Save selected Fabric loader")?;
        atomic(
            &store.root.join("config/launcher.json"),
            &serde_json::to_vec_pretty(&state).context("Save profile")?,
        )?;
    }
    let normal = store.instance(&profile.id)?;
    let game_dir = if safe {
        normal
            .join("safe-sessions")
            .join(uuid::Uuid::new_v4().to_string())
    } else {
        normal.clone()
    };
    std::fs::create_dir_all(&game_dir).context("Create game directory")?;
    crate::game_options::apply(&game_dir, &profile)?;
    // Safe Mode uses an isolated directory: normal worlds, configs and mods are untouched.
    if safe {
        event_optional(
            app,
            "starting",
            "Safe Mode: isolated session, no external mods or normal worlds",
            0,
            0,
        );
    }
    let game_dir = crate::storage::physical_path(&game_dir)?;
    let runtime = crate::storage::physical_path(&runtime)?;
    let classpath = installation.classpath.iter().map(|path| crate::storage::physical_path(path))
        .collect::<Result<Vec<_>>>()?;
    let nonce = uuid::Uuid::new_v4().to_string();
    let mut map = BTreeMap::new();
    for (k, v) in [
        ("auth_player_name", player_name.clone()),
        (
            "version_name",
            installation
                .fabric
                .as_ref()
                .and_then(|f| f["id"].as_str())
                .unwrap_or(&profile.minecraft)
                .into(),
        ),
        ("game_directory", game_dir.to_string_lossy().into_owned()),
        (
            "assets_root",
            store
                .root
                .join("cache/assets")
                .to_string_lossy()
                .into_owned(),
        ),
        (
            "assets_index_name",
            str_field(&installation.vanilla["assetIndex"], "id")?.into(),
        ),
        ("auth_uuid", player_id.clone()),
        (
            "auth_access_token",
            if token.is_empty() {
                "0".into()
            } else {
                token.clone()
            },
        ),
        (
            "auth_session",
            format!(
                "token:{}:{}",
                if token.is_empty() { "0" } else { &token },
                player_id
            ),
        ),
        ("user_properties", "{}".into()),
        (
            "game_assets",
            store
                .root
                .join("cache/assets/virtual")
                .join(
                    installation.vanilla["assetIndex"]["id"]
                        .as_str()
                        .unwrap_or("legacy"),
                )
                .to_string_lossy()
                .into_owned(),
        ),
        ("auth_xuid", xuid),
        (
            "clientid",
            store.state()?["clientId"].as_str().unwrap_or("").into(),
        ),
        ("user_type", user_type.into()),
        ("version_type", "release".into()),
        (
            "natives_directory",
            normal.join("natives").to_string_lossy().into_owned(),
        ),
        ("launcher_name", "Votex".into()),
        ("launcher_version", "0.4.1-rc.1".into()),
        (
            "classpath",
            std::env::join_paths(&classpath)
                .context("Invalid Java classpath")?
                .to_string_lossy()
                .into_owned(),
        ),
        (
            "library_directory",
            store
                .root
                .join("cache/libraries")
                .to_string_lossy()
                .into_owned(),
        ),
        ("classpath_separator", ";".into()),
    ] {
        map.insert(k.into(), v);
    }
    let mut args = vec![
        format!("-Xmx{}M", profile.memory),
        "-Xms512M".into(),
        format!("-Dvotex.launchNonce={nonce}"),
        format!("-Dvotex.profileId={}", profile.id),
    ];
    if installation.vanilla["arguments"]["jvm"].is_array() {
        args.extend(arguments(&installation.vanilla["arguments"]["jvm"], &map)?);
    } else {
        args.push(format!("-Djava.library.path={}", map["natives_directory"]));
        args.push("-cp".into());
        args.push(map["classpath"].clone());
    }
    if let Some(logging) = installation.vanilla["logging"]["client"]["argument"].as_str() {
        let path = relative(
            &store.root.join("cache/logging"),
            str_field(&installation.vanilla["logging"]["client"]["file"], "id")?,
        )?;
        args.push(logging.replace("${path}", &path.to_string_lossy()));
    }
    if let Some(f) = &installation.fabric {
        args.extend(arguments(&f["arguments"]["jvm"], &map)?);
    }
    args.push(
        installation
            .fabric
            .as_ref()
            .and_then(|f| f["mainClass"].as_str())
            .unwrap_or(str_field(&installation.vanilla, "mainClass")?)
            .into(),
    );
    if let Some(legacy) = installation
        .fabric
        .as_ref()
        .and_then(|f| f["minecraftArguments"].as_str())
        .or(installation.vanilla["minecraftArguments"].as_str())
    {
        let values = json!(legacy.split_whitespace().collect::<Vec<_>>());
        args.extend(arguments(&values, &map)?);
    } else {
        args.extend(arguments(&installation.vanilla["arguments"]["game"], &map)?);
    }
    if let Some(f) = &installation.fabric {
        args.extend(arguments(&f["arguments"]["game"], &map)?);
    }
    args.extend(crate::play_history::direct_join_arguments(
        &installation.vanilla,
        server_address.as_deref(),
    )?);
    let curseforge_key = crate::curseforge::game_key();
    let minimized_for_game = app.is_some() && store.state()?["minimizeOnLaunch"] == true;
    let log_path = store.root.join("logs").join(format!("{}-latest.log", profile.id));
    atomic(&log_path, b"Votex launch output (command arguments and credentials omitted)\n")?;
    let file = Arc::new(tokio::sync::Mutex::new(
        tokio::fs::OpenOptions::new().append(true).open(&log_path).await.context("Open game log")?,
    ));
    // Confirm the pack that will actually run, after queued changes, dependency
    // installation, loader selection and options have been written.
    let snapshot = recovery::hold(&store, &profile.id, "prepared-launch")?;
    let mut game_command = Command::new(&runtime);
    game_command
        .args(&args)
        .current_dir(&game_dir)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .env_remove("VOTEX_CURSEFORGE_API_KEY")
        .kill_on_drop(false);
    if let Some(key) = &curseforge_key {
        game_command.env("VOTEX_CURSEFORGE_API_KEY", key);
    }
    let mut child = game_command
        .spawn()
        .context("Minecraft process creation failed")?;
    // A successful process creation is a real launch, even if startup later fails.
    // History is optional metadata and must never orphan an already running game.
    if let Err(error) = crate::play_history::record_launched(&store, &profile.id) {
        eprintln!("Could not save last launched profile: {error}");
    }
    struct RunningGame<'a>(&'a std::sync::atomic::AtomicUsize);
    impl Drop for RunningGame<'_> {
        fn drop(&mut self) {
            self.0.fetch_sub(1, Ordering::Relaxed);
        }
    }
    store.running_games.fetch_add(1, Ordering::Relaxed);
    let _running_game = RunningGame(&store.running_games);
    let pid = child.id();
    let size = Arc::new(AtomicUsize::new(0));
    let mut readers = vec![];
    for pipe in [
        child
            .stdout
            .take()
            .map(|s| Box::new(s) as Box<dyn tokio::io::AsyncRead + Unpin + Send>),
        child
            .stderr
            .take()
            .map(|s| Box::new(s) as Box<dyn tokio::io::AsyncRead + Unpin + Send>),
    ] {
        if let Some(pipe) = pipe {
            let token = token.clone();
            let curseforge_key = curseforge_key.clone();
            let file = file.clone();
            let size = size.clone();
            readers.push(tokio::spawn(async move {
                use tokio::io::AsyncWriteExt;
                let mut lines = BufReader::new(pipe).lines();
                while let Ok(Some(line)) = lines.next_line().await {
                    if size.fetch_add(line.len(), Ordering::Relaxed) > 8_388_608 {
                        continue;
                    }
                    let line = if token.is_empty() {
                        line
                    } else {
                        redact(&line, &token)
                    };
                    let line = match &curseforge_key {
                        Some(key) => redact(&line, key),
                        None => line,
                    };
                    let _ = file
                        .lock()
                        .await
                        .write_all(format!("{line}\n").as_bytes())
                        .await;
                }
            }));
        }
    }
    event_optional(
        app,
        "running",
        &format!("Minecraft process {pid:?} is running. Startup is not confirmed yet."),
        0,
        0,
    );
    drop(preparation);
    if minimized_for_game {
        if let Some(app) = app {
            if let Some(w) = app.get_webview_window("main") {
                let _ = w.minimize();
            }
        }
    }
    let started = Instant::now();
    let wall_started = std::time::SystemTime::now();
    let mut ready_at = None;
    let mut confirmed = false;
    let mut smoke_confirmed = false;
    let mut recovery_warning = false;
    let mut options_stamp = crate::game_options::stamp(&game_dir);
    let status = loop {
        if let Some(status) = child.try_wait().context("Monitor Minecraft process")? {
            break status;
        }
        if startup_smoke && started.elapsed() >= Duration::from_secs(240) {
            eprintln!(
                "VOTEX_PACKAGE_STARTUP_TIMEOUT profile={} elapsed_seconds=240",
                profile.id
            );
            child
                .kill()
                .await
                .context("Stop timed-out isolated startup smoke client")?;
            break child
                .wait()
                .await
                .context("Wait for timed-out isolated startup smoke client")?;
        }
        if !safe && !confirmed {
            let ready = game_dir.join("config/votex/startup.json");
            if let Ok(value) = read_json(&ready) {
                if value["nonce"] == nonce
                    && matches!(
                        value["stage"].as_str(),
                        Some("title-screen" | "world-joined")
                    )
                {
                    if ready_at.is_none() {
                        ready_at = Some(Instant::now())
                    }
                }
            }
            let stable_for = ready_at.map(|t: Instant| t.elapsed()).unwrap_or_default();
            if startup_smoke && stable_for >= Duration::from_secs(10) {
                smoke_confirmed = true;
                eprintln!("VOTEX_PACKAGE_STARTUP_PASS profile={} nonce={} stage=title-screen stable_seconds=10", profile.id, nonce);
                child
                    .kill()
                    .await
                    .context("Stop isolated startup smoke client")?;
                break child
                    .wait()
                    .await
                    .context("Wait for isolated startup smoke client")?;
            }
            if !startup_smoke && !recovery_warning && stable_for >= Duration::from_secs(45) {
                match recovery::validate(&store, &profile.id, &snapshot.snapshot.id, "confirmed-working") {
                    Ok(()) => {
                        confirmed = true;

                    }
                    Err(error) => {
                        recovery_warning = true;
                        event_optional(app,"recovery-warning",&format!("Minecraft läuft. Sein Sicherungspunkt konnte nicht bestätigt werden: {error}"),0,0);
                    }
                }
            }
        }
        if !safe && started.elapsed().as_secs() % 2 == 0 {
            let next_stamp = crate::game_options::stamp(&game_dir);
            if next_stamp != options_stamp {
                match crate::game_options::sync(&store, &profile.id) {
                    Ok(true) => {
                        if let Some(app) = app {
                            let _ = app.emit(
                                "votex:game-options",
                                serde_json::json!({"profile":profile.id}),
                            );
                        }
                    }
                    Ok(false) => {}
                    Err(error) => {
                        event_optional(app, "settings-warning", &error, 0, 0);
                    }
                }
                options_stamp = next_stamp;
            }
        }
        tokio::time::sleep(Duration::from_secs(1)).await;
    };
    for reader in readers {
        let _ = reader.await;
    }
    if !safe {
        let _ = crate::game_options::sync(&store, &profile.id);
    }
    if minimized_for_game {
        if let Some(app) = app {
            if let Some(w) = app.get_webview_window("main") {
                if w.is_minimized().unwrap_or(false) {
                    let _ = w.unminimize();
                    let _ = w.set_focus();
                }
            }
        }
    }
    drop(_running_game);
    if smoke_confirmed {
        recovery::validate(&store, &profile.id, &snapshot.snapshot.id, "confirmed-working")?;
        eprintln!(
            "VOTEX_PACKAGE_STARTUP_PASS proof={}",
            game_dir.join("config/votex/startup.json").display()
        );
        return Ok(());
    }
    if status.success() {
        if !confirmed && !safe {
            recovery::validate(&store, &profile.id, &snapshot.snapshot.id, "initial-checks-only")?;
        }
        if store.running_games.load(Ordering::Relaxed) == 0
            && !store.busy.lock().map(|busy| *busy).unwrap_or(true)
        {
            event_optional(
                app,
                "exited",
                &format!(
                    "Minecraft exited normally after {} seconds{}.",
                    started.elapsed().as_secs(),
                    if safe { " (isolated Safe Mode)" } else { "" }
                ),
                0,
                0,
            );
        }
        Ok(())
    } else {
        if !safe {
            recovery::validate(&store, &profile.id, &snapshot.snapshot.id, "failed")?;
        }
        let changes = recovery::latest_working(&store, &profile.id)?
            .map(|s| recovery::compare(&store, &s))
            .transpose()?
            .unwrap_or_default();
        let diagnostics = crate::diagnostics::inspect(&game_dir, &log_path, wall_started, &token);
        if let Some(app) = app {
            let _=app.emit("votex:crash",json!({"profile":profile.id,"exitCode":status.code(),"seconds":started.elapsed().as_secs(),"changes":changes,"hasWorking":recovery::latest_working(&store,&profile.id)?.is_some(),"safe":safe,"diagnostics":diagnostics}));
        }
        Err(format!("Minecraft exited with code {:?}. Review the actual log in Recovery. Recent changes are clues, not proven causes.",status.code()))
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[tokio::test]
    #[ignore = "Explicit isolated end-to-end launch: downloads Minecraft/Fabric/runtime/addons, opens one offline test client, and closes it after the Core confirms the title screen"]
    async fn offline_starter_pack_reaches_native_title_screen() {
        let qa_root = PathBuf::from(
            std::env::var("VOTEX_PACKAGE_STARTUP_QA_ROOT")
                .expect("Set a new isolated QA data folder"),
        );
        assert!(
            !qa_root.exists(),
            "Refusing to reuse or overwrite an existing QA folder: {}",
            qa_root.display()
        );
        std::fs::create_dir_all(&qa_root).unwrap();
        atomic(
            &qa_root.join("QA-ONLY-NO-USER-PROFILES.txt"),
            b"Disposable offline Votex test data. No Microsoft account or user profiles.\n",
        )
        .unwrap();
        let store = Arc::new(Store::new(qa_root.join("store")).unwrap());
        // Optional read-only cache seed from a preceding private attempt. All
        // production downloads still verify their expected publisher hashes.
        if let Some(source) = std::env::var_os("VOTEX_PACKAGE_STARTUP_CACHE") {
            let source = PathBuf::from(source).canonicalize().unwrap();
            for entry in walkdir::WalkDir::new(&source).follow_links(false) {
                let entry = entry.unwrap();
                assert!(!entry.file_type().is_symlink(), "QA cache cannot contain links");
                if !entry.file_type().is_file() { continue; }
                let destination = store.root.join("cache").join(entry.path().strip_prefix(&source).unwrap());
                std::fs::create_dir_all(destination.parent().unwrap()).unwrap();
                std::fs::copy(entry.path(), destination).unwrap();
            }
        }
        let mut state: Value = serde_json::from_slice(include_bytes!(
            "../../../../packages/shared-config/defaults.json"
        ))
        .unwrap();
        state["profiles"][0]["minecraft"] = json!("26.2");
        state["profiles"][0]["loader"] = json!("fabric");
        if let Ok(version) = std::env::var("VOTEX_QA_FABRIC_LOADER") {
            state["profiles"][0]["loaderVersion"] = json!(version);
        }
        state["profiles"][0]["preset"] = json!("performance");
        state["profiles"][0]["memory"] = json!(2048);
        // Vanilla quantizes this saved FPS slider value when it loads options.
        // Observe that real save through the production launch-monitor sync.
        state["profiles"][0]["maxFps"] = json!(143);
        state["profiles"][0]["guiScale"] = json!(2);
        state["profiles"][0]["fov"] = json!(90);
        state["profiles"][0]["gameOptionsRevision"] = json!(1);
        state["profiles"][0]["performanceMods"] = json!(true);
        state["profiles"][0]["shaderEnabled"] = json!(true);
        state["profiles"][0]["starterMods"] = json!({"sodium":true,"iris":true,"modmenu":true,"zoomify":true,"more":true,"extras":["xaeros-minimap","xaeros-world-map","appleskin","3dskinlayers","voicechat","mousetweaks"],"shaders":["complementary-reimagined","bsl-shaders"]});
        state["minimizeOnLaunch"] = json!(false);
        atomic(
            &store.root.join("config/launcher.json"),
            &serde_json::to_vec_pretty(&state).unwrap(),
        )
        .unwrap();
        let core_path = PathBuf::from(
            std::env::var("VOTEX_GUARD_NATIVE_JAR")
                .expect("Pass the current native-tested Votex Core JAR"),
        );
        let core = std::fs::read(&core_path).unwrap();
        assert!(
            crate::prepare::voice_guard_ready(&store.instance("main").unwrap()).unwrap() == false
        );
        let profile = store.profile("main").unwrap();
        launch_inner(
            store.clone(),
            profile,
            None,
            false,
            Some("VotexRegQA".into()),
            None,
            Some(&core),
            true,
            None,
        )
        .await
        .unwrap();
        let game = store.instance("main").unwrap();
        let proof = read_json(&game.join("config/votex/startup.json")).unwrap();
        assert!(
            proof["nonce"].as_str().unwrap_or_default().len() == 36,
            "Invalid native startup proof: {proof:#}"
        );
        assert_eq!(
            proof["stage"], "title-screen",
            "The isolated client did not reach the title screen: {proof:#}"
        );
        let installed = crate::modrinth::installed(&store, "main").unwrap();
        for id in [
            "sodium",
            "iris",
            "modmenu",
            "zoomify",
            "lithium",
            "ferritecore",
            "immediatelyfast",
            "xaerominimap",
            "xaeroworldmap",
            "appleskin",
            "cloth-config",
            "skinlayers3d",
            "voicechat",
            "mousetweaks",
        ] {
            assert!(
                installed
                    .as_array()
                    .unwrap()
                    .iter()
                    .any(|item| item["modId"] == id && item["enabled"] == true),
                "Missing loaded-pack mod {id}: {installed:#}"
            );
        }
        let saved = store.profile("main").unwrap();
        let state = store.state().unwrap();
        let options = std::fs::read_to_string(game.join("options.txt")).unwrap();
        let game_fps: u32 = options.lines().find_map(|line| line.strip_prefix("maxFps:")).unwrap().parse().unwrap();
        assert_ne!(game_fps, 143, "Vanilla must have saved its actual quantized slider value");
        assert_eq!(saved.max_fps, game_fps);
        assert_eq!(saved.gui_scale, 2);
        assert_eq!(saved.fov, 90.0);
        assert_eq!(state["gamePresetValues"]["performance"]["maxFps"], game_fps);
        assert_eq!(state["gamePresetValues"]["performance"]["guiScale"], 2);
        assert_eq!(state["gamePresetValues"]["performance"]["fov"], 90.0);
        let logs = std::fs::read_to_string(game.join("logs/latest.log")).unwrap();
        assert!(!logs.contains("Mod appleskin provides a broken implementation of ModMenuApi"));
        assert!(!game.join("config/votex/voice-microphone.json").exists());
        let working = recovery::latest_working(&store, "main").unwrap().expect("The successfully loaded pack needs a recovery checkpoint");
        for item in installed.as_array().unwrap() {
            let name = format!("mods/{}", item["filename"].as_str().unwrap());
            assert_eq!(working.files.get(&name), Some(&network::hash(&std::fs::read(game.join(&name)).unwrap(),"sha512").unwrap()));
        }
        assert!(store.recovery_pins.lock().unwrap().is_empty());
        let receipt = json!({"status":"passed","minecraft":"26.2","loader":"fabric","offlineAccount":"VotexRegQA","coreStartup":proof,"coreSha256":network::hash(&core,"sha256").unwrap(),"enabledModCount":installed.as_array().unwrap().len(),"shaders":["complementary-reimagined","bsl-shaders"],"appleSkinModMenuConfigError":false,"actualSavedFps":game_fps,"launcherSyncedFps":saved.max_fps,"sharedClassSynced":true,"guiScale":saved.gui_scale,"fov":saved.fov,"recoveryContainsActualLoadedPack":true,"recoveryLeasesReleased":true,"microsoftLogin":false,"microphonePermission":false,"gameProcess":"closed-after-title-screen"});
        atomic(
            &qa_root.join("native-title-screen-proof.json"),
            &serde_json::to_vec_pretty(&receipt).unwrap(),
        )
        .unwrap();
        println!(
            "VOTEX_PACKAGE_STARTUP_PASS proof={}",
            qa_root.join("native-title-screen-proof.json").display()
        );
    }
    #[test]
    fn credentials_are_redacted_without_changing_empty_secret_logs() {
        assert_eq!(redact("normal game output", ""), "normal game output");
        assert_eq!(
            redact("key=fixture-$-key token=fixture-$-key", "fixture-$-key"),
            "key=[REDACTED] token=[REDACTED]"
        );
    }
    #[tokio::test]
    #[ignore = "Explicit online smoke test"]
    async fn live_official_metadata() {
        let temp = tempfile::tempdir().unwrap();
        let store = Store::new(temp.path().join("votex")).unwrap();
        let c = network::client().unwrap();
        let v = versions(&store).await.unwrap();
        let entry = v["versions"]
            .as_array()
            .unwrap()
            .iter()
            .find(|v| v["id"] == "26.2")
            .unwrap();
        let path = store.root.join("cache/test-metadata.json");
        network::download(
            &c,
            entry["url"].as_str().unwrap(),
            &path,
            Some(("sha1", entry["sha1"].as_str().unwrap())),
        )
        .await
        .unwrap();
        assert_eq!(read_json(&path).unwrap()["javaVersion"]["majorVersion"], 25);
        let loaders = network::json(&c, "https://meta.fabricmc.net/v2/versions/loader/26.2")
            .await
            .unwrap();
        assert!(loaders
            .as_array()
            .unwrap()
            .iter()
            .any(|l| l["loader"]["version"] == "0.19.5" && l["loader"]["stable"] == true));
    }
    #[test]
    fn keeps_native_classifier_libraries() {
        assert_ne!(
            library_key("org.lwjgl:lwjgl:3.4.1"),
            library_key("org.lwjgl:lwjgl:3.4.1:natives-windows")
        );
        assert_eq!(
            library_key("org.ow2.asm:asm:9.9"),
            library_key("org.ow2.asm:asm:9.10.1")
        );
    }
    #[test]
    fn conditional_args() {
        let map = BTreeMap::from([("name".into(), "a b".into())]);
        assert_eq!(
            arguments(
                &json!(["${name}",{"rules":[{"action":"allow","os":{"name":"osx"}}],"value":"no"}]),
                &map
            )
            .unwrap(),
            vec!["a b"]
        );
        assert!(arguments(&json!(["${missing}"]), &map).is_err());
    }
    #[test]
    fn maven_paths() {
        assert_eq!(
            maven("net.fabricmc:fabric-loader:0.19.5").unwrap(),
            "net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar"
        );
        assert!(maven("x:y:../a").is_err());
    }
}
