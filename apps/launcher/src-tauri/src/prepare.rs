use crate::{
    modrinth,
    storage::{atomic, read_json, relative, Context, Result, Store},
};
use serde_json::json;
use std::io::{Cursor, Write};
pub async fn profile(store: &Store, id: &str, app: &tauri::AppHandle) -> Result<()> {
    profile_with_progress(store, id, Some(app), None).await
}
pub(crate) async fn profile_with_progress(
    store: &Store,
    id: &str,
    app: Option<&tauri::AppHandle>,
    bundled_core: Option<&[u8]>,
) -> Result<()> {
    let p = store.profile(id)?;
    if p.performance_mods && !matches!(p.loader.as_str(), "fabric" | "quilt") {
        return Err("Performance-Vorbereitung benötigt Fabric oder Quilt".into());
    }
    if p.shader_enabled && p.loader != "fabric" {
        return Err("Die Iris-Vorbereitung benötigt ein Fabric-Profil".into());
    }
    let receipt = store.instance(id)?.join("config/votex/prepared.json");
    let key = json!({"minecraft":p.minecraft,"loader":p.loader,"performanceMods":p.performance_mods,"shaderEnabled":p.shader_enabled,"softLightVersion":1,"coreVersion":crate::core_install::VERSION});
    let previous = read_json(&receipt).unwrap_or(json!({}));
    let already = previous == key;
    // A release candidate may contain a corrected Core with the same semantic version.
    // Verify the actual bundled bytes on launch; identical/disabled cores remain unchanged.
    if p.loader == "fabric"
        && p.minecraft == "26.2"
        && !store.instance(id)?.join("votex-modpack.json").exists()
    {
        if !already {
            modrinth::install_with_progress(store, id, "fabric-api", app).await?;
        }
        if let Some(app) = app {
            crate::core_install::bundled(store, app, id, false)?;
        } else if let Some(bytes) = bundled_core {
            crate::core_install::apply(store, id, bytes, false)?;
        } else {
            return Err("Bundled Votex Core is unavailable for isolated preparation".into());
        }
        // Voice is an optional first-time companion. A separate receipt respects user
        // removal/disable choices and must not invalidate performance/shader preparation.
        let guard_ready = voice_guard_ready(&store.instance(id)?).unwrap_or(false);
        if p.starter_mods.is_none() {
            if let Err(error) = voice_once(store, id, guard_ready, || async {
                modrinth::install_pinned_with_progress(store, id, VOICE_PROJECT, VOICE_VERSION, app)
                    .await
                    .map(|_| ())
            })
            .await
            {
                crate::minecraft::event_optional(app, "voice-warning", &format!("Sprachchat konnte nicht vorbereitet werden; der Spielstart bleibt verfügbar. {error}"), 0, 0);
            }
        }
    }
    crate::starter::prepare_with_progress(
        store,
        id,
        app,
        voice_guard_ready(&store.instance(id)?).unwrap_or(false),
    )
    .await?;
    if p.starter_mods.is_some() {
        atomic(
            &receipt,
            &serde_json::to_vec(&key).context("Save new-profile preparation")?,
        )?;
    }
    if !already && p.starter_mods.is_none() {
        if p.performance_mods {
            modrinth::install_with_progress(store, id, "sodium", app).await?;
            modrinth::install_with_progress(store, id, "lithium", app).await?;
        }
        if p.shader_enabled {
            modrinth::install_with_progress(store, id, "iris", app).await?;
            let mut zip = zip::ZipWriter::new(Cursor::new(Vec::new()));
            let options = zip::write::SimpleFileOptions::default()
                .compression_method(zip::CompressionMethod::Deflated);
            for (name,source) in [("shaders/final.vsh","#version 120\nvarying vec2 texcoord;\nvoid main(){gl_Position=ftransform();texcoord=gl_MultiTexCoord0.xy;}\n"),("shaders/final.fsh","#version 120\nuniform sampler2D colortex0;\nvarying vec2 texcoord;\nvoid main(){vec3 color=texture2D(colortex0,texcoord).rgb;float l=dot(color,vec3(0.2126,0.7152,0.0722));color=mix(vec3(l),color,1.035);color=pow(max(color,vec3(0.0)),vec3(0.985))*vec3(1.012,1.004,0.995);float edge=smoothstep(0.25,0.8,length(texcoord-0.5));gl_FragColor=vec4(clamp(color*(1.0-edge*0.065),0.0,1.0),1.0);}\n"),("README.txt","Votex SoftLight 1.0 - Original Votex shader, MIT License. Subtle color grading and vignette. Requires Iris. No dynamic shadows or ray tracing.\n")]{zip.start_file(name,options).context("Create SoftLight entry")?;zip.write_all(source.as_bytes()).context("Write SoftLight shader")?;}
            atomic(
                &store.instance(id)?.join("shaderpacks/Votex-SoftLight.zip"),
                &zip.finish()
                    .context("Finish SoftLight shader")?
                    .into_inner(),
            )?;
        }
        atomic(
            &receipt,
            &serde_json::to_vec(&key).context("Save preparation receipt")?,
        )?;
    }
    let path = store.instance(id)?.join("config/iris.properties");
    if !already
        && p.starter_mods.is_none()
        && (p.shader_enabled
            || (previous["shaderEnabled"] == true
                && std::fs::read_to_string(&path)
                    .unwrap_or_default()
                    .lines()
                    .any(|l| l == "shaderPack=Votex-SoftLight.zip")))
    {
        let old = std::fs::read_to_string(&path).unwrap_or_default();
        let mut lines: Vec<String> = old
            .lines()
            .filter(|l| {
                !l.starts_with("enableShaders=")
                    && (!p.shader_enabled || !l.starts_with("shaderPack="))
            })
            .map(str::to_string)
            .collect();
        lines.push(format!("enableShaders={}", p.shader_enabled));
        if p.shader_enabled {
            lines.push("shaderPack=Votex-SoftLight.zip".into());
        }
        atomic(&path, format!("{}\n", lines.join("\n")).as_bytes())?;
    }
    Ok(())
}

// The internal SVC adapter is deliberately pinned. The capability must be present in
// the actual enabled Core JAR; a launcher build cannot advertise readiness for an old Core.
const VOICE_PROJECT: &str = "9eGKb6K1";
const VOICE_VERSION: &str = "Ls232EsW";
const VOICE_NATIVE_VERSION: &str = "2.6.24+26.2";
const VOICE_RECEIPT: &str = "config/votex/voice-setup.json";
#[derive(Debug, PartialEq)]
enum VoicePlan {
    Ineligible,
    AlreadyHandled,
    Record(&'static str),
    Install,
}

pub(crate) fn voice_guard_ready(root: &std::path::Path) -> Result<bool> {
    use std::io::Read;
    let mods = relative(root, "mods")?;
    if !mods.exists() {
        return Ok(false);
    }
    let mut core_count = 0usize;
    let mut ready = false;
    for (index, entry) in std::fs::read_dir(&mods)
        .context("Voice-Freigabe prüfen")?
        .enumerate()
    {
        if index >= 1000 {
            return Ok(false);
        }
        let entry = entry.context("Core-Eintrag lesen")?;
        let filename = entry.file_name().to_string_lossy().to_string();
        if !filename.to_ascii_lowercase().ends_with(".jar") {
            continue;
        }
        let path = relative(&mods, &filename)?;
        if !path.is_file() || path.metadata().context("Core-Dateigröße prüfen")?.len() > 50_000_000
        {
            continue;
        }
        if voice_jar_id(&path).as_deref() != Some("votex_client") {
            continue;
        }
        core_count += 1;
        let mut archive =
            zip::ZipArchive::new(std::fs::File::open(&path).context("Aktiven Core öffnen")?)
                .context("Aktiven Core prüfen")?;
        let capability = {
            let Ok(mut entry) = archive.by_name("assets/votex_client/capabilities.json") else {
                continue;
            };
            if entry.size() > 4096 {
                continue;
            }
            let mut data = Vec::new();
            entry
                .by_ref()
                .take(4097)
                .read_to_end(&mut data)
                .context("Voice-Fähigkeit lesen")?;
            if data.len() > 4096 {
                continue;
            }
            serde_json::from_slice::<serde_json::Value>(&data).ok()
        };
        let Some(capability) = capability else {
            continue;
        };
        ready = capability["schemaVersion"] == 1
            && capability["voiceStartMicConsentGuard"] == 1
            && capability["voicechatVersion"] == VOICE_NATIVE_VERSION
            && [
                "dev/votex/client/VoiceMicGate.class",
                "dev/votex/client/voice/PinnedVoiceConsent.class",
                "dev/votex/client/voice/VoiceConsentPlugin.class",
            ]
            .iter()
            .all(|name| {
                archive
                    .by_name(name)
                    .map(|entry| entry.size() > 0 && entry.size() < 100_000)
                    .unwrap_or(false)
            });
    }
    Ok(core_count == 1 && ready)
}

/// Runs only through ordinary `prepare::profile`; Safe Mode passes prepare=false.
/// Install failures do not get a receipt and can be retried on the next ordinary launch.
async fn voice_once<F, Work>(
    store: &Store,
    id: &str,
    consent_guard_ready: bool,
    install: F,
) -> Result<()>
where
    F: FnOnce() -> Work,
    Work: std::future::Future<Output = Result<()>>,
{
    if !consent_guard_ready {
        return Ok(());
    }
    let plan = voice_plan(store, id)?;
    let outcome = match plan {
        VoicePlan::Ineligible | VoicePlan::AlreadyHandled => return Ok(()),
        VoicePlan::Record(outcome) => outcome,
        VoicePlan::Install => {
            install().await?;
            if !voice_mods(&store.instance(id)?)?.1 {
                return Err(
                    "Simple Voice Chat wurde nach der Vorbereitung nicht als Fabric-Mod gefunden"
                        .into(),
                );
            }
            "installed"
        }
    };
    let root = store.instance(id)?;
    let path = relative(&root, VOICE_RECEIPT)?;
    atomic(
        &path,
        &serde_json::to_vec_pretty(&json!({
            "schemaVersion": 1, "projectId": VOICE_PROJECT, "handled": true,
            "outcome": outcome, "checkedAt": chrono::Utc::now().to_rfc3339(),
            "policy": "first-time-only; preserve existing disabled jars and later removals"
        }))
        .context("Sprachchat-Vorbereitung speichern")?,
    )
}
fn voice_plan(store: &Store, id: &str) -> Result<VoicePlan> {
    let p = store.profile(id)?;
    let root = store.instance(id)?;
    if p.loader != "fabric"
        || p.minecraft != "26.2"
        || relative(&root, "votex-modpack.json")?.exists()
    {
        return Ok(VoicePlan::Ineligible);
    }
    let receipt = relative(&root, VOICE_RECEIPT)?;
    if receipt.exists() {
        let value = voice_json(&receipt, 8 * 1024)?;
        if value["schemaVersion"] != 1
            || value["projectId"] != VOICE_PROJECT
            || value["handled"] != true
            || !matches!(
                value["outcome"].as_str(),
                Some("installed" | "kept-existing" | "kept-user-choice")
            )
        {
            return Err(
                "Unbekannter Sprachchat-Vorbereitungsbeleg; vorhandene Dateien bleiben unverändert"
                    .into(),
            );
        }
        return Ok(VoicePlan::AlreadyHandled);
    }
    let (core_active, voice_present) = voice_mods(&root)?;
    if !core_active {
        return Ok(VoicePlan::Ineligible);
    }
    if voice_present {
        return Ok(VoicePlan::Record("kept-existing"));
    }
    // A previously recorded download removed from disk is an explicit user choice too.
    let metadata = relative(&root, "mods/installed.json")?;
    if metadata.exists() {
        let data = voice_json(&metadata, 4 * 1024 * 1024)?;
        let rows = data
            .as_array()
            .ok_or("Ungültige Mod-Metadaten für Sprachchat-Vorbereitung")?;
        if rows.iter().any(|row| {
            matches!(
                row["project_id"].as_str(),
                Some(VOICE_PROJECT | "simple-voice-chat")
            )
        }) {
            return Ok(VoicePlan::Record("kept-user-choice"));
        }
    }
    if voice_change_choice(&root)? {
        return Ok(VoicePlan::Record("kept-user-choice"));
    }
    Ok(VoicePlan::Install)
}
fn voice_json(path: &std::path::Path, limit: u64) -> Result<serde_json::Value> {
    use std::io::Read;
    if std::fs::metadata(path)
        .context("Sprachchat-Vorbereitung prüfen")?
        .len()
        > limit
    {
        return Err("Sprachchat-Vorbereitungsdaten überschreiten das Limit".into());
    }
    let mut bytes = Vec::new();
    std::fs::File::open(path)
        .context("Sprachchat-Vorbereitung öffnen")?
        .take(limit + 1)
        .read_to_end(&mut bytes)
        .context("Sprachchat-Vorbereitung lesen")?;
    if bytes.len() as u64 > limit {
        return Err("Sprachchat-Vorbereitungsdaten überschreiten das Limit".into());
    }
    serde_json::from_slice(&bytes).context("Ungültige Sprachchat-Vorbereitungsdaten")
}
fn voice_jar_id(path: &std::path::Path) -> Option<String> {
    use std::io::Read;
    if std::fs::metadata(path).ok()?.len() > 128 * 1024 * 1024 {
        return None;
    }
    let mut archive = zip::ZipArchive::new(std::fs::File::open(path).ok()?).ok()?;
    let info = archive.by_name("fabric.mod.json").ok()?;
    if info.size() > 64 * 1024 {
        return None;
    }
    let mut bytes = Vec::new();
    info.take(64 * 1024 + 1).read_to_end(&mut bytes).ok()?;
    if bytes.len() > 64 * 1024 {
        return None;
    }
    serde_json::from_slice::<serde_json::Value>(&bytes).ok()?["id"]
        .as_str()
        .map(str::to_owned)
}
fn voice_filename(name: &str) -> bool {
    let name = name.to_ascii_lowercase();
    (name.starts_with("voicechat-")
        || name.starts_with("simple-voice-chat-")
        || name == "voicechat.jar"
        || name == "voicechat.jar.disabled")
        && (name.ends_with(".jar") || name.ends_with(".jar.disabled"))
}
fn voice_mods(root: &std::path::Path) -> Result<(bool, bool)> {
    let mods = relative(root, "mods")?;
    if !mods.exists() {
        return Ok((false, false));
    }
    let mut core = false;
    let mut voice = false;
    for (index, entry) in std::fs::read_dir(&mods)
        .context("Sprachchat-Mods prüfen")?
        .enumerate()
    {
        if index >= 1000 {
            return Err("Zu viele Dateien zur sicheren Sprachchat-Vorbereitung".into());
        }
        let entry = entry.context("Mod-Eintrag lesen")?;
        let name = entry.file_name().to_string_lossy().to_string();
        if !name.to_ascii_lowercase().ends_with(".jar")
            && !name.to_ascii_lowercase().ends_with(".jar.disabled")
        {
            continue;
        }
        let path = relative(&mods, &name)?;
        if !path.is_file() {
            continue;
        }
        let id = voice_jar_id(&path);
        core |=
            id.as_deref() == Some("votex_client") && name.to_ascii_lowercase().ends_with(".jar");
        voice |= id.as_deref() == Some("voicechat") || voice_filename(&name);
    }
    Ok((core, voice))
}
fn voice_change_choice(root: &std::path::Path) -> Result<bool> {
    let pending = relative(root, "config/votex/addon-queue.json")?;
    if pending.exists() && voice_queue_choice(root, &pending, None)? {
        return Ok(true);
    }
    let archives = relative(root, "config/votex/addon-backups")?;
    if !archives.exists() {
        return Ok(false);
    }
    let mut bytes = 0u64;
    for (index, entry) in std::fs::read_dir(&archives)
        .context("Angewendete Mod-Entscheidungen prüfen")?
        .enumerate()
    {
        if index >= 256 {
            return Err("Zu viele Mod-Änderungsbelege; Sprachchat bitte manuell hinzufügen".into());
        }
        let entry = entry.context("Mod-Änderungsbeleg lesen")?;
        let batch = entry.file_name().to_string_lossy().to_string();
        if uuid::Uuid::parse_str(&batch).is_err() {
            continue;
        }
        let queue = relative(
            root,
            &format!("config/votex/addon-backups/{batch}/applied-queue.json"),
        )?;
        if !queue.is_file() {
            continue;
        }
        bytes += std::fs::metadata(&queue)
            .context("Mod-Änderungsbeleg prüfen")?
            .len();
        if bytes > 8 * 1024 * 1024 {
            return Err("Mod-Änderungsbelege überschreiten das Prüflimit; Sprachchat bitte manuell hinzufügen".into());
        }
        if voice_queue_choice(root, &queue, Some(&batch))? {
            return Ok(true);
        }
    }
    Ok(false)
}
fn voice_queue_choice(
    root: &std::path::Path,
    path: &std::path::Path,
    batch: Option<&str>,
) -> Result<bool> {
    let data = voice_json(path, 512 * 1024)?;
    if data["schemaVersion"] != 1 {
        return Err("Unbekannte Mod-Änderungsliste bei Sprachchat-Vorbereitung".into());
    }
    let actions = data["actions"]
        .as_array()
        .ok_or("Mod-Änderungsaktionen fehlen")?;
    if actions.len() > 100 {
        return Err("Zu viele Mod-Änderungsaktionen".into());
    }
    for action in actions {
        if action["folder"] != "mods"
            || !matches!(action["kind"].as_str(), Some("remove" | "disable"))
        {
            continue;
        }
        let name = action["filename"]
            .as_str()
            .ok_or("Dateiname der Mod-Entscheidung fehlt")?;
        let relative_path = match batch {
            Some(batch) => format!("config/votex/addon-backups/{batch}/mods/{name}"),
            None => format!("mods/{name}"),
        };
        let original = relative(root, &relative_path)?;
        if voice_filename(name)
            || (original.is_file() && voice_jar_id(&original).as_deref() == Some("voicechat"))
            || matches!(
                action["metadata"]["project_id"].as_str(),
                Some(VOICE_PROJECT | "simple-voice-chat")
            )
        {
            return Ok(true);
        }
    }
    Ok(false)
}

#[cfg(test)]
mod voice_tests {
    use super::*;
    use std::sync::atomic::{AtomicUsize, Ordering};
    fn jar(id: &str) -> Vec<u8> {
        let mut zip = zip::ZipWriter::new(Cursor::new(Vec::new()));
        zip.start_file("fabric.mod.json", zip::write::SimpleFileOptions::default())
            .unwrap();
        zip.write_all(
            serde_json::to_vec(&json!({"schemaVersion":1,"id":id,"version":"fixture"}))
                .unwrap()
                .as_slice(),
        )
        .unwrap();
        zip.finish().unwrap().into_inner()
    }
    fn setup() -> (tempfile::TempDir, Store) {
        let temp = tempfile::tempdir().unwrap();
        let store = Store::new(temp.path().join("votex")).unwrap();
        atomic(
            &store.root.join("config/launcher.json"),
            include_bytes!("../../../../packages/shared-config/defaults.json"),
        )
        .unwrap();
        atomic(
            &store
                .instance("main")
                .unwrap()
                .join("mods/votex-client-core-fixture.jar"),
            &jar("votex_client"),
        )
        .unwrap();
        (temp, store)
    }
    fn receipt(store: &Store) -> serde_json::Value {
        read_json(&store.instance("main").unwrap().join(VOICE_RECEIPT)).unwrap()
    }
    fn capable_jar(capability: serde_json::Value, classes: bool) -> Vec<u8> {
        let mut zip = zip::ZipWriter::new(Cursor::new(Vec::new()));
        for (name, data) in [
            (
                "fabric.mod.json",
                serde_json::to_vec(&json!({"id":"votex_client"})).unwrap(),
            ),
            (
                "assets/votex_client/capabilities.json",
                serde_json::to_vec(&capability).unwrap(),
            ),
        ] {
            zip.start_file(name, zip::write::SimpleFileOptions::default())
                .unwrap();
            zip.write_all(&data).unwrap();
        }
        if classes {
            for name in [
                "dev/votex/client/VoiceMicGate.class",
                "dev/votex/client/voice/PinnedVoiceConsent.class",
                "dev/votex/client/voice/VoiceConsentPlugin.class",
            ] {
                zip.start_file(name, zip::write::SimpleFileOptions::default())
                    .unwrap();
                zip.write_all(b"fixture-class-presence").unwrap();
            }
        }
        zip.finish().unwrap().into_inner()
    }
    #[test]
    fn activation_reads_actual_enabled_core_capability_and_exact_internal_adapter_version() {
        let (_temp, store) = setup();
        let root = store.instance("main").unwrap();
        let path = root.join("mods/votex-client-core-fixture.jar");
        assert!(!voice_guard_ready(&root).unwrap());
        for version in ["2.6.24", "future", VOICE_NATIVE_VERSION] {
            for classes in [false, true] {
                atomic(&path,&capable_jar(json!({"schemaVersion":1,"voiceStartMicConsentGuard":1,"voicechatVersion":version}),classes)).unwrap();
                assert_eq!(
                    voice_guard_ready(&root).unwrap(),
                    classes && version == VOICE_NATIVE_VERSION
                );
            }
        }
        atomic(&path,&capable_jar(json!({"schemaVersion":1,"voiceStartMicConsentGuard":0,"voicechatVersion":VOICE_NATIVE_VERSION}),true)).unwrap();
        assert!(!voice_guard_ready(&root).unwrap());
        let bytes = capable_jar(
            json!({"schemaVersion":1,"voiceStartMicConsentGuard":1,"voicechatVersion":VOICE_NATIVE_VERSION}),
            true,
        );
        atomic(&path, &bytes).unwrap();
        assert!(voice_guard_ready(&root).unwrap());
        let duplicate = root.join("mods/another-core.jar");
        atomic(&duplicate, &bytes).unwrap();
        assert!(!voice_guard_ready(&root).unwrap());
        std::fs::rename(&duplicate, root.join("mods/another-core.jar.disabled")).unwrap();
        assert!(voice_guard_ready(&root).unwrap());
        std::fs::rename(
            &path,
            root.join("mods/votex-client-core-fixture.jar.disabled"),
        )
        .unwrap();
        assert!(!voice_guard_ready(&root).unwrap());
    }
    #[test]
    #[ignore = "Explicit actual native-tested Core artifact check"]
    fn native_tested_core_advertises_the_proven_microphone_guard() {
        let source = std::env::var("VOTEX_GUARD_NATIVE_JAR")
            .expect("Pass the privately built native-tested Core JAR");
        let (_temp, store) = setup();
        let root = store.instance("main").unwrap();
        atomic(
            &root.join("mods/votex-client-core-fixture.jar"),
            &std::fs::read(source).unwrap(),
        )
        .unwrap();
        assert!(voice_guard_ready(&root).unwrap());
    }
    #[tokio::test]
    #[ignore = "Explicit HTTPS installation into an isolated temporary profile; no Minecraft or microphone"]
    async fn actual_first_time_voice_download_uses_native_guard_and_preserves_later_removal() {
        let source = std::env::var("VOTEX_GUARD_NATIVE_JAR")
            .expect("Pass the privately built native-tested Core JAR");
        let (_temp, store) = setup();
        let root = store.instance("main").unwrap();
        let core = std::fs::read(source).unwrap();
        atomic(&root.join("mods/votex-client-core-fixture.jar"), &core).unwrap();
        let options = b"maxFps:165\nrenderDistance:25\n";
        atomic(&root.join("options.txt"), options).unwrap();
        assert!(voice_guard_ready(&root).unwrap());
        voice_once(
            &store,
            "main",
            voice_guard_ready(&root).unwrap(),
            || async {
                modrinth::install_pinned_without_window(
                    &store,
                    "main",
                    VOICE_PROJECT,
                    VOICE_VERSION,
                )
                .await
                .map(|_| ())
            },
        )
        .await
        .unwrap();
        assert_eq!(receipt(&store)["outcome"], "installed");
        let metadata = read_json(&root.join("mods/installed.json")).unwrap();
        let record = metadata
            .as_array()
            .unwrap()
            .iter()
            .find(|row| row["project_id"] == VOICE_PROJECT)
            .expect("Pinned Voice metadata missing");
        assert_eq!(record["id"], VOICE_VERSION);
        let filename = record["filename"].as_str().unwrap();
        let installed = root.join("mods").join(filename);
        assert_eq!(voice_jar_id(&installed).as_deref(), Some("voicechat"));
        let declared = record["files"]
            .as_array()
            .unwrap()
            .iter()
            .find(|file| file["filename"] == filename)
            .unwrap();
        assert_eq!(
            crate::network::hash(&std::fs::read(&installed).unwrap(), "sha512").unwrap(),
            declared["hashes"]["sha512"].as_str().unwrap()
        );
        assert_eq!(
            std::fs::read(root.join("mods/votex-client-core-fixture.jar")).unwrap(),
            core
        );
        assert_eq!(std::fs::read(root.join("options.txt")).unwrap(), options);
        assert!(!root.join("config/votex/voice-microphone.json").exists());
        let receipt_before = std::fs::read(root.join(VOICE_RECEIPT)).unwrap();
        // The real installer removal updates installed.json; a fixture unlink would not
        // prove that this ordinary user action remains authoritative on the next launch.
        modrinth::remove(&store, "main", filename).unwrap();
        voice_once(
            &store,
            "main",
            voice_guard_ready(&root).unwrap(),
            || async { panic!("Explicit removal must not fetch or reinstall Voice") },
        )
        .await
        .unwrap();
        assert!(!installed.exists());
        assert_eq!(
            std::fs::read(root.join(VOICE_RECEIPT)).unwrap(),
            receipt_before
        );
        println!("VOTEX_FIRST_VOICE_HTTPS_PASS exactVersion={} guard=true hash512=true receiptOnce=true removalPreserved=true noGameOrMic=true",VOICE_VERSION);
    }
    #[tokio::test]
    async fn provisioning_stays_inert_until_the_real_microphone_consent_guard_is_ready() {
        let (_temp, store) = setup();
        let root = store.instance("main").unwrap();
        voice_once(&store, "main", false, || async {
            panic!("microphone consent gate is not yet ready")
        })
        .await
        .unwrap();
        assert!(!root.join(VOICE_RECEIPT).exists());
        assert!(!root.join("mods/voicechat-fabric-fixture.jar").exists());
    }
    #[tokio::test]
    async fn first_install_is_once_only_and_keeps_later_manual_removal() {
        let (_temp, store) = setup();
        let root = store.instance("main").unwrap();
        let calls = AtomicUsize::new(0);
        voice_once(&store, "main", true, || async {
            calls.fetch_add(1, Ordering::SeqCst);
            atomic(
                &root.join("mods/voicechat-fabric-fixture.jar"),
                &jar("voicechat"),
            )
        })
        .await
        .unwrap();
        assert_eq!(receipt(&store)["outcome"], "installed");
        assert_eq!(calls.load(Ordering::SeqCst), 1);
        std::fs::remove_file(root.join("mods/voicechat-fabric-fixture.jar")).unwrap();
        voice_once(&store, "main", true, || async {
            panic!("intentional Voice removal must not trigger reinstall")
        })
        .await
        .unwrap();
        assert!(!root.join("mods/voicechat-fabric-fixture.jar").exists());
    }
    #[tokio::test]
    async fn keeps_existing_enabled_disabled_and_renamed_voice_jars_exactly() {
        for filename in [
            "voicechat-fabric-old.jar",
            "voicechat-fabric-old.jar.disabled",
            "my-renamed-mod.jar.disabled",
        ] {
            let (_temp, store) = setup();
            let root = store.instance("main").unwrap();
            let bytes = jar("voicechat");
            atomic(&root.join("mods").join(filename), &bytes).unwrap();
            voice_once(&store, "main", true, || async {
                panic!("existing Voice jar must be preserved")
            })
            .await
            .unwrap();
            assert_eq!(
                std::fs::read(root.join("mods").join(filename)).unwrap(),
                bytes
            );
            assert_eq!(receipt(&store)["outcome"], "kept-existing");
        }
    }
    #[tokio::test]
    async fn does_not_touch_performance_shader_preparation_or_user_options() {
        let (_temp, store) = setup();
        let root = store.instance("main").unwrap();
        let prepared = b"{\"performanceMods\":true,\"shaderEnabled\":false,\"sentinel\":42}";
        let iris = b"shaderPack=User-choice.zip\nenableShaders=false\n";
        let options = b"maxFps:165\nrenderDistance:25\n";
        atomic(&root.join("config/votex/prepared.json"), prepared).unwrap();
        atomic(&root.join("config/iris.properties"), iris).unwrap();
        atomic(&root.join("options.txt"), options).unwrap();
        voice_once(&store, "main", true, || async {
            atomic(&root.join("mods/voicechat-fixture.jar"), &jar("voicechat"))
        })
        .await
        .unwrap();
        assert_eq!(
            std::fs::read(root.join("config/votex/prepared.json")).unwrap(),
            prepared
        );
        assert_eq!(
            std::fs::read(root.join("config/iris.properties")).unwrap(),
            iris
        );
        assert_eq!(std::fs::read(root.join("options.txt")).unwrap(), options);
    }
    #[tokio::test]
    async fn skips_modpacks_other_loaders_versions_and_a_disabled_core() {
        for mode in ["modpack", "vanilla", "old-version", "disabled-core"] {
            let (_temp, store) = setup();
            let root = store.instance("main").unwrap();
            match mode {
                "modpack" => atomic(&root.join("votex-modpack.json"), b"{}").unwrap(),
                "disabled-core" => std::fs::rename(
                    root.join("mods/votex-client-core-fixture.jar"),
                    root.join("mods/votex-client-core-fixture.jar.disabled"),
                )
                .unwrap(),
                _ => {
                    let mut state = store.state().unwrap();
                    if mode == "vanilla" {
                        state["profiles"][0]["loader"] = json!("vanilla");
                    } else {
                        state["profiles"][0]["minecraft"] = json!("1.21.11");
                    }
                    atomic(
                        &store.root.join("config/launcher.json"),
                        &serde_json::to_vec(&state).unwrap(),
                    )
                    .unwrap();
                }
            }
            voice_once(&store, "main", true, || async {
                panic!("ineligible profile must not install Voice")
            })
            .await
            .unwrap();
            assert!(!root.join(VOICE_RECEIPT).exists(), "{mode}");
        }
    }
    #[tokio::test]
    async fn first_setup_respects_an_applied_removal_even_with_a_renamed_jar() {
        let (_temp, store) = setup();
        let root = store.instance("main").unwrap();
        let batch = uuid::Uuid::new_v4().to_string();
        let backup = root.join("config/votex/addon-backups").join(&batch);
        atomic(
            &backup.join("mods/renamed-by-player.jar"),
            &jar("voicechat"),
        )
        .unwrap();
        atomic(&backup.join("applied-queue.json"),&serde_json::to_vec(&json!({"schemaVersion":1,"actions":[{"kind":"remove","folder":"mods","filename":"renamed-by-player.jar"}]})).unwrap()).unwrap();
        voice_once(&store, "main", true, || async {
            panic!("applied explicit Voice removal must win")
        })
        .await
        .unwrap();
        assert_eq!(receipt(&store)["outcome"], "kept-user-choice");
        assert!(!root.join("mods/renamed-by-player.jar").exists());
    }
    #[tokio::test]
    async fn pending_voice_disable_and_legacy_missing_record_keep_user_choice() {
        for pending in [false, true] {
            let (_temp, store) = setup();
            let root = store.instance("main").unwrap();
            if pending {
                atomic(&root.join("config/votex/addon-queue.json"),&serde_json::to_vec(&json!({"schemaVersion":1,"actions":[{"kind":"disable","folder":"mods","filename":"voicechat-fabric-missing.jar"}]})).unwrap()).unwrap();
            } else {
                atomic(
                    &root.join("mods/installed.json"),
                    &serde_json::to_vec(
                        &json!([{"project_id":VOICE_PROJECT,"filename":"manually-removed.jar"}]),
                    )
                    .unwrap(),
                )
                .unwrap();
            }
            voice_once(&store, "main", true, || async {
                panic!("recorded Voice choice must win")
            })
            .await
            .unwrap();
            assert_eq!(receipt(&store)["outcome"], "kept-user-choice");
        }
    }
    #[tokio::test]
    async fn a_failed_download_gets_no_receipt_and_can_retry() {
        let (_temp, store) = setup();
        let root = store.instance("main").unwrap();
        assert!(voice_once(&store, "main", true, || async {
            Err("offline fixture".into())
        })
        .await
        .is_err());
        assert!(!root.join(VOICE_RECEIPT).exists());
        voice_once(&store, "main", true, || async {
            atomic(&root.join("mods/voicechat-fixture.jar"), &jar("voicechat"))
        })
        .await
        .unwrap();
        assert_eq!(receipt(&store)["outcome"], "installed");
    }
    #[tokio::test]
    async fn malformed_or_oversized_receipts_cannot_cause_a_reinstall() {
        for bytes in [
            b"broken".to_vec(),
            vec![b' '; 8193],
            b"{\"schemaVersion\":2,\"handled\":true}".to_vec(),
        ] {
            let (_temp, store) = setup();
            let root = store.instance("main").unwrap();
            atomic(&root.join(VOICE_RECEIPT), &bytes).unwrap();
            assert!(voice_once(&store, "main", true, || async {
                panic!("unknown receipt must not be overwritten")
            })
            .await
            .is_err());
            assert_eq!(std::fs::read(root.join(VOICE_RECEIPT)).unwrap(), bytes);
        }
    }
}
