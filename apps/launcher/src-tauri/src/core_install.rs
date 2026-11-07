use crate::{
    recovery,
    storage::{atomic, relative, Context, Result, Store},
};
use std::io::{Cursor, Read};
use tauri::Manager;
pub const VERSION: &str = "0.4.1";
pub fn bundled(store: &Store, app: &tauri::AppHandle, id: &str, force: bool) -> Result<()> {
    let p = store.profile(id)?;
    if p.loader != "fabric" || p.minecraft != "26.2" {
        return Err("Votex Core benötigt Fabric für Minecraft 26.2".into());
    }
    let path = app
        .path()
        .resource_dir()
        .context("Locate Core resources")?
        .join(format!("resources/votex-client-core-{VERSION}.jar"));
    let bytes = std::fs::read(path).context("Read bundled Votex Core")?;
    apply(store, id, &bytes, force)
}
fn identity(bytes: &[u8]) -> Result<()> {
    let mut zip = zip::ZipArchive::new(Cursor::new(bytes)).context("Invalid Core JAR")?;
    let mut data = Vec::new();
    zip.by_name("fabric.mod.json")
        .context("Missing Core metadata")?
        .by_ref()
        .take(64_000)
        .read_to_end(&mut data)
        .context("Read Core metadata")?;
    let value: serde_json::Value =
        serde_json::from_slice(&data).context("Invalid Core metadata")?;
    if value["id"] != "votex_client" {
        return Err("Unexpected mod in Core filename; preserved without replacement".into());
    }
    Ok(())
}
pub fn apply(store: &Store, id: &str, bytes: &[u8], force: bool) -> Result<()> {
    identity(bytes)?;
    let root = relative(&store.instance(id)?, "mods")?;
    std::fs::create_dir_all(&root).context("Create Core destination")?;
    let mut old = Vec::new();
    let mut enabled = force;
    for e in std::fs::read_dir(&root).context("Inspect existing Core")? {
        let e = e.context("Read Core file")?;
        let name = e.file_name().to_string_lossy().to_string();
        if name.starts_with("votex-client-core-")
            && (name.ends_with(".jar") || name.ends_with(".jar.disabled"))
        {
            let path = relative(&root, &name)?;
            identity(&std::fs::read(&path).context("Inspect old Core identity")?)?;
            enabled |= name.ends_with(".jar");
            old.push((name, path));
        }
    }
    if old.is_empty() {
        enabled = true;
    }
    let filename = format!(
        "votex-client-core-{VERSION}.jar{}",
        if enabled { "" } else { ".disabled" }
    );
    let target = relative(&root, &filename)?;
    if old.len() == 1
        && old[0].0 == filename
        && std::fs::read(&target).context("Read installed Core")? == bytes
    {
        return Ok(());
    }
    let before = recovery::create(store, id, "pre-core-installation")?;
    let result = (|| -> Result<()> {
        for (name, path) in old {
            let backup = store
                .root
                .join("backups/quarantine")
                .join(id)
                .join(&before.id)
                .join(name);
            std::fs::create_dir_all(backup.parent().unwrap()).context("Preserve old Core")?;
            std::fs::rename(path, backup).context("Preserve old Core")?;
        }
        atomic(&target, bytes)
    })();
    if let Err(e) = result {
        recovery::restore(store, id, &before.id)?;
        return Err(format!("Core update rolled back: {e}"));
    }
    Ok(())
}
#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    fn jar(version: &str) -> Vec<u8> {
        let mut z = zip::ZipWriter::new(Cursor::new(Vec::new()));
        z.start_file("fabric.mod.json", zip::write::SimpleFileOptions::default())
            .unwrap();
        write!(z, "{{\"id\":\"votex_client\",\"version\":\"{version}\"}}").unwrap();
        z.finish().unwrap().into_inner()
    }
    #[test]
    fn update_preserves_old_core_and_avoids_duplicate_mod_ids() {
        let t = tempfile::tempdir().unwrap();
        let s = Store::new(t.path().join("votex")).unwrap();
        atomic(
            &s.root.join("config/launcher.json"),
            include_bytes!("../../../../packages/shared-config/defaults.json"),
        )
        .unwrap();
        let root = s.instance("main").unwrap().join("mods");
        atomic(&root.join("votex-client-core-0.1.0.jar"), &jar("0.1.0")).unwrap();
        apply(&s, "main", &jar(VERSION), false).unwrap();
        assert!(!root.join("votex-client-core-0.1.0.jar").exists());
        assert!(root
            .join(format!("votex-client-core-{VERSION}.jar"))
            .exists());
        assert!(walkdir::WalkDir::new(s.root.join("backups/quarantine"))
            .into_iter()
            .filter_map(std::result::Result::ok)
            .any(|e| e.file_name() == "votex-client-core-0.1.0.jar"));
    }
    #[test]
    fn automatic_update_keeps_a_disabled_core_disabled() {
        let t = tempfile::tempdir().unwrap();
        let s = Store::new(t.path().join("votex")).unwrap();
        atomic(
            &s.root.join("config/launcher.json"),
            include_bytes!("../../../../packages/shared-config/defaults.json"),
        )
        .unwrap();
        atomic(
            &s.instance("main")
                .unwrap()
                .join("mods/votex-client-core-0.1.0.jar.disabled"),
            &jar("0.1.0"),
        )
        .unwrap();
        apply(&s, "main", &jar(VERSION), false).unwrap();
        assert!(s
            .instance("main")
            .unwrap()
            .join(format!("mods/votex-client-core-{VERSION}.jar.disabled"))
            .exists());
    }

    #[test]
    #[ignore = "Explicit two real Core artifacts; isolated filesystem only, no EXE or game launch"]
    fn real_same_version_update_preserves_settings_disable_choice_and_recovery() {
        let previous = std::fs::read(std::env::var("VOTEX_UPDATE_PREVIOUS_JAR").expect("Previous verified Core required")).unwrap();
        let current = std::fs::read(std::env::var("VOTEX_UPDATE_CURRENT_JAR").expect("Current native-tested Core required")).unwrap();
        identity(&previous).unwrap();
        identity(&current).unwrap();
        assert!(previous != current, "This check must exercise changed bytes under the same version");
        for bytes in [&previous, &current] {
            let mut zip = zip::ZipArchive::new(Cursor::new(bytes)).unwrap();
            let mut metadata = String::new();
            zip.by_name("fabric.mod.json").unwrap().read_to_string(&mut metadata).unwrap();
            assert_eq!(serde_json::from_str::<serde_json::Value>(&metadata).unwrap()["version"], VERSION);
        }
        for enabled in [true, false] {
            let temp = tempfile::tempdir().unwrap();
            let store = Store::new(temp.path().join("votex")).unwrap();
            let config = include_bytes!("../../../../packages/shared-config/defaults.json");
            atomic(&store.root.join("config/launcher.json"), config).unwrap();
            let mut restored_state: serde_json::Value = serde_json::from_slice(config).unwrap();
            restored_state["profiles"][0] = serde_json::to_value(store.profile("main").unwrap()).unwrap();
            let instance = store.instance("main").unwrap();
            let fixtures: &[(&str, &[u8])] = &[
                ("config/votex/modules.json", br#"{"schemaVersion":1,"enabled":{"fps":false,"chat":true},"positions":{"fps":{"x":180,"y":40}},"appearance":{"fps":{"scale":130,"background":50,"accent":false}}}"#),
                ("config/votex/ingame-look.json", br#"{"schemaVersion":1,"nametag":"Mein Votex","equipment":{"head":"top-hat"},"wheel":["wave","sit","dance"]}"#),
                ("config/votex/theme.json", br##"{"schemaVersion":1,"hover":false,"proximityEffect":false,"reducedMotion":true,"accent":"#7ab2ff"}"##),
                ("options.txt", b"guiScale:3\nfov:0.5\nmaxFps:165\nkey_key.forward:key.keyboard.w\n"),
                ("saves/Original-QA-World/level.dat", b"Original QA world bytes; never opened"),
            ];
            for (name, bytes) in fixtures { atomic(&instance.join(name), bytes).unwrap(); }
            let filename = format!("votex-client-core-{VERSION}.jar");
            let old_name = format!("{filename}{}", if enabled { "" } else { ".disabled" });
            atomic(&instance.join("mods").join(&old_name), &previous).unwrap();
            apply(&store, "main", &current, false).unwrap();
            assert!(std::fs::read(instance.join("mods").join(&old_name)).unwrap() == current, "Installed artifact differs");
            let snapshots = recovery::list(&store, "main").unwrap();
            assert_eq!(snapshots.len(), 1);
            let snapshot = &snapshots[0];
            assert!(std::fs::read(store.root.join("backups/quarantine/main").join(&snapshot.id).join(&old_name)).unwrap() == previous, "Backup differs");
            apply(&store, "main", &current, false).unwrap();
            assert_eq!(recovery::list(&store, "main").unwrap().len(), 1, "Unchanged Core update must be inert");
            assert!(std::fs::read(store.root.join("config/launcher.json")).unwrap() == config, "Core update rewrote launcher settings");
            if enabled {
                recovery::restore(&store, "main", &snapshot.id).unwrap();
                assert!(std::fs::read(instance.join("mods").join(&filename)).unwrap() == previous, "Recovery did not restore original artifact");
                assert_eq!(store.state().unwrap(), restored_state, "Recovery changed values beyond normal profile defaults");
            } else {
                assert!(!instance.join("mods").join(&filename).exists());
                apply(&store, "main", &current, true).unwrap();
                assert!(std::fs::read(instance.join("mods").join(&filename)).unwrap() == current, "Explicit enable changed the artifact");
                assert!(!instance.join("mods").join(&old_name).exists());
                assert!(std::fs::read(store.root.join("config/launcher.json")).unwrap() == config, "Explicit Core enable rewrote launcher settings");
            }
            for (name, bytes) in fixtures { assert!(std::fs::read(instance.join(name)).unwrap() == *bytes, "Changed {name}"); }
        }
        println!("VOTEX_REAL_CORE_UPDATE_PASS previousSha256={} currentSha256={} sameVersion={} settingsPreserved=true disabledPreserved=true explicitEnable=true idempotent=true realRecovery=true gameLaunch=false",
            crate::network::hash(&previous, "sha256").unwrap(), crate::network::hash(&current, "sha256").unwrap(), VERSION);
    }
}
