use crate::{
    network::hash,
    storage::{atomic, read_json, relative, Context, Profile, Result, Store},
};
use serde::{Deserialize, Serialize};
use serde_json::json;
use std::{collections::BTreeMap, fs, path::Path};
#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Snapshot {
    pub schema_version: u32,
    pub id: String,
    pub created: String,
    pub profile: Profile,
    pub kind: String,
    pub validation: String,
    pub files: BTreeMap<String, String>,
}
fn scope(s: &str) -> bool {
    s == "options.txt"
        || (s.starts_with("mods/")
            && (s.ends_with(".jar") || s.ends_with(".jar.disabled") || s == "mods/installed.json"))
        || (s.starts_with("config/") && !s.starts_with("config/votex/") && !s.ends_with(".tmp"))
}
fn collect(store: &Store, id: &str) -> Result<BTreeMap<String, String>> {
    let instance = store.instance(id)?;
    let mut files = BTreeMap::new();
    let mut config_size = 0u64;
    for folder in ["mods", "config", "options.txt"] {
        let path = relative(&instance, folder)?;
        if !path.exists() {
            continue;
        }
        for entry in walkdir::WalkDir::new(path).follow_links(false) {
            let e = entry.context("Inspect recovery files")?;
            if e.file_type().is_symlink() {
                return Err("Recovery refuses symbolic links".into());
            }
            if !e.file_type().is_file() {
                continue;
            }
            let rel = e
                .path()
                .strip_prefix(&instance)
                .context("Invalid recovery path")?
                .to_string_lossy()
                .replace('\\', "/");
            if !scope(&rel) {
                continue;
            }
            let size = e.metadata().context("Recovery file size")?.len();
            if rel.starts_with("config/") {
                config_size += size;
                if size > 2_097_152 || config_size > 33_554_432 {
                    return Err("Config snapshot exceeds safety limit (2 MiB/file, 32 MiB total). No partial snapshot was created.".into());
                }
            } else if size > 536_870_912 {
                return Err("Mod file exceeds 512 MiB recovery limit".into());
            }
            let bytes = fs::read(e.path()).context("Read recovery file")?;
            let sum = hash(&bytes, "sha512")?;
            let blob = store.root.join("backups/blobs").join(&sum);
            if !blob.exists() {
                atomic(&blob, &bytes)?;
            }
            files.insert(rel, sum);
        }
    }
    Ok(files)
}
fn create_manifest(store: &Store, id: &str, kind: &str) -> Result<Snapshot> {
    let snapshot = Snapshot {
        schema_version: 1,
        id: uuid::Uuid::new_v4().to_string(),
        created: chrono::Utc::now().to_rfc3339(),
        profile: store.profile(id)?,
        kind: kind.into(),
        validation: "unverified".into(),
        files: collect(store, id)?,
    };
    save(store, &snapshot)?;
    Ok(snapshot)
}
pub fn create(store: &Store, id: &str, kind: &str) -> Result<Snapshot> {
    let snapshot = create_manifest(store, id, kind)?;
    prune(store, id)?;
    Ok(snapshot)
}

/// Keep a live launch's pre-change and prepared snapshots addressable even when
/// installing a large pack creates more snapshots than the retention limit.
pub struct HeldSnapshot<'a> {
    pub snapshot: Snapshot,
    store: &'a Store,
    key: (String, String),
}
impl Drop for HeldSnapshot<'_> {
    fn drop(&mut self) {
        if let Ok(mut pins) = self.store.recovery_pins.lock() { pins.remove(&self.key); }
    }
}
pub fn hold<'a>(store: &'a Store, id: &str, kind: &str) -> Result<HeldSnapshot<'a>> {
    // Register under the same lock used during pruning; the newly created
    // manifest cannot be deleted between creation and acquiring its lease.
    let mut pins = store.recovery_pins.lock().map_err(|_| "Recovery lock unavailable")?;
    let snapshot = create_manifest(store, id, kind)?;
    let key = (id.to_owned(), snapshot.id.clone());
    pins.insert(key.clone());
    drop(pins);
    let held = HeldSnapshot { snapshot, store, key };
    prune(store, id)?;
    Ok(held)
}
fn save(store: &Store, s: &Snapshot) -> Result<()> {
    let dir = store.root.join("backups").join(&s.profile.id);
    let p = dir.join(format!("{}.json", s.id));
    atomic(
        &p,
        &serde_json::to_vec_pretty(s).context("Serialize snapshot")?,
    )
}
pub fn list(store: &Store, id: &str) -> Result<Vec<Snapshot>> {
    crate::storage::safe_id(id)?;
    let d = store.root.join("backups").join(id);
    if !d.exists() {
        return Ok(vec![]);
    }
    let mut values = vec![];
    for e in fs::read_dir(d).context("Read snapshots")? {
        let p = e.context("Read snapshot entry")?.path();
        if p.extension().and_then(|x| x.to_str()) == Some("json") {
            let s: Snapshot = serde_json::from_value(read_json(&p)?).context("Invalid snapshot")?;
            if s.profile.id != id || s.schema_version != 1 {
                return Err("Snapshot identity mismatch".into());
            }
            values.push(s)
        }
    }
    values.sort_by(|a, b| b.created.cmp(&a.created));
    Ok(values)
}
pub fn get(store: &Store, id: &str, snapshot: &str) -> Result<Snapshot> {
    crate::storage::safe_id(snapshot)?;
    list(store, id)?
        .into_iter()
        .find(|s| s.id == snapshot)
        .ok_or("Snapshot not found".into())
}
pub fn compare(store: &Store, s: &Snapshot) -> Result<Vec<String>> {
    let current = collect(store, &s.profile.id)?;
    let mut names: Vec<_> = current.keys().chain(s.files.keys()).cloned().collect();
    names.sort();
    names.dedup();
    let mut changes = names
        .into_iter()
        .filter(|n| current.get(n) != s.files.get(n))
        .collect::<Vec<_>>();
    if serde_json::to_value(store.profile(&s.profile.id)?).unwrap()
        != serde_json::to_value(&s.profile).unwrap()
    {
        changes.push("Profile: Minecraft, loader, Java or memory settings".into());
    }
    Ok(changes)
}
fn verify_blob(store: &Store, sum: &str) -> Result<Vec<u8>> {
    if sum.len() != 128 || !sum.bytes().all(|b| b.is_ascii_hexdigit()) {
        return Err("Invalid recovery checksum".into());
    }
    let b =
        fs::read(store.root.join("backups/blobs").join(sum)).context("Missing recovery content")?;
    if hash(&b, "sha512")? != sum {
        return Err("Recovery content checksum mismatch".into());
    }
    Ok(b)
}
fn apply(store: &Store, target: &Snapshot, quarantine: &Path) -> Result<()> {
    let instance = store.instance(&target.profile.id)?;
    // Validate ALL paths and blobs before the first mutation.
    for (name, sum) in &target.files {
        if !scope(name) {
            return Err("Snapshot contains a protected path".into());
        }
        relative(&instance, name)?;
        verify_blob(store, sum)?;
    }
    let current = collect(store, &target.profile.id)?;
    for name in current.keys() {
        if !target.files.contains_key(name) {
            let source = relative(&instance, name)?;
            let dest = relative(quarantine, name)?;
            fs::create_dir_all(dest.parent().unwrap()).context("Create recovery quarantine")?;
            fs::rename(source, dest).context("Preserve extra file in recovery quarantine")?;
        }
    }
    for (name, sum) in &target.files {
        atomic(&relative(&instance, name)?, &verify_blob(store, sum)?)?;
    }
    let mut state = store.state()?;
    let p = state["profiles"]
        .as_array_mut()
        .unwrap()
        .iter_mut()
        .find(|p| p["id"] == target.profile.id)
        .ok_or("Profile no longer exists")?;
    *p = serde_json::to_value(&target.profile).context("Restore profile")?;
    atomic(
        &store.root.join("config/launcher.json"),
        &serde_json::to_vec_pretty(&state).context("Save restored profile")?,
    )?;
    if collect(store, &target.profile.id)? != target.files {
        return Err("Restoration validation failed".into());
    }
    Ok(())
}
pub fn restore(store: &Store, id: &str, snapshot: &str) -> Result<Snapshot> {
    let target = get(store, id, snapshot)?;
    for (name, sum) in &target.files {
        if !scope(name) {
            return Err("Protected recovery path".into());
        }
        relative(&store.instance(id)?, name)?;
        verify_blob(store, sum)?;
    }
    let previous = create(store, id, "pre-restore")?;
    let quarantine = store
        .root
        .join("backups/quarantine")
        .join(id)
        .join(uuid::Uuid::new_v4().to_string());
    match apply(store, &target, &quarantine) {
        Ok(()) => Ok(previous),
        Err(error) => {
            let rollback = apply(store, &previous, &quarantine.join("failed-restore"));
            match rollback{Ok(())=>Err(format!("Restore failed; original state restored: {error}")),Err(e)=>Err(format!("Restore failed: {error}. Rollback also failed: {e}. Pre-restore snapshot {} is preserved.",previous.id))}
        }
    }
}
pub fn validate(store: &Store, id: &str, snapshot: &str, status: &str) -> Result<()> {
    let mut s = get(store, id, snapshot)?;
    s.validation = status.into();
    if status == "confirmed-working" {
        s.kind = "last-known-working".into()
    }
    save(store, &s)
}
pub fn latest_working(store: &Store, id: &str) -> Result<Option<Snapshot>> {
    Ok(list(store, id)?
        .into_iter()
        .find(|s| s.validation == "confirmed-working"))
}
pub fn delete(store: &Store, id: &str, snapshot: &str) -> Result<()> {
    let pins = store.recovery_pins.lock().map_err(|_| "Recovery lock unavailable")?;
    if pins.contains(&(id.to_owned(), snapshot.to_owned())) {
        return Err("A snapshot used by a running launch cannot be deleted.".into());
    }
    let s = get(store, id, snapshot)?;
    if latest_working(store, id)?
        .map(|w| w.id == s.id)
        .unwrap_or(false)
    {
        return Err("The protected last known working snapshot cannot be deleted.".into());
    }
    fs::remove_file(
        store
            .root
            .join("backups")
            .join(id)
            .join(format!("{}.json", s.id)),
    )
    .context("Delete snapshot manifest")
}
fn prune(store: &Store, id: &str) -> Result<()> {
    let pins = store.recovery_pins.lock().map_err(|_| "Recovery lock unavailable")?;
    let retention = store.state()?["retention"].as_u64().unwrap_or(12) as usize;
    let snapshots = list(store, id)?;
    let protected = latest_working(store, id)?.map(|s| s.id);
    for s in snapshots.iter().skip(retention) {
        if Some(&s.id) != protected.as_ref() && !pins.contains(&(id.to_owned(), s.id.clone())) {
            fs::remove_file(
                store
                    .root
                    .join("backups")
                    .join(id)
                    .join(format!("{}.json", s.id)),
            )
            .context("Prune old snapshot")?;
        }
    }
    Ok(())
}
pub fn usage(store: &Store) -> Result<serde_json::Value> {
    let mut bytes = 0;
    for e in walkdir::WalkDir::new(store.root.join("backups")).follow_links(false) {
        let e = e.context("Read backup usage")?;
        if e.file_type().is_file() {
            bytes += e.metadata().context("Backup file size")?.len();
        }
    }
    Ok(json!({"bytes":bytes}))
}
pub fn gc(store: &Store) -> Result<u64> {
    let mut used = std::collections::HashSet::new();
    for e in fs::read_dir(store.root.join("backups")).context("Read recovery store")? {
        let e = e.context("Read recovery directory")?;
        let id = e.file_name().to_string_lossy().to_string();
        if !e
            .file_type()
            .context("Read recovery directory type")?
            .is_dir()
            || id == "blobs"
            || id == "quarantine"
        {
            continue;
        }
        for s in list(store, &id)? {
            used.extend(s.files.into_values());
        }
    }
    let d = store.root.join("backups/blobs");
    let mut count = 0;
    if d.exists() {
        for e in fs::read_dir(d).context("Read blob store")? {
            let e = e.context("Read blob")?;
            let name = e.file_name().to_string_lossy().to_string();
            if name.len() == 128 && !used.contains(&name) {
                fs::remove_file(e.path()).context("Remove unreferenced blob")?;
                count += 1;
            }
        }
    }
    Ok(count)
}
#[cfg(test)]
mod tests {
    use super::*;
    fn setup() -> (tempfile::TempDir, Store) {
        let temp = tempfile::tempdir().unwrap();
        let store = Store::new(temp.path().join("VotexClient")).unwrap();
        let state: serde_json::Value = serde_json::from_str(include_str!(
            "../../../../packages/shared-config/defaults.json"
        ))
        .unwrap();
        atomic(
            &store.root.join("config/launcher.json"),
            &serde_json::to_vec(&state).unwrap(),
        )
        .unwrap();
        (temp, store)
    }
    #[test]
    fn round_trip_preserves_worlds_and_recent_files() {
        let (_temp, s) = setup();
        let instance = s.instance("main").unwrap();
        atomic(&instance.join("mods/a.jar"), b"old mod").unwrap();
        atomic(&instance.join("config/options.json"), b"old config").unwrap();
        atomic(
            &instance.join("saves/world/level.dat"),
            b"world must survive",
        )
        .unwrap();
        let point = create(&s, "main", "manual").unwrap();
        atomic(&instance.join("mods/a.jar"), b"updated mod").unwrap();
        atomic(&instance.join("mods/new.jar"), b"new mod").unwrap();
        atomic(&instance.join("config/options.json"), b"new config").unwrap();
        assert!(compare(&s, &point)
            .unwrap()
            .contains(&"mods/new.jar".into()));
        restore(&s, "main", &point.id).unwrap();
        assert_eq!(fs::read(instance.join("mods/a.jar")).unwrap(), b"old mod");
        assert_eq!(
            fs::read(instance.join("config/options.json")).unwrap(),
            b"old config"
        );
        assert!(!instance.join("mods/new.jar").exists());
        assert_eq!(
            fs::read(instance.join("saves/world/level.dat")).unwrap(),
            b"world must survive"
        );
        let paths = walkdir::WalkDir::new(s.root.join("backups/quarantine"))
            .into_iter()
            .filter_map(|e| e.ok())
            .filter(|e| e.file_name() == "new.jar")
            .count();
        assert_eq!(paths, 1);
    }
    #[test]
    fn corrupt_blob_is_rejected_before_mutation() {
        let (_t, s) = setup();
        let file = s.instance("main").unwrap().join("mods/a.jar");
        atomic(&file, b"old").unwrap();
        let point = create(&s, "main", "manual").unwrap();
        let sum = point.files.get("mods/a.jar").unwrap();
        atomic(&s.root.join("backups/blobs").join(sum), b"corrupt").unwrap();
        atomic(&file, b"current").unwrap();
        assert!(restore(&s, "main", &point.id).is_err());
        assert_eq!(fs::read(&file).unwrap(), b"current");
    }
    #[test]
    fn deduplicates_and_protects_working_point() {
        let (_t, s) = setup();
        atomic(
            &s.instance("main").unwrap().join("mods/a.jar"),
            b"same content",
        )
        .unwrap();
        let point = create(&s, "main", "manual").unwrap();
        validate(&s, "main", &point.id, "confirmed-working").unwrap();
        let mut state = s.state().unwrap();
        state["retention"] = json!(2);
        atomic(
            &s.root.join("config/launcher.json"),
            &serde_json::to_vec(&state).unwrap(),
        )
        .unwrap();
        for _ in 0..5 {
            create(&s, "main", "manual").unwrap();
        }
        assert!(get(&s, "main", &point.id).is_ok());
        assert!(delete(&s, "main", &point.id).is_err());
        assert_eq!(
            fs::read_dir(s.root.join("backups/blobs")).unwrap().count(),
            1
        );
    }
    #[test]
    fn a_large_install_keeps_active_launch_snapshots_and_validates_the_prepared_pack() {
        let (_t, store) = setup();
        let mut state = store.state().unwrap(); state["retention"] = json!(2);
        atomic(&store.root.join("config/launcher.json"), &serde_json::to_vec(&state).unwrap()).unwrap();
        let instance = store.instance("main").unwrap();
        atomic(&instance.join("mods/current.jar"), b"before installation").unwrap();
        let before = hold(&store, "main", "automatic").unwrap();
        let before_id = before.snapshot.id.clone();
        for index in 0..18 {
            create(&store, "main", "before-mod-install").unwrap();
            atomic(&instance.join(format!("mods/addon-{index}.jar")), &[index]).unwrap();
        }
        assert!(get(&store, "main", &before_id).is_ok());
        assert!(delete(&store, "main", &before_id).is_err());
        let prepared = hold(&store, "main", "prepared-launch").unwrap();
        let prepared_id = prepared.snapshot.id.clone();
        assert_eq!(prepared.snapshot.files.len(), 19);
        for _ in 0..5 { create(&store, "main", "manual").unwrap(); }
        validate(&store, "main", &prepared_id, "confirmed-working").unwrap();
        assert_eq!(latest_working(&store, "main").unwrap().unwrap().files, prepared.snapshot.files);
        drop(before); drop(prepared);
        create(&store, "main", "manual").unwrap();
        assert!(get(&store, "main", &before_id).is_err());
        assert!(get(&store, "main", &prepared_id).is_ok());
        assert!(delete(&store, "main", &prepared_id).is_err());
    }
    #[test]
    fn malicious_manifest_paths_are_rejected() {
        let (_t, s) = setup();
        let mut point = create(&s, "main", "manual").unwrap();
        point
            .files
            .insert("mods/../../saves/world.dat".into(), "a".repeat(128));
        save(&s, &point).unwrap();
        assert!(restore(&s, "main", &point.id).is_err());
    }
    #[test]
    fn protects_user_files() {
        for s in [
            "saves/world/level.dat",
            "servers.dat",
            "screenshots/a.png",
            "accounts/token",
            "config/votex/modules.json",
        ] {
            assert!(!scope(s))
        }
        assert!(scope("mods/a.jar.disabled"));
        assert!(scope("config/sodium-options.json"));
    }
}
