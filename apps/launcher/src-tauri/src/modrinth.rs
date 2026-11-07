use crate::{
    network, recovery,
    storage::{atomic, read_json, relative, Context, GameEngine, Result, Store},
};
use serde_json::{json, Value};
use std::collections::{HashSet, VecDeque};
use tauri::AppHandle;
const API: &str = "https://api.modrinth.com/v2";
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn version_fallback_only_handles_dependency_graph_rejections() {
        for error in ["Keine kompatible Mod-Version verfügbar: forge-only", "INCOMPATIBLE_MOD_VERSION: dependency", "Required dependencies select conflicting versions of one project", "Dependency plan contains an explicit incompatibility"] {
            assert!(resolution_can_fallback(error), "{error}");
        }
        for error in ["Modrinth API request failed (retry later if rate limited): 429", "Modrinth network unavailable", "Invalid mod versions", "Missing dependency metadata", "Dependency graph exceeds 100 mods", "Download-Prüfsumme stimmt nicht überein"] {
            assert!(!resolution_can_fallback(error), "{error}");
        }
    }
    #[test]
    fn pinned_companion_requires_the_exact_version_and_project_before_download() {
        let plan = vec![json!({"id":"Ls232EsW","project_id":"9eGKb6K1"}), json!({"id":"dependency","project_id":"fabric-api"})];
        assert!(validate_pin(&plan,"Ls232EsW","9eGKb6K1").is_ok());
        assert!(validate_pin(&plan,"latest","9eGKb6K1").is_err());
        assert!(validate_pin(&plan,"Ls232EsW","another-project").is_err());
        assert!(validate_pin(&[],"Ls232EsW","9eGKb6K1").is_err());
    }
    #[test]
    fn installed_icons_come_from_bounded_jar_metadata() {
        use std::io::Write;
        use base64::Engine;
        let root=tempfile::tempdir().unwrap();let path=root.path().join("mod.jar");
        let png=base64::engine::general_purpose::STANDARD.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a8S8AAAAASUVORK5CYII=").unwrap();
        for icon_path in ["assets/icon.png","../outside.png"] {
            let mut zip=zip::ZipWriter::new(std::fs::File::create(&path).unwrap());
            zip.start_file("fabric.mod.json",zip::write::SimpleFileOptions::default()).unwrap();
            zip.write_all(serde_json::to_string(&json!({"id":"test_mod","name":"Readable Mod","icon":icon_path})).unwrap().as_bytes()).unwrap();
            zip.start_file(icon_path,zip::write::SimpleFileOptions::default()).unwrap();zip.write_all(&png).unwrap();zip.finish().unwrap();
            let (name,id,icon)=jar_presentation(&path);
            assert_eq!(name.as_deref(),Some("Readable Mod"));assert_eq!(id.as_deref(),Some("test_mod"));
            assert_eq!(icon.is_some(),icon_path=="assets/icon.png");
        }
        std::fs::write(&path,b"broken zip").unwrap();assert_eq!(jar_presentation(&path),(None,None,None));
    }
    #[tokio::test]
    #[ignore = "Explicit online smoke test"]
    async fn live_modrinth_search() {
        let temp = tempfile::tempdir().unwrap();
        let store = Store::new(temp.path().join("votex")).unwrap();
        atomic(
            &store.root.join("config/launcher.json"),
            include_bytes!("../../../../packages/shared-config/defaults.json"),
        )
        .unwrap();
        let r = search(&store, "main", "sodium").await.unwrap();
        let hits = r["hits"].as_array().unwrap();
        assert!(!hits.is_empty());
        assert!(hits.iter().all(|h| h["versions"]
            .as_array()
            .unwrap()
            .iter()
            .any(|v| v == "26.2")));
    }
}
async fn get(c: &reqwest::Client, path: &str) -> Result<Value> {
    c.get(format!("{API}{path}"))
        .send()
        .await
        .context("Modrinth network unavailable")?
        .error_for_status()
        .context("Modrinth API request failed (retry later if rate limited)")?
        .json()
        .await
        .context("Invalid Modrinth response")
}
pub async fn search(store: &Store, id: &str, query: &str) -> Result<Value> {
    let p = store.profile(id)?;
    if p.engine == GameEngine::Horizon {
        return Err("Horizon nutzt keine Java-Mods. Suche nach nativen Horizon-Mods ist noch nicht in Votex integriert.".into());
    }
    if p.loader != "fabric" {
        return Err("Select a Fabric profile to browse compatible Fabric mods".into());
    }
    if query.len() > 200 {
        return Err("Search is too long".into());
    }
    let facets = json!([
        ["project_type:mod"],
        ["categories:fabric"],
        [format!("versions:{}", p.minecraft)]
    ])
    .to_string();
    let c = network::client()?;
    c.get(format!("{API}/search"))
        .query(&[
            ("query", query),
            ("facets", facets.as_str()),
            ("limit", "20"),
        ])
        .send()
        .await
        .context("Modrinth search unavailable")?
        .error_for_status()
        .context("Modrinth API error")?
        .json()
        .await
        .context("Invalid search result")
}
fn jar_presentation(path: &std::path::Path) -> (Option<String>, Option<String>, Option<String>) {
    use base64::Engine;
    use std::io::Read;
    let inspect = || -> Option<(Option<String>, Option<String>, Option<String>)> {
        let mut zip = zip::ZipArchive::new(std::fs::File::open(path).ok()?).ok()?;
        let info: Value = {
            let entry = zip.by_name("fabric.mod.json").ok()?;
            if entry.size() > 131_072 { return None; }
            let mut bytes=Vec::new(); entry.take(131_073).read_to_end(&mut bytes).ok()?;
            serde_json::from_slice(&bytes).ok()?
        };
        let name=info["name"].as_str().filter(|s|s.len()<=120).map(str::to_owned);
        let id=info["id"].as_str().filter(|s|s.len()<=80).map(str::to_owned);
        let icon_path=info["icon"].as_str().or_else(||info["icon"]["128"].as_str()).or_else(||info["icon"].as_object()?.values().find_map(Value::as_str));
        let icon=icon_path.and_then(|icon| {
            if icon.starts_with('/') || icon.contains('\\') || icon.contains(':') || icon.split('/').any(|s|s=="..") {return None;}
            let entry=zip.by_name(icon).ok()?;
            if entry.size()>524_288{return None;}
            let mut bytes=Vec::new();entry.take(524_289).read_to_end(&mut bytes).ok()?;
            if bytes.len()<24 || bytes.len()>524_288 || &bytes[..8]!=b"\x89PNG\r\n\x1a\n" {return None;}
            let w=u32::from_be_bytes(bytes[16..20].try_into().ok()?);let h=u32::from_be_bytes(bytes[20..24].try_into().ok()?);
            if w==0 || h==0 || w>1024 || h>1024{return None;}
            Some(format!("data:image/png;base64,{}",base64::engine::general_purpose::STANDARD.encode(bytes)))
        });
        Some((name,id,icon))
    };
    inspect().unwrap_or((None,None,None))
}
pub fn installed(store: &Store, id: &str) -> Result<Value> {
    let dir = store.instance(id)?.join("mods");
    std::fs::create_dir_all(&dir).context("Create mods directory")?;
    let meta_path = dir.join("installed.json");
    let metadata = if meta_path.exists() {
        read_json(&meta_path)?
    } else {
        json!([])
    };
    let mut items = vec![];
    for e in std::fs::read_dir(dir).context("List installed mods")? {
        let e = e.context("Read mod entry")?;
        let name = e.file_name().to_string_lossy().to_string();
        if !name.ends_with(".jar") && !name.ends_with(".jar.disabled") {
            continue;
        }
        let file = name.trim_end_matches(".disabled");
        let meta = metadata
            .as_array()
            .and_then(|a| a.iter().find(|m| m["filename"] == file));
        let (display_name,mod_id,icon)=jar_presentation(&e.path());
        items.push(json!({"filename":name,"enabled":!name.ends_with(".disabled"),"metadata":meta,"displayName":display_name,"modId":mod_id,"icon":icon}));
    }
    Ok(json!(items))
}
pub async fn resolve(
    profile: &crate::storage::Profile,
    seeds: Vec<(String, bool)>,
    old: &Value,
) -> Result<Vec<Value>> {
    // A project can publish a multi-loader release whose required graph cannot
    // run on this loader. Try bounded older root releases before declaring the
    // whole project unavailable. Explicit version pins never take this path.
    if seeds.len() != 1 || seeds[0].1 {
        return resolve_exact(profile, seeds, old, None).await;
    }
    let c = network::client()?;
    let reference = &seeds[0].0;
    let versions = compatible_versions(&c, profile, reference).await?;
    let candidates = versions.as_array().ok_or("Invalid mod versions")?;
    let mut last_error = format!("Keine kompatible Mod-Version verfügbar: {reference}");
    for candidate in candidates.iter().take(16) {
        let id = candidate["id"].as_str().ok_or("Missing mod version ID")?;
        match resolve_exact(profile, vec![(id.into(), true)], old, Some(candidate.clone())).await {
            Ok(plan) => return Ok(plan),
            Err(error) if resolution_can_fallback(&error) => last_error = error,
            Err(error) => return Err(error),
        }
    }
    Err(format!("Keine vollständig kompatible Version für {reference} gefunden. {last_error}"))
}
fn resolution_can_fallback(error: &str) -> bool {
    ["Keine kompatible Mod-Version verfügbar:", "INCOMPATIBLE_MOD_VERSION:",
     "Modrinth declares an incompatibility with an installed mod",
     "Required dependencies select conflicting versions of one project",
     "Dependency plan contains an explicit incompatibility"].iter().any(|prefix| error.starts_with(prefix))
}
async fn compatible_versions(c: &reqwest::Client, profile: &crate::storage::Profile, reference: &str) -> Result<Value> {
    c.get(format!("{API}/project/{reference}/version"))
        .query(&[("game_versions", json!([profile.minecraft]).to_string()), ("loaders", json!([profile.loader]).to_string())])
        .send().await.context("Find compatible mod version")?
        .error_for_status().context("Modrinth versions unavailable")?
        .json::<Value>().await.context("Invalid mod versions")
}
async fn resolve_exact(
    profile: &crate::storage::Profile,
    seeds: Vec<(String, bool)>,
    old: &Value,
    mut first_version: Option<Value>,
) -> Result<Vec<Value>> {
    let c = network::client()?;
    let mut queue = VecDeque::from(seeds);
    let mut seen = HashSet::new();
    let mut plan = vec![];
    let old_items = old.as_array().ok_or("Invalid installed mod metadata")?;
    while let Some((reference, is_version)) = queue.pop_front() {
        if !seen.insert(reference.clone()) {
            continue;
        }
        if seen.len() > 100 {
            return Err("Dependency graph exceeds 100 mods".into());
        }
        let version = if is_version {
            if first_version.as_ref().is_some_and(|v| v["id"] == reference) {
                first_version.take().unwrap()
            } else {
                get(&c, &format!("/version/{reference}")).await?
            }
        } else {
            let versions = compatible_versions(&c, profile, &reference).await?;
            versions
                .as_array()
                .and_then(|a| a.first())
                .ok_or_else(|| format!("Keine kompatible Mod-Version verfügbar: {reference} ({}/{})", profile.minecraft, profile.loader))?
                .clone()
        };
        if !version["game_versions"]
            .as_array()
            .map(|a| a.iter().any(|s| s == &profile.minecraft))
            .unwrap_or(false)
            || !version["loaders"]
                .as_array()
                .map(|a| a.iter().any(|s| s == &profile.loader))
                .unwrap_or(false)
        {
            return Err("INCOMPATIBLE_MOD_VERSION: A required dependency is incompatible with this Minecraft version.".into());
        }
        for d in version["dependencies"]
            .as_array()
            .ok_or("Missing dependency metadata")?
        {
            match d["dependency_type"].as_str() {
                Some("required") => {
                    if let Some(v) = d["version_id"].as_str() {
                        queue.push_back((v.into(), true));
                    } else if let Some(p) = d["project_id"].as_str() {
                        queue.push_back((p.into(), false));
                    } else {
                        return Err("A required dependency cannot be resolved automatically".into());
                    }
                }
                Some("incompatible") => {
                    if old_items.iter().any(|m| {
                        (!d["version_id"].is_null() && m["id"] == d["version_id"])
                            || (!d["project_id"].is_null() && m["project_id"] == d["project_id"])
                    }) {
                        return Err(
                            "Modrinth declares an incompatibility with an installed mod".into()
                        );
                    }
                }
                _ => {}
            }
        }
        if let Some(existing) = plan
            .iter()
            .find(|v: &&Value| v["project_id"] == version["project_id"])
        {
            if existing["id"] != version["id"] {
                return Err(
                    "Required dependencies select conflicting versions of one project".into(),
                );
            }
            continue;
        }
        plan.push(version);
    }
    // Check declared incompatibilities within the new plan too.
    for v in &plan {
        for d in v["dependencies"].as_array().unwrap() {
            if d["dependency_type"] == "incompatible"
                && plan.iter().any(|m| {
                    (!d["version_id"].is_null() && m["id"] == d["version_id"])
                        || (!d["project_id"].is_null() && m["project_id"] == d["project_id"])
                })
            {
                return Err("Dependency plan contains an explicit incompatibility".into());
            }
        }
    }
    Ok(plan)
}
pub async fn install(store: &Store, id: &str, project: &str, app: &AppHandle) -> Result<Value> {
    install_with_progress(store, id, project, Some(app)).await
}
pub(crate) async fn install_with_progress(store: &Store, id: &str, project: &str, app: Option<&AppHandle>) -> Result<Value> {
    install_reference(store, id, project, false, None, app).await
}
/// Optional companions may rely on a native adapter tested against one exact version.
pub async fn install_pinned(store: &Store, id: &str, project: &str, version: &str, app: &AppHandle) -> Result<Value> {
    install_pinned_with_progress(store, id, project, version, Some(app)).await
}
pub(crate) async fn install_pinned_with_progress(store: &Store, id: &str, project: &str, version: &str, app: Option<&AppHandle>) -> Result<Value> {
    crate::storage::safe_id(project)?;
    install_reference(store, id, version, true, Some(project), app).await
}
#[cfg(test)]
pub(crate) async fn install_pinned_without_window(store: &Store, id: &str, project: &str, version: &str) -> Result<Value> {
    install_pinned_with_progress(store, id, project, version, None).await
}
fn validate_pin(plan: &[Value], version: &str, project: &str) -> Result<()> {
    if !plan.iter().any(|item| item["id"] == version && item["project_id"] == project) {
        return Err("Die festgelegte Begleitmod-Version gehört nicht zum erwarteten Projekt".into());
    }
    Ok(())
}
async fn install_reference(store: &Store, id: &str, reference: &str, pinned: bool, expected_project: Option<&str>, app: Option<&AppHandle>) -> Result<Value> {
    crate::storage::safe_id(reference)?;
    let profile = store.profile(id)?;
    if profile.engine == GameEngine::Horizon {
        return Err("Horizon kann keine Java-Mods installieren. Diese bleiben nur in Java-Profilen verfügbar.".into());
    }
    if profile.loader == "vanilla" {
        return Err("Ein Profil mit Modloader ist erforderlich".into());
    }
    let c = network::client()?;
    let meta_path = store.instance(id)?.join("mods/installed.json");
    let old = if meta_path.exists() {
        read_json(&meta_path)?
    } else {
        json!([])
    };
    let old_items = old.as_array().ok_or("Invalid installed mod metadata")?;
    let plan = resolve(&profile, vec![(reference.to_string(), pinned)], &old).await?;
    if let Some(project) = expected_project { validate_pin(&plan, reference, project)?; }
    let mut downloads = vec![];
    for (index, v) in plan.iter().enumerate() {
        let files = v["files"].as_array().ok_or("Missing mod files")?;
        let f = files
            .iter()
            .find(|f| f["primary"] == true)
            .or(files.first())
            .ok_or("No downloadable mod file")?;
        let filename = f["filename"].as_str().ok_or("Missing filename")?;
        if !filename.ends_with(".jar") || filename.contains('/') || filename.contains('\\') {
            return Err("Unsafe mod filename".into());
        }
        let sum = f["hashes"]["sha512"]
            .as_str()
            .ok_or("Mod file is missing a SHA-512 checksum")?;
        let cache = relative(&store.root.join("cache/mods"), &format!("{sum}.jar"))?;
        if let Some(app) = app { crate::minecraft::event(
            app,
            "mods",
            "Downloading and verifying compatible mods",
            index,
            plan.len(),
        ); }
        network::download(
            &c,
            f["url"].as_str().ok_or("Missing mod URL")?,
            &cache,
            Some(("sha512", sum)),
        )
        .await?;
        downloads.push((filename.to_string(), cache, v.clone()));
    }
    let before = recovery::create(store, id, "pre-mod-installation")?;
    let mods = store.instance(id)?.join("mods");
    let mut items = old_items.clone();
    let result = (|| -> Result<()> {
        for (filename, cache, v) in downloads {
            for old in items.iter().filter(|m| m["project_id"] == v["project_id"]) {
                let old_name = old["filename"]
                    .as_str()
                    .ok_or("Invalid existing mod metadata")?;
                if old_name != filename {
                    for suffix in ["", ".disabled"] {
                        let path = relative(&mods, &format!("{old_name}{suffix}"))?;
                        if path.exists() {
                            let dest = store
                                .root
                                .join("backups/quarantine")
                                .join(id)
                                .join(&before.id)
                                .join(format!("{old_name}{suffix}"));
                            std::fs::create_dir_all(dest.parent().unwrap())
                                .context("Preserve old mod")?;
                            std::fs::rename(path, dest).context("Preserve old mod")?;
                        }
                    }
                }
            }
            atomic(
                &relative(&mods, &filename)?,
                &std::fs::read(cache).context("Read verified mod")?,
            )?;
            items.retain(|m| m["project_id"] != v["project_id"]);
            let mut record = v;
            record["filename"] = json!(filename);
            items.push(record);
        }
        atomic(
            &meta_path,
            &serde_json::to_vec_pretty(&items).context("Save installed mod metadata")?,
        )?;
        Ok(())
    })();
    if let Err(e) = result {
        recovery::restore(store, id, &before.id)?;
        return Err(format!("Mod installation failed and was rolled back: {e}"));
    }
    installed(store, id)
}
pub fn toggle(store: &Store, id: &str, filename: &str) -> Result<Value> {
    let root = store.instance(id)?.join("mods");
    if filename.contains('/')
        || filename.contains('\\')
        || (!filename.ends_with(".jar") && !filename.ends_with(".jar.disabled"))
    {
        return Err("Invalid mod filename".into());
    }
    let source = relative(&root, filename)?;
    let dest = relative(
        &root,
        &if filename.ends_with(".disabled") {
            filename.trim_end_matches(".disabled").into()
        } else {
            format!("{filename}.disabled")
        },
    )?;
    if dest.exists() {
        return Err("Destination mod already exists; resolve duplicate files first".into());
    }
    recovery::create(store, id, "pre-mod-change")?;
    std::fs::rename(source, dest).context("Toggle mod for next launch")?;
    installed(store, id)
}
pub fn remove(store: &Store, id: &str, filename: &str) -> Result<Value> {
    if filename.contains('/')
        || filename.contains('\\')
        || (!filename.ends_with(".jar") && !filename.ends_with(".jar.disabled"))
    {
        return Err("Invalid mod filename".into());
    }
    let s = recovery::create(store, id, "pre-mod-removal")?;
    let source = relative(&store.instance(id)?.join("mods"), filename)?;
    let dest = store
        .root
        .join("backups/quarantine")
        .join(id)
        .join(&s.id)
        .join(filename);
    std::fs::create_dir_all(dest.parent().unwrap()).context("Create preserved mods directory")?;
    std::fs::rename(source, dest).context("Preserve removed mod")?;
    let path = store.instance(id)?.join("mods/installed.json");
    if path.exists() {
        let mut m = read_json(&path)?;
        m.as_array_mut()
            .ok_or("Invalid installed metadata")?
            .retain(|m| m["filename"] != filename.trim_end_matches(".disabled"));
        atomic(
            &path,
            &serde_json::to_vec(&m).context("Update mod metadata")?,
        )?;
    }
    installed(store, id)
}
