use crate::{
    modrinth, network, recovery,
    storage::{self, atomic, read_json, relative, Context, Profile, Result, Store},
};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::{
    collections::{BTreeMap, HashSet},
    path::Path,
};
const API: &str = "https://api.modrinth.com/v2";
#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct Item {
    filename: String,
    source: String,
    hash: String,
    url: String,
    enabled: bool,
    metadata: Value,
}
#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct Plan {
    token: String,
    created: i64,
    source: String,
    target: Profile,
    create: bool,
    source_stamp: String,
    target_stamp: String,
    items: Vec<Item>,
    rows: Vec<Value>,
}
fn inventory(dir: &Path) -> Result<BTreeMap<String, String>> {
    let mut files = BTreeMap::new();
    if !dir.exists() {
        return Ok(files);
    }
    for e in std::fs::read_dir(dir).context("Mods lesen")? {
        let e = e.context("Mod lesen")?;
        let name = e.file_name().to_string_lossy().to_string();
        if name.ends_with(".jar") || name.ends_with(".jar.disabled") || name == "installed.json" {
            let path = relative(dir, &name)?;
            let meta = std::fs::metadata(&path).context("Mod prüfen")?;
            if !meta.is_file() || meta.len() > 536_870_912 {
                return Err("Mod-Datei ungültig oder größer als 512 MiB".into());
            }
            files.insert(
                name,
                network::hash(&std::fs::read(path).context("Mod prüfen")?, "sha512")?,
            );
            if files.len() > 300 {
                return Err("Maximal 300 Mods pro Übernahme".into());
            }
        }
    }
    Ok(files)
}
fn stamp(store: &Store, p: &Profile) -> Result<String> {
    network::hash(
        &serde_json::to_vec(&(p, inventory(&store.instance(&p.id)?.join("mods"))?))
            .context("Übernahme prüfen")?,
        "sha256",
    )
}
fn meta(store: &Store, id: &str) -> Result<Value> {
    let path = store.instance(id)?.join("mods/installed.json");
    if path.exists() {
        read_json(&path)
    } else {
        Ok(json!([]))
    }
}
async fn from_hash(c: &reqwest::Client, hash: &str) -> Result<Option<Value>> {
    let r = c
        .get(format!("{API}/version_file/{hash}"))
        .query(&[("algorithm", "sha512")])
        .send()
        .await
        .context("Modrinth-Dateiprüfung")?;
    if r.status() == reqwest::StatusCode::NOT_FOUND {
        return Ok(None);
    }
    Ok(Some(
        r.error_for_status()
            .context("Modrinth-Dateiprüfung fehlgeschlagen")?
            .json()
            .await
            .context("Modrinth-Dateiantwort")?,
    ))
}
fn version_file(v: &Value) -> Result<&Value> {
    let a = v["files"].as_array().ok_or("Mod-Dateien fehlen")?;
    a.iter()
        .find(|f| f["primary"] == true)
        .or(a.first())
        .ok_or_else(|| "Keine Mod-Datei".into())
}
fn validate_file(f: &Value) -> Result<(String, String, String)> {
    let name = f["filename"].as_str().ok_or("Dateiname fehlt")?;
    storage::relative(Path::new("mods"), name)?;
    if !name.ends_with(".jar") || name.contains('/') {
        return Err("Ungültiger Mod-Dateiname".into());
    }
    let hash = f["hashes"]["sha512"].as_str().ok_or("SHA-512 fehlt")?;
    if hash.len() != 128 || !hash.bytes().all(|c| c.is_ascii_hexdigit()) {
        return Err("Ungültige SHA-512".into());
    }
    let url = f["url"].as_str().ok_or("Download fehlt")?;
    network::allowed(url)?;
    Ok((name.into(), hash.into(), url.into()))
}
pub async fn plan(store: &Store, source: &str, target: &Profile, create: bool) -> Result<Value> {
    let src = store.profile(source)?;
    if src.engine == crate::storage::GameEngine::Horizon
        || target.engine == crate::storage::GameEngine::Horizon
    {
        return Err("Java-Mods und Horizon-Profile bleiben getrennt und lassen sich nicht übertragen.".into());
    }
    storage::safe_id(&target.id)?;
    if source == target.id {
        return Err("Quell- und Zielprofil müssen verschieden sein".into());
    }
    let mut state = store.state()?;
    if create {
        if state["profiles"]
            .as_array()
            .ok_or("Profile fehlen")?
            .iter()
            .any(|p| p["id"] == target.id)
            || store.instance(&target.id)?.exists()
        {
            return Err("Zielprofil existiert bereits".into());
        }
        state["profiles"]
            .as_array_mut()
            .unwrap()
            .push(json!(target));
        storage::validate_state(&state)?;
    } else if serde_json::to_value(store.profile(&target.id)?).context("Zielprofil prüfen")?
        != json!(target)
    {
        return Err("Zielprofil hat sich geändert; bitte erneut prüfen".into());
    }
    if target.loader == "vanilla" {
        return Err("Für Mods braucht das Zielprofil einen Modloader".into());
    }
    let source_stamp = stamp(store, &src)?;
    let target_stamp = stamp(store, target)?;
    let files = inventory(&store.instance(source)?.join("mods"))?;
    let c = network::client()?;
    let old = if create {
        json!([])
    } else {
        meta(store, &target.id)?
    };
    let mut rows = vec![];
    let mut roots = vec![];
    let mut items = vec![];
    let mut root_enabled = BTreeMap::new();
    for (filename, hash) in files
        .iter()
        .filter(|(n, _)| n.ends_with(".jar") || n.ends_with(".jar.disabled"))
    {
        let enabled = !filename.ends_with(".disabled");
        if filename.starts_with("VotexCore-")
            || filename.starts_with("votex-core-")
            || filename.starts_with("votex-client-core-")
        {
            rows.push(json!({"name":filename,"action":"skip","detail":"Votex Core wird passend zum Zielprofil separat vorbereitet."}));
            continue;
        }
        if let Some(v) = from_hash(&c, hash).await? {
            let project = v["project_id"]
                .as_str()
                .ok_or("Projekt-ID fehlt")?
                .to_string();
            match modrinth::resolve(target, vec![(project.clone(), false)], &json!([])).await {
                Ok(resolved) => {
                    let root = resolved
                        .iter()
                        .find(|r| r["project_id"] == project)
                        .ok_or("Mod-Projekt fehlt im Plan")?;
                    rows.push(json!({"name":filename,"action":if root["id"]==v["id"]{"copy"}else{"update"},"detail":format!("{} → {} · {} {}",v["version_number"].as_str().unwrap_or("?"),root["version_number"].as_str().unwrap_or("?"),target.loader,target.minecraft),"enabled":enabled}));
                    roots.push((
                        root["id"].as_str().ok_or("Versions-ID fehlt")?.to_string(),
                        true,
                    ));
                    root_enabled
                        .entry(project)
                        .and_modify(|e| *e |= enabled)
                        .or_insert(enabled);
                }
                Err(e) => rows.push(json!({"name":filename,"action":"skip","detail":e})),
            }
        } else if src.minecraft == target.minecraft && src.loader == target.loader {
            rows.push(json!({"name":filename,"action":"local","detail":"Lokale Datei unverändert; identische Minecraft-Version und gleicher Loader.","enabled":enabled}));
            items.push(Item {
                filename: filename.trim_end_matches(".disabled").into(),
                source: filename.clone(),
                hash: hash.clone(),
                url: String::new(),
                enabled,
                metadata: Value::Null,
            });
        } else {
            rows.push(json!({"name":filename,"action":"skip","detail":"Nicht auf Modrinth erkannt. Kompatibilität für dieses Ziel nicht belegt; keine blinde JAR-Kopie."}));
        }
    }
    let versions = modrinth::resolve(target, roots, &json!([])).await?;
    // Replaced projects are removed from the old compatibility set; all remaining active mods are checked in both directions.
    let replacing: HashSet<String> = versions
        .iter()
        .filter_map(|v| v["project_id"].as_str().map(String::from))
        .collect();
    let retained: Vec<Value> = old
        .as_array()
        .ok_or("Ungültige Ziel-Metadaten")?
        .iter()
        .filter(|v| !replacing.contains(v["project_id"].as_str().unwrap_or("")))
        .cloned()
        .collect();
    let combined: Vec<&Value> = versions.iter().chain(retained.iter()).collect();
    for v in &combined {
        if let Some(deps) = v["dependencies"].as_array() {
            for d in deps {
                if d["dependency_type"] == "incompatible"
                    && combined.iter().any(|m| {
                        (!d["version_id"].is_null() && m["id"] == d["version_id"])
                            || (!d["project_id"].is_null() && m["project_id"] == d["project_id"])
                    })
                {
                    return Err("Der Plan widerspricht einer deklarierten Mod-Inkompatibilität. Zielprofil zuerst bereinigen.".into());
                }
            }
        }
    }
    for v in versions {
        let (filename, hash, url) = validate_file(version_file(&v)?)?;
        let project = v["project_id"].as_str().unwrap_or("");
        let enabled = *root_enabled.get(project).unwrap_or(&true);
        if !root_enabled.contains_key(project) {
            rows.push(json!({"name":v["name"],"action":"dependency","detail":format!("Benötigte Abhängigkeit · {}",v["version_number"].as_str().unwrap_or("?"))}));
        }
        items.push(Item {
            filename,
            source: String::new(),
            hash,
            url,
            enabled,
            metadata: v,
        });
    }
    let mut names = HashSet::new();
    for item in &items {
        if !names.insert(item.filename.to_lowercase()) {
            return Err("Zwei Mods verwenden denselben Dateinamen; Übernahme abgebrochen".into());
        }
    }
    let target_files = inventory(&store.instance(&target.id)?.join("mods"))?;
    for item in &items {
        for suffix in ["", ".disabled"] {
            if let Some(hash) = target_files.get(&format!("{}{suffix}", item.filename)) {
                let same_project = !item.metadata.is_null()
                    && old.as_array().unwrap().iter().any(|m| {
                        m["filename"] == item.filename
                            && m["project_id"] == item.metadata["project_id"]
                    });
                if hash != &item.hash && !same_project {
                    return Err(format!("Dateikonflikt im Ziel: {}. Die vorhandene, nicht zugeordnete Datei bleibt erhalten.",item.filename));
                }
            }
        }
    }
    let token = uuid::Uuid::new_v4().to_string();
    let p = Plan {
        token: token.clone(),
        created: chrono::Utc::now().timestamp(),
        source: source.into(),
        target: target.clone(),
        create,
        source_stamp,
        target_stamp,
        items,
        rows,
    };
    atomic(
        &relative(
            &store.root.join("cache/transfer-plans"),
            &format!("{token}.json"),
        )?,
        &serde_json::to_vec(&p).context("Plan sichern")?,
    )?;
    Ok(review(&p))
}
fn review(p: &Plan) -> Value {
    json!({"token":p.token,"source":p.source,"target":p.target,"create":p.create,"rows":p.rows,"files":p.items.len(),"skipped":p.rows.iter().filter(|r|r["action"]=="skip").count()})
}
fn verify(store: &Store, p: &Plan) -> Result<()> {
    if chrono::Utc::now().timestamp() - p.created > 1800 {
        return Err("Dieser Plan ist abgelaufen. Bitte erneut prüfen.".into());
    }
    if stamp(store, &store.profile(&p.source)?)? != p.source_stamp {
        return Err("Quellprofil geändert. Bitte erneut prüfen.".into());
    }
    if !p.create && json!(store.profile(&p.target.id)?) != json!(p.target) {
        return Err("Zielprofil geändert. Bitte erneut prüfen.".into());
    }
    if p.create
        && (store.state()?["profiles"]
            .as_array()
            .unwrap()
            .iter()
            .any(|v| v["id"] == p.target.id)
            || store.instance(&p.target.id)?.exists())
    {
        return Err("Zielprofil existiert inzwischen".into());
    }
    if stamp(store, &p.target)? != p.target_stamp {
        return Err("Ziel-Mods geändert. Bitte erneut prüfen.".into());
    }
    Ok(())
}
pub async fn commit(store: &Store, token: &str) -> Result<Value> {
    storage::safe_id(token)?;
    let path = relative(
        &store.root.join("cache/transfer-plans"),
        &format!("{token}.json"),
    )?;
    let p: Plan = serde_json::from_value(read_json(&path)?).context("Übernahmeplan lesen")?;
    let _target_lease = store.instance_lease(&p.target.id)?;
    verify(store, &p)?;
    let c = network::client()?;
    let mut staged = vec![];
    for item in &p.items {
        let cache = relative(
            &store.root.join("cache/mods"),
            &format!("{}.jar", item.hash),
        )?;
        if item.url.is_empty() {
            let bytes = std::fs::read(relative(
                &store.instance(&p.source)?.join("mods"),
                &item.source,
            )?)
            .context("Quell-Mod lesen")?;
            if network::hash(&bytes, "sha512")? != item.hash {
                return Err("Quell-Mod geändert".into());
            }
            atomic(&cache, &bytes)?;
        } else {
            network::download(&c, &item.url, &cache, Some(("sha512", &item.hash))).await?;
        }
        staged.push(cache);
    }
    let _lock = store
        .lock
        .lock()
        .map_err(|_| "Konfigurationssperre nicht verfügbar")?;
    verify(store, &p)?;
    let snapshot = if p.create {
        None
    } else {
        Some(recovery::create(store, &p.target.id, "pre-mod-transfer")?)
    };
    let root = store.instance(&p.target.id)?;
    let mods = root.join("mods");
    let old = meta(store, &p.target.id)?;
    let mut records = old.as_array().ok_or("Ziel-Metadaten ungültig")?.clone();
    let mut created_files = vec![];
    let result = (|| -> Result<()> {
        std::fs::create_dir_all(&mods).context("Zielordner erstellen")?;
        for (item, cache) in p.items.iter().zip(staged) {
            let bytes = std::fs::read(cache).context("Geprüfte Mod lesen")?;
            if network::hash(&bytes, "sha512")? != item.hash {
                return Err("Zwischengespeicherte Mod wurde verändert".into());
            }
            let replacing: Vec<String> = records
                .iter()
                .filter(|v| {
                    !item.metadata.is_null() && v["project_id"] == item.metadata["project_id"]
                })
                .filter_map(|v| v["filename"].as_str().map(String::from))
                .collect();
            for name in replacing.iter().chain(std::iter::once(&item.filename)) {
                for suffix in ["", ".disabled"] {
                    let oldpath = relative(&mods, &format!("{name}{suffix}"))?;
                    if oldpath.exists() {
                        if let Some(s) = &snapshot {
                            let dest = relative(
                                &store
                                    .root
                                    .join("backups/quarantine")
                                    .join(&p.target.id)
                                    .join(&s.id),
                                &format!("{name}{suffix}"),
                            )?;
                            std::fs::create_dir_all(dest.parent().unwrap())
                                .context("Alte Mod bewahren")?;
                            if !dest.exists() {
                                std::fs::rename(&oldpath, dest).context("Alte Mod bewahren")?;
                            }
                        }
                    }
                }
            }
            let filename = format!(
                "{}{}",
                item.filename,
                if item.enabled { "" } else { ".disabled" }
            );
            let dest = relative(&mods, &filename)?;
            atomic(&dest, &bytes)?;
            created_files.push(dest);
            records.retain(|v| {
                v["filename"] != item.filename
                    && (item.metadata.is_null() || v["project_id"] != item.metadata["project_id"])
            });
            if !item.metadata.is_null() {
                let mut record = item.metadata.clone();
                record["filename"] = json!(item.filename);
                records.push(record);
            }
        }
        let metadata_path = mods.join("installed.json");
        atomic(
            &metadata_path,
            &serde_json::to_vec_pretty(&records).context("Mod-Metadaten sichern")?,
        )?;
        created_files.push(metadata_path);
        if p.create {
            let mut state = store.state()?;
            state["profiles"]
                .as_array_mut()
                .unwrap()
                .push(json!(p.target));
            state["activeProfile"] = json!(p.target.id);
            storage::validate_state(&state)?;
            crate::export_presets(store, &state)?;
            atomic(
                &store.root.join("config/launcher.json"),
                &serde_json::to_vec_pretty(&state).context("Profil sichern")?,
            )?;
        }
        Ok(())
    })();
    if let Err(e) = result {
        if let Some(s) = snapshot {
            recovery::restore(store, &p.target.id, &s.id)?;
        } else {
            for f in created_files {
                let _ = std::fs::remove_file(f);
            }
            // This root was absent at validation and created only for this unregistered draft.
            let expected = store.root.join("instances").join(&p.target.id);
            if root == expected
                && !store.state()?["profiles"]
                    .as_array()
                    .unwrap()
                    .iter()
                    .any(|v| v["id"] == p.target.id)
                && !std::fs::symlink_metadata(&root)
                    .map(|m| m.file_type().is_symlink())
                    .unwrap_or(true)
            {
                let _ = std::fs::remove_dir_all(&root);
            }
        }
        return Err(format!(
            "Übernahme fehlgeschlagen, Mod-Dateien zurückgesetzt: {e}"
        ));
    }
    let _ = std::fs::remove_file(path);
    Ok(review(&p))
}
#[cfg(test)]
mod tests {
    use super::*;
    fn setup() -> (tempfile::TempDir, Store, Profile) {
        let t = tempfile::tempdir().unwrap();
        let s = Store::new(t.path().join("votex")).unwrap();
        atomic(
            &s.root.join("config/launcher.json"),
            include_bytes!("../../../../packages/shared-config/defaults.json"),
        )
        .unwrap();
        let p = s.profile("main").unwrap();
        (t, s, p)
    }
    #[test]
    fn stamp_detects_mod_and_profile_changes() {
        let (_t, s, p) = setup();
        let a = stamp(&s, &p).unwrap();
        atomic(&s.instance("main").unwrap().join("mods/local.jar"), b"one").unwrap();
        assert_ne!(a, stamp(&s, &p).unwrap());
        let mut next = p.clone();
        next.minecraft = "26.3".into();
        assert_ne!(stamp(&s, &p).unwrap(), stamp(&s, &next).unwrap());
    }
    #[tokio::test]
    async fn local_transfer_preserves_source_world_and_disabled_state() {
        let (_t, s, src) = setup();
        let mut target = src.clone();
        target.id = "target".into();
        target.name = "Target".into();
        let mut state = s.state().unwrap();
        state["profiles"]
            .as_array_mut()
            .unwrap()
            .push(json!(target));
        atomic(
            &s.root.join("config/launcher.json"),
            &serde_json::to_vec(&state).unwrap(),
        )
        .unwrap();
        atomic(
            &s.instance("main").unwrap().join("mods/a.jar.disabled"),
            b"local",
        )
        .unwrap();
        atomic(
            &s.instance("main").unwrap().join("saves/world/data"),
            b"world",
        )
        .unwrap();
        let hash = network::hash(b"local", "sha512").unwrap();
        let p = Plan {
            token: "test-plan".into(),
            created: chrono::Utc::now().timestamp(),
            source: "main".into(),
            target: target.clone(),
            create: false,
            source_stamp: stamp(&s, &src).unwrap(),
            target_stamp: stamp(&s, &target).unwrap(),
            items: vec![Item {
                filename: "a.jar".into(),
                source: "a.jar.disabled".into(),
                hash,
                url: String::new(),
                enabled: false,
                metadata: Value::Null,
            }],
            rows: vec![],
        };
        atomic(
            &s.root.join("cache/transfer-plans/test-plan.json"),
            &serde_json::to_vec(&p).unwrap(),
        )
        .unwrap();
        commit(&s, "test-plan").await.unwrap();
        assert_eq!(
            std::fs::read(s.instance("target").unwrap().join("mods/a.jar.disabled")).unwrap(),
            b"local"
        );
        assert!(s
            .instance("main")
            .unwrap()
            .join("mods/a.jar.disabled")
            .exists());
        assert_eq!(
            std::fs::read(s.instance("main").unwrap().join("saves/world/data")).unwrap(),
            b"world"
        );
        assert!(!s.instance("target").unwrap().join("saves").exists());
    }
    #[test]
    fn unsafe_download_metadata_is_rejected() {
        assert!(validate_file(&json!({"filename":"../x.jar","hashes":{"sha512":"a".repeat(128)},"url":"https://cdn.modrinth.com/x"})).is_err());
    }
    #[tokio::test]
    #[ignore = "Explicit online compatible Modrinth resolver smoke"]
    async fn live_resolve_target_version() {
        let (_t, _s, mut p) = setup();
        p.minecraft = "1.21.1".into();
        let versions = modrinth::resolve(&p, vec![("sodium".into(), false)], &json!([]))
            .await
            .unwrap();
        assert!(!versions.is_empty());
        for v in versions {
            assert!(v["game_versions"]
                .as_array()
                .unwrap()
                .iter()
                .any(|n| n == "1.21.1"));
            validate_file(version_file(&v).unwrap()).unwrap();
        }
    }
    #[test]
    fn confirmation_refuses_changed_source_without_mutating_target() {
        let (_t, s, src) = setup();
        let mut target = src.clone();
        target.id = "draft".into();
        let p = Plan {
            token: "stale".into(),
            created: chrono::Utc::now().timestamp(),
            source: "main".into(),
            target: target.clone(),
            create: true,
            source_stamp: stamp(&s, &src).unwrap(),
            target_stamp: stamp(&s, &target).unwrap(),
            items: vec![],
            rows: vec![],
        };
        atomic(
            &s.instance("main").unwrap().join("mods/new.jar"),
            b"changed",
        )
        .unwrap();
        assert!(verify(&s, &p).unwrap_err().contains("Quellprofil"));
        assert!(!s.instance("draft").unwrap().exists());
    }
    #[tokio::test]
    #[ignore = "Explicit online download and cross-version profile transfer smoke"]
    async fn live_cross_version_transfer_and_create() {
        let (_t, s, mut src) = setup();
        src.minecraft = "1.21.1".into();
        let mut state = s.state().unwrap();
        state["profiles"][0] = json!(src);
        atomic(
            &s.root.join("config/launcher.json"),
            &serde_json::to_vec(&state).unwrap(),
        )
        .unwrap();
        let versions = modrinth::resolve(&src, vec![("sodium".into(), false)], &json!([]))
            .await
            .unwrap();
        let v = versions
            .iter()
            .find(|v| v["project_id"] == "AANobbMI")
            .unwrap();
        let (filename, hash, url) = validate_file(version_file(v).unwrap()).unwrap();
        let c = network::client().unwrap();
        let source_file = s.instance("main").unwrap().join("mods").join(&filename);
        network::download(&c, &url, &source_file, Some(("sha512", &hash)))
            .await
            .unwrap();
        atomic(
            &s.instance("main").unwrap().join("saves/keep/level.dat"),
            b"world",
        )
        .unwrap();
        let mut target = src.clone();
        target.id = "updated-copy".into();
        target.minecraft = "1.21.4".into();
        let review = plan(&s, "main", &target, true).await.unwrap();
        assert!(review["rows"]
            .as_array()
            .unwrap()
            .iter()
            .any(|r| r["action"] == "update"));
        commit(&s, review["token"].as_str().unwrap()).await.unwrap();
        assert_eq!(s.profile("updated-copy").unwrap().minecraft, "1.21.4");
        assert_eq!(
            network::hash(&std::fs::read(source_file).unwrap(), "sha512").unwrap(),
            hash
        );
        assert!(!s.instance("updated-copy").unwrap().join("saves").exists());
        for v in meta(&s, "updated-copy").unwrap().as_array().unwrap() {
            assert!(v["game_versions"]
                .as_array()
                .unwrap()
                .iter()
                .any(|n| n == "1.21.4"));
            let file = version_file(v).unwrap();
            let bytes = std::fs::read(
                s.instance("updated-copy")
                    .unwrap()
                    .join("mods")
                    .join(v["filename"].as_str().unwrap()),
            )
            .unwrap();
            assert_eq!(
                network::hash(&bytes, "sha512").unwrap(),
                file["hashes"]["sha512"]
            );
        }
    }
    #[tokio::test]
    async fn partial_transfer_failure_restores_original_target_files() {
        let (_t, s, src) = setup();
        let mut target = src.clone();
        target.id = "rollback-target".into();
        let mut state = s.state().unwrap();
        state["profiles"]
            .as_array_mut()
            .unwrap()
            .push(json!(target));
        atomic(
            &s.root.join("config/launcher.json"),
            &serde_json::to_vec(&state).unwrap(),
        )
        .unwrap();
        let source = s.instance("main").unwrap().join("mods");
        let dest = s.instance(&target.id).unwrap().join("mods");
        atomic(&source.join("first.jar"), b"new first").unwrap();
        atomic(&source.join("second.jar"), b"second").unwrap();
        atomic(&dest.join("first.jar"), b"old first").unwrap();
        // A malformed externally edited metadata record causes failure after the first file write.
        let old = json!([{"project_id":"bad-project","filename":"../outside.jar"}]);
        atomic(
            &dest.join("installed.json"),
            &serde_json::to_vec(&old).unwrap(),
        )
        .unwrap();
        let items = vec![
            Item {
                filename: "first.jar".into(),
                source: "first.jar".into(),
                hash: network::hash(b"new first", "sha512").unwrap(),
                url: String::new(),
                enabled: true,
                metadata: Value::Null,
            },
            Item {
                filename: "second.jar".into(),
                source: "second.jar".into(),
                hash: network::hash(b"second", "sha512").unwrap(),
                url: String::new(),
                enabled: true,
                metadata: json!({"project_id":"bad-project"}),
            },
        ];
        let plan = Plan {
            token: "rollback-test".into(),
            created: chrono::Utc::now().timestamp(),
            source: "main".into(),
            target: target.clone(),
            create: false,
            source_stamp: stamp(&s, &src).unwrap(),
            target_stamp: stamp(&s, &target).unwrap(),
            items,
            rows: vec![],
        };
        atomic(
            &s.root.join("cache/transfer-plans/rollback-test.json"),
            &serde_json::to_vec(&plan).unwrap(),
        )
        .unwrap();
        assert!(commit(&s, "rollback-test")
            .await
            .unwrap_err()
            .contains("zurückgesetzt"));
        assert_eq!(std::fs::read(dest.join("first.jar")).unwrap(), b"old first");
        assert_eq!(read_json(&dest.join("installed.json")).unwrap(), old);
        assert!(!dest.join("second.jar").exists());
    }
}
