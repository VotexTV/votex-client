use crate::{
    curseforge, network,
    storage::{atomic, relative, Context, Result, Store},
};
use serde_json::{json, Value};
use std::{
    collections::HashSet,
    io::{Cursor, Read},
    path::Path,
};
const MAX_PACK: usize = 67_108_864;
pub fn safe_content(path: &str) -> Result<()> {
    crate::storage::relative(Path::new("C:/votex-staging"), path)?;
    let lower = path.to_lowercase();
    if !(lower.starts_with("mods/")
        || lower.starts_with("config/")
        || lower.starts_with("resourcepacks/")
        || lower.starts_with("shaderpacks/")
        || lower == "options.txt"
        || lower == "icon.png")
        || lower.starts_with("config/votex/")
        || [
            ".exe", ".dll", ".bat", ".cmd", ".ps1", ".vbs", ".lnk", ".url", ".sh",
        ]
        .iter()
        .any(|s| lower.ends_with(s))
    {
        return Err(format!("Nicht erlaubter Modpack-Pfad: {path}"));
    }
    Ok(())
}
struct ArchivePlan {
    manifest: Value,
    curse: bool,
    overrides: Vec<(String, Vec<u8>)>,
}
fn inspect(bytes: &[u8]) -> Result<ArchivePlan> {
    if bytes.len() > MAX_PACK {
        return Err("Modpack-Archiv überschreitet 64 MiB".into());
    }
    let mut zip = zip::ZipArchive::new(Cursor::new(bytes)).context("Ungültiges Modpack-Archiv")?;
    if zip.len() > 10_000 {
        return Err("Modpack enthält zu viele Dateien".into());
    }
    let curse = zip.by_name("modrinth.index.json").is_err();
    let manifest_name = if curse {
        "manifest.json"
    } else {
        "modrinth.index.json"
    };
    let manifest: Value = {
        let e = zip
            .by_name(manifest_name)
            .context("Kein unterstütztes Modpack-Manifest gefunden")?;
        if e.size() > 4_000_000 {
            return Err("Modpack-Manifest ist zu groß".into());
        }
        serde_json::from_reader(e).context("Ungültiges Modpack-Manifest")?
    };
    if curse {
        if manifest["manifestType"] != "minecraftModpack" || manifest["manifestVersion"] != 1 {
            return Err("Unbekanntes CurseForge-Modpack-Format".into());
        }
    } else if manifest["game"] != "minecraft" || manifest["formatVersion"] != 1 {
        return Err("Unbekanntes Modrinth-Modpack-Format".into());
    }
    let override_dir = if curse {
        manifest["overrides"].as_str().unwrap_or("overrides")
    } else {
        "overrides"
    };
    crate::storage::safe_id(override_dir)?;
    let mut seen = HashSet::new();
    let mut files = vec![];
    let mut total = 0u64;
    for i in 0..zip.len() {
        let mut e = zip.by_index(i).context("Read archive entry")?;
        let name = e.name().to_string();
        if name.contains('\\')
            || e.enclosed_name().is_none()
            || e.unix_mode()
                .map(|m| m & 0o170000 == 0o120000)
                .unwrap_or(false)
        {
            return Err("Modpack enthält einen unsicheren Pfad oder Link".into());
        }
        if e.is_dir() {
            continue;
        }
        if !seen.insert(name.to_lowercase()) {
            return Err("Modpack enthält doppelte Dateipfade".into());
        }
        let prefix = format!("{override_dir}/");
        let client = name.starts_with("client-overrides/");
        let Some(path) = name.strip_prefix(&prefix).or_else(|| {
            if client && !curse {
                name.strip_prefix("client-overrides/")
            } else {
                None
            }
        }) else {
            continue;
        };
        safe_content(path)?;
        total = total.checked_add(e.size()).ok_or("Archive size overflow")?;
        if e.size() > 134_217_728
            || total > 536_870_912
            || e.size() > e.compressed_size().saturating_mul(1000).max(1_048_576)
        {
            return Err("Modpack überschreitet die Entpackgrenzen".into());
        }
        let mut data = Vec::new();
        e.by_ref()
            .take(134_217_729)
            .read_to_end(&mut data)
            .context("Read override")?;
        if data.len() as u64 != e.size() {
            return Err("Ungültige Archivgröße".into());
        }
        files.push((path.to_string(), data, client));
    }
    files.sort_by_key(|f| f.2);
    Ok(ArchivePlan {
        manifest,
        curse,
        overrides: files.into_iter().map(|(a, b, _)| (a, b)).collect(),
    })
}
pub async fn import(
    store: &Store,
    template: &str,
    bytes: Vec<u8>,
    app: &tauri::AppHandle,
) -> Result<Value> {
    import_inner(store, template, bytes, Some(app)).await
}
async fn import_inner(
    store: &Store,
    template: &str,
    bytes: Vec<u8>,
    app: Option<&tauri::AppHandle>,
) -> Result<Value> {
    let plan = inspect(&bytes)?;
    let m = &plan.manifest;
    let mut p = store.profile(template)?;
    p.id = uuid::Uuid::new_v4().to_string();
    p.name = m["name"]
        .as_str()
        .unwrap_or("Mein Modpack")
        .chars()
        .take(60)
        .collect();
    p.performance_mods = false;
    p.shader_enabled = false;
    p.game_options_revision = 0;
    p.loader = "vanilla".into();
    p.loader_version = String::new();
    if plan.curse {
        p.minecraft = m["minecraft"]["version"]
            .as_str()
            .ok_or("Missing pack Minecraft version")?
            .into();
        let loader = m["minecraft"]["modLoaders"]
            .as_array()
            .and_then(|a| {
                a.iter()
                    .find(|v| v["primary"] == true)
                    .or_else(|| a.first())
            })
            .and_then(|v| v["id"].as_str())
            .ok_or("Missing pack loader")?;
        let (id, v) = loader.split_once('-').ok_or("Invalid pack loader")?;
        if !matches!(id, "fabric" | "quilt" | "forge" | "neoforge") {
            return Err("Nicht unterstützter Modpack-Loader".into());
        }
        p.loader = id.into();
        p.loader_version = v.into();
    } else {
        p.minecraft = m["dependencies"]["minecraft"]
            .as_str()
            .ok_or("Missing pack Minecraft version")?
            .into();
        let mut count = 0;
        for (key, id) in [
            ("fabric-loader", "fabric"),
            ("quilt-loader", "quilt"),
            ("forge", "forge"),
            ("neoforge", "neoforge"),
        ] {
            if let Some(v) = m["dependencies"][key].as_str() {
                p.loader = id.into();
                p.loader_version = v.into();
                count += 1;
            }
        }
        if count > 1 {
            return Err("Modpack verlangt mehrere inkompatible Loader".into());
        }
        for (key, _) in m["dependencies"]
            .as_object()
            .ok_or("Missing dependencies")?
        {
            if !matches!(
                key.as_str(),
                "minecraft" | "fabric-loader" | "quilt-loader" | "forge" | "neoforge"
            ) {
                return Err(format!("Unbekannte Modpack-Abhängigkeit: {key}"));
            }
        }
    }
    if p.loader == "forge" && !p.loader_version.starts_with(&format!("{}-", p.minecraft)) {
        p.loader_version = format!("{}-{}", p.minecraft, p.loader_version);
    }
    // Validate the profile before it influences any path or network request.
    crate::storage::safe_id(&p.id)?;
    if p.minecraft.len() > 64
        || !p
            .minecraft
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b"._-".contains(&b))
        || p.loader_version.len() > 64
        || !p
            .loader_version
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b"._+-".contains(&b))
    {
        return Err("Ungültige Versionskennung im Modpack".into());
    }
    let stage = relative(&store.root, &format!("cache/modpack-staging/{}", p.id))?;
    std::fs::create_dir_all(&stage).context("Create isolated pack staging")?;
    let files = m["files"].as_array().ok_or("Missing pack file list")?;
    if files.len() > 5000 {
        return Err("Modpack enthält zu viele Downloads".into());
    }
    let c = network::client()?;
    let mut seen = HashSet::new();
    let mut downloaded = 0u64;
    let mut installed = vec![];
    for (i, f) in files.iter().enumerate() {
        if let Some(app) = app {
            crate::minecraft::event(
                app,
                "mods",
                "Modpack wird getrennt heruntergeladen und geprüft",
                i,
                files.len(),
            );
        }
        if plan.curse {
            if f["required"] == false {
                continue;
            }
            let project = f["projectID"]
                .as_u64()
                .ok_or("Invalid CurseForge pack project")?;
            let file = f["fileID"].as_u64().ok_or("Invalid CurseForge pack file")?;
            let data = curseforge::file(project, file).await?;
            if !data["gameVersions"]
                .as_array()
                .map(|a| a.iter().any(|v| v == &p.minecraft))
                .unwrap_or(false)
            {
                return Err("CurseForge-Modpack enthält eine inkompatible Datei".into());
            }
            let size = data["fileLength"]
                .as_u64()
                .ok_or("Missing pack file size")?;
            downloaded = downloaded.checked_add(size).ok_or("Pack size overflow")?;
            if size > 536_870_912 || downloaded > 4_294_967_296 {
                return Err("Modpack überschreitet das Downloadlimit (4 GiB)".into());
            }
            let (name, url, sum) = curseforge::download_info(&data)?;
            let folder = curseforge::content_folder(project).await?;
            let path = format!("{folder}/{name}");
            safe_content(&path)?;
            if !seen.insert(path.to_lowercase()) {
                return Err("Duplicate pack destination".into());
            }
            network::download(&c, url, &relative(&stage, &path)?, Some(("sha1", sum))).await?;
            if folder == "mods" {
                installed.push(json!({"project_id":format!("cf-{project}"),"filename":name,"version_number":data["displayName"]}));
            }
        } else {
            if matches!(
                f["env"]["client"].as_str(),
                Some("unsupported" | "optional")
            ) {
                continue;
            }
            let path = f["path"].as_str().ok_or("Missing pack file path")?;
            safe_content(path)?;
            if !seen.insert(path.to_lowercase()) {
                return Err("Duplicate pack destination".into());
            }
            let size = f["fileSize"].as_u64().ok_or("Missing pack file size")?;
            downloaded = downloaded.checked_add(size).ok_or("Pack size overflow")?;
            if size > 536_870_912 || downloaded > 4_294_967_296 {
                return Err("Modpack überschreitet das Downloadlimit (4 GiB)".into());
            }
            let sum = f["hashes"]["sha512"]
                .as_str()
                .ok_or("Modpack-Datei ohne SHA-512-Prüfsumme")?;
            if sum.len() != 128 || !sum.bytes().all(|b| b.is_ascii_hexdigit()) {
                return Err("Invalid pack checksum".into());
            }
            let url = f["downloads"]
                .as_array()
                .and_then(|a| {
                    a.iter()
                        .filter_map(Value::as_str)
                        .find(|url| network::allowed(url).is_ok())
                })
                .ok_or("Modpack verlangt eine nicht freigegebene Downloadquelle")?;
            let target = relative(&stage, path)?;
            network::download(&c, url, &target, Some(("sha512", sum))).await?;
            if std::fs::metadata(&target)
                .context("Verify pack file size")?
                .len()
                != size
            {
                return Err("Modpack-Dateigröße stimmt nicht überein".into());
            }
        }
    }
    for (path, data) in plan.overrides {
        atomic(&relative(&stage, &path)?, &data)?;
    }
    if !installed.is_empty() {
        atomic(
            &stage.join("mods/installed.json"),
            &serde_json::to_vec(&installed).context("Pack mod receipt")?,
        )?;
    }
    atomic(&stage.join("votex-modpack.json"),&serde_json::to_vec(&json!({"name":p.name,"source":if plan.curse{"curseforge"}else{"modrinth"},"optionalFiles":"omitted","importedAt":chrono::Utc::now().to_rfc3339()})).context("Pack import receipt")?)?;
    let _lock = store
        .lock
        .lock()
        .map_err(|_| "Configuration lock unavailable")?;
    let mut state = store.state()?;
    let profiles = state["profiles"].as_array_mut().ok_or("Missing profiles")?;
    if profiles.len() >= 100 {
        return Err("Maximal 100 Profile".into());
    }
    profiles.push(serde_json::to_value(&p).context("Serialize pack profile")?);
    state["activeProfile"] = json!(p.id);
    crate::storage::validate_state(&state)?;
    let destination = store.instance(&p.id)?;
    if destination.exists() {
        return Err("Modpack destination already exists".into());
    }
    std::fs::rename(&stage, &destination).context("Commit verified modpack")?;
    if let Err(e) = atomic(
        &store.root.join("config/launcher.json"),
        &serde_json::to_vec_pretty(&state).context("Save modpack profile")?,
    ) {
        let _ = std::fs::rename(&destination, &stage);
        return Err(e);
    }
    Ok(
        json!({"profile":p,"message":"Modpack als neues, getrenntes Profil importiert. Optionale Dateien wurden ausgelassen."}),
    )
}
pub async fn from_project(
    store: &Store,
    id: &str,
    provider: &str,
    project: &str,
    app: &tauri::AppHandle,
) -> Result<Value> {
    let p = store.profile(id)?;
    let c = network::client()?;
    let (url, algorithm, sum) = if provider == "modrinth" {
        let data = c
            .get(format!(
                "https://api.modrinth.com/v2/project/{project}/version"
            ))
            .query(&[
                ("game_versions", json!([p.minecraft]).to_string()),
                ("loaders", json!([p.loader]).to_string()),
            ])
            .send()
            .await
            .context("Find compatible modpack")?
            .error_for_status()
            .context("Modpack lookup failed")?
            .json::<Value>()
            .await
            .context("Invalid pack versions")?;
        let v = data
            .as_array()
            .and_then(|a| a.first())
            .ok_or("Kein passendes Modpack für dieses Profil")?;
        let f = v["files"]
            .as_array()
            .and_then(|a| {
                a.iter().find(|f| {
                    f["filename"]
                        .as_str()
                        .map(|s| s.ends_with(".mrpack"))
                        .unwrap_or(false)
                })
            })
            .ok_or("Kein MRPACK-Download")?;
        (
            f["url"].as_str().ok_or("Missing pack URL")?.to_string(),
            "sha512",
            f["hashes"]["sha512"]
                .as_str()
                .ok_or("Missing pack hash")?
                .to_string(),
        )
    } else {
        let f = curseforge::compatible(project.parse().map_err(|_| "Invalid CurseForge ID")?, &p)
            .await?;
        let (_, url, sum) = curseforge::download_info(&f)?;
        (url.to_string(), "sha1", sum.to_string())
    };
    let path = relative(&store.root.join("cache/modpacks"), &format!("{sum}.zip"))?;
    network::download(&c, &url, &path, Some((algorithm, &sum))).await?;
    if std::fs::metadata(&path).context("Read pack size")?.len() > MAX_PACK as u64 {
        return Err("Modpack-Archiv überschreitet 64 MiB".into());
    }
    import(
        store,
        id,
        std::fs::read(path).context("Read downloaded pack")?,
        app,
    )
    .await
}
#[cfg(test)]
mod tests {
    use super::*;
    #[tokio::test]
    async fn valid_pack_creates_a_separate_profile_without_changing_existing_worlds() {
        use std::io::Write;
        let temp = tempfile::tempdir().unwrap();
        let store = Store::new(temp.path().join("votex")).unwrap();
        atomic(
            &store.root.join("config/launcher.json"),
            include_bytes!("../../../../packages/shared-config/defaults.json"),
        )
        .unwrap();
        let world = store
            .instance("main")
            .unwrap()
            .join("saves/example/level.dat");
        atomic(&world, b"my-world").unwrap();
        let mut zip = zip::ZipWriter::new(Cursor::new(Vec::new()));
        let options = zip::write::SimpleFileOptions::default();
        zip.start_file("modrinth.index.json", options).unwrap();
        zip.write_all(br#"{"game":"minecraft","formatVersion":1,"name":"Test pack","versionId":"1","files":[],"dependencies":{"minecraft":"1.20.1","fabric-loader":"0.16.14"}}"#).unwrap();
        zip.start_file("overrides/config/example.json", options)
            .unwrap();
        zip.write_all(b"{\"enabled\":true}").unwrap();
        let result = import_inner(&store, "main", zip.finish().unwrap().into_inner(), None)
            .await
            .unwrap();
        let id = result["profile"]["id"].as_str().unwrap();
        assert_ne!(id, "main");
        assert_eq!(std::fs::read(world).unwrap(), b"my-world");
        assert!(store
            .instance(id)
            .unwrap()
            .join("config/example.json")
            .exists());
        assert_eq!(
            store.state().unwrap()["profiles"].as_array().unwrap().len(),
            2
        );
        assert_eq!(store.profile(id).unwrap().loader_version, "0.16.14");
    }
    #[test]
    fn rejects_host_paths_and_executables() {
        for p in [
            "../mods/a.jar",
            "mods/../../escape",
            "C:/x",
            "config/votex/presets.json",
            "mods/start.cmd",
            "saves/world/level.dat",
            "mods/CON.jar",
        ] {
            assert!(safe_content(p).is_err(), "{p}");
        }
        assert!(safe_content("mods/example.jar").is_ok());
    }
    #[test]
    fn zip_path_escape_rejected() {
        use std::io::Write;
        let mut z = zip::ZipWriter::new(Cursor::new(Vec::new()));
        z.start_file(
            "modrinth.index.json",
            zip::write::SimpleFileOptions::default(),
        )
        .unwrap();
        z.write_all(br#"{"game":"minecraft","formatVersion":1}"#)
            .unwrap();
        z.start_file(
            "overrides/../../escape",
            zip::write::SimpleFileOptions::default(),
        )
        .unwrap();
        z.write_all(b"bad").unwrap();
        assert!(inspect(&z.finish().unwrap().into_inner()).is_err());
    }
}
