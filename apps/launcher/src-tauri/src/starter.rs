//! Explicit new-profile choices. Legacy profiles and imported modpacks keep their existing policy.
use crate::storage::{atomic, read_json, relative, Context, Profile, Result, Store};
use serde_json::{json, Value};
use tauri::AppHandle;

pub fn status(store: &Store, id: &str) -> Result<Value> {
    store.profile(id)?;
    let path = store
        .instance(id)?
        .join("config/votex/starter-downloads.json");
    if !path.exists() {
        return Ok(Value::Null);
    }
    if std::fs::metadata(&path)
        .context("Check profile download report")?
        .len()
        > 64_000
    {
        return Err("Profile download report exceeds limit".into());
    }
    let value = read_json(&path)?;
    if value["schemaVersion"] != 1 || !value["results"].as_array().is_some_and(|a| a.len() <= 32) {
        return Err("Invalid profile download report".into());
    }
    Ok(value)
}

pub fn plan(p: &Profile) -> Vec<(&'static str, bool)> {
    let Some(m) = &p.starter_mods else {
        return vec![];
    };
    if p.loader == "vanilla" {
        return vec![];
    }
    let mut items = vec![];
    for (slug, enabled) in [
        ("sodium", m.sodium || m.iris),
        ("iris", m.iris),
        ("modmenu", m.modmenu),
        ("zoomify", m.zoomify),
    ] {
        if enabled {
            items.push((slug, false));
        }
    }
    if p.performance_mods {
        for slug in ["lithium", "ferrite-core", "immediatelyfast"] {
            items.push((slug, false));
        }
    }
    if m.more {
        for slug in [
            "xaeros-minimap",
            "xaeros-world-map",
            "appleskin",
            "3dskinlayers",
            "voicechat",
            "mousetweaks",
        ] {
            if m.extras.iter().any(|x| x == slug) {
                // AppleSkin exposes its Mod Menu settings through this optional
                // upstream dependency; without it the installed config entry fails.
                if slug == "appleskin" {
                    items.push(("cloth-config", false));
                }
                items.push((slug, false));
            }
        }
    }
    if p.shader_enabled && m.iris {
        for slug in ["complementary-reimagined", "bsl-shaders"] {
            if m.shaders.iter().any(|x| x == slug) {
                items.push((slug, true));
            }
        }
    }
    items
}

pub async fn prepare(store: &Store, id: &str, app: &AppHandle, voice_guard: bool) -> Result<()> {
    prepare_with_progress(store, id, Some(app), voice_guard).await
}
// The same resolver, downloads and receipts run in isolated integration checks;
// only desktop progress emission needs a live window.
pub(crate) async fn prepare_with_progress(
    store: &Store,
    id: &str,
    app: Option<&AppHandle>,
    voice_guard: bool,
) -> Result<()> {
    let p = store.profile(id)?;
    if p.starter_mods.is_none()
        || p.loader == "vanilla"
        || store.instance(id)?.join("votex-modpack.json").exists()
    {
        return Ok(());
    }
    let root = store.instance(id)?;
    let path = root.join("config/votex/starter-downloads.json");
    let mut receipt = read_json(&path).unwrap_or(json!({}));
    let key = format!("{}-{}", p.minecraft, p.loader);
    if receipt["target"] != key {
        receipt = json!({"schemaVersion":1,"target":key,"handled":{},"results":[]});
    }
    let mut results = vec![];
    let mut first_shader = None;
    let installed = crate::modrinth::installed(store, id)?;
    let choices = plan(&p);
    let total = choices.len();
    for (slug, shader) in choices {
        if shader && first_shader.is_none() {
            first_shader = receipt["handled"][slug]["filename"]
                .as_str()
                .map(str::to_owned);
        }
        if receipt["handled"][slug].is_object() {
            results.push(json!({"project":slug,"status":"preserved"}));
            continue;
        }
        let mod_id = match slug {
            "ferrite-core" => "ferritecore",
            "3dskinlayers" => "skinlayers3d",
            "xaeros-minimap" => "xaerominimap",
            "xaeros-world-map" => "xaeroworldmap",
            _ => slug,
        };
        if !shader
            && installed
                .as_array()
                .is_some_and(|items| items.iter().any(|item| item["modId"] == mod_id))
        {
            receipt["handled"][slug] = json!({"existing":true});
            results.push(json!({"project":slug,"status":"preserved"}));
            continue;
        }
        if let Some(app) = app {
            crate::minecraft::event(
                app,
                "mods",
                &format!("Profilpakete · {slug}"),
                results.len(),
                total,
            );
        }
        let result = if shader {
            shader_download(store, &p, slug).await
        } else if slug == "voicechat" && p.minecraft == "26.2" && p.loader == "fabric" {
            if !voice_guard {
                Err("Voice benötigt den passenden Votex-Mikrofonschutz".into())
            } else {
                crate::modrinth::install_pinned_with_progress(
                    store, id, "9eGKb6K1", "Ls232EsW", app,
                )
                .await
            }
        } else {
            crate::modrinth::install_with_progress(
                store,
                id,
                match slug {
                    "voicechat" => "simple-voice-chat",
                    "mousetweaks" => "mouse-tweaks",
                    _ => slug,
                },
                app,
            )
            .await
        };
        match result {
            Ok(value) => {
                if shader && first_shader.is_none() {
                    first_shader = value["filename"].as_str().map(str::to_owned);
                }
                receipt["handled"][slug] = if value.is_object() {
                    value
                } else {
                    json!({"installed":true})
                };
                results.push(json!({"project":slug,"status":"installed"}));
            }
            Err(error) => {
                results.push(json!({"project":slug,"status":"unavailable","reason":error}));
                if let Some(app) = app {
                    crate::minecraft::event(
                        app,
                        "mods-warning",
                        &format!("{slug} wurde ausgelassen: {error}"),
                        0,
                        0,
                    );
                }
            }
        }
        receipt["results"] = json!(results);
        atomic(
            &path,
            &serde_json::to_vec_pretty(&receipt).context("Save profile download report")?,
        )?;
    }
    // Select a first shader once. A later in-game choice is never replaced on launch.
    if !receipt["shaderSelectionApplied"].as_bool().unwrap_or(false) {
        if let Some(filename) = first_shader {
            let iris = root.join("config/iris.properties");
            let mut lines: Vec<_> = std::fs::read_to_string(&iris)
                .unwrap_or_default()
                .lines()
                .filter(|l| !l.starts_with("shaderPack=") && !l.starts_with("enableShaders="))
                .map(str::to_owned)
                .collect();
            lines.push(format!("shaderPack={filename}"));
            lines.push("enableShaders=true".into());
            atomic(&iris, format!("{}\n", lines.join("\n")).as_bytes())?;
            receipt["shaderSelectionApplied"] = json!(true);
        }
    }
    receipt["results"] = json!(results);
    atomic(
        &path,
        &serde_json::to_vec_pretty(&receipt).context("Save profile download report")?,
    )
}
async fn shader_download(store: &Store, p: &Profile, slug: &str) -> Result<Value> {
    let client = crate::network::client()?;
    let versions: Value = client
        .get(format!(
            "https://api.modrinth.com/v2/project/{slug}/version"
        ))
        .query(&[
            ("game_versions", json!([p.minecraft]).to_string()),
            ("loaders", json!(["iris"]).to_string()),
        ])
        .send()
        .await
        .context("Find matching shader")?
        .error_for_status()
        .context("Shader catalog")?
        .json()
        .await
        .context("Read shader catalog")?;
    let v = versions
        .as_array()
        .and_then(|a| a.first())
        .ok_or("Kein passender Shader für diese Minecraft-Version")?;
    let files = v["files"]
        .as_array()
        .ok_or("Shader file metadata missing")?;
    let file = files
        .iter()
        .find(|f| f["primary"] == true)
        .or(files.first())
        .ok_or("Shader file missing")?;
    let name = file["filename"].as_str().ok_or("Shader filename missing")?;
    if !name.ends_with(".zip")
        || name.len() > 180
        || file["size"].as_u64().unwrap_or(u64::MAX) > 128 * 1024 * 1024
    {
        return Err("Invalid shader download".into());
    }
    let target = relative(&store.instance(&p.id)?.join("shaderpacks"), name)?;
    let sum = file["hashes"]["sha512"]
        .as_str()
        .ok_or("Shader checksum missing")?;
    crate::network::download(
        &client,
        file["url"].as_str().ok_or("Shader URL missing")?,
        &target,
        Some(("sha512", sum)),
    )
    .await?;
    Ok(json!({"filename":name,"version":v["id"],"sha512":sum}))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[tokio::test]
    #[ignore = "Explicit HTTPS default/SMP/shader installation into a temporary profile; no game, account or microphone"]
    async fn actual_selected_packages_download_and_preserve_user_changes() {
        let temp = tempfile::tempdir().unwrap();
        let store = Store::new(temp.path().join("votex")).unwrap();
        let mut state: Value = serde_json::from_slice(include_bytes!(
            "../../../../packages/shared-config/defaults.json"
        ))
        .unwrap();
        state["profiles"][0]["minecraft"] = json!("26.2");
        state["profiles"][0]["loader"] = json!("fabric");
        state["profiles"][0]["performanceMods"] = json!(false);
        state["profiles"][0]["shaderEnabled"] = json!(false);
        state["profiles"][0]["starterMods"] = json!({"sodium":true,"iris":true,"modmenu":true,"zoomify":true,"more":false,"extras":[],"shaders":[]});
        let config = store.root.join("config/launcher.json");
        atomic(&config, &serde_json::to_vec(&state).unwrap()).unwrap();
        let root = store.instance("main").unwrap();
        let core = std::fs::read(
            std::env::var("VOTEX_GUARD_NATIVE_JAR").expect("Pass the native-tested Core JAR"),
        )
        .unwrap();
        atomic(&root.join("mods/votex-client-core-fixture.jar"), &core).unwrap();
        let options = b"maxFps:165\nrenderDistance:25\nfov:0.5\n";
        atomic(&root.join("options.txt"), options).unwrap();
        let guard = crate::prepare::voice_guard_ready(&root).unwrap();
        assert!(guard);
        prepare_with_progress(&store, "main", None, guard)
            .await
            .unwrap();
        let basics = status(&store, "main").unwrap();
        assert_eq!(basics["results"].as_array().unwrap().len(), 4);
        assert!(
            basics["results"]
                .as_array()
                .unwrap()
                .iter()
                .all(|row| row["status"] == "installed"),
            "{basics:#}"
        );
        state["profiles"][0]["performanceMods"] = json!(true);
        state["profiles"][0]["shaderEnabled"] = json!(true);
        state["profiles"][0]["starterMods"]["more"] = json!(true);
        state["profiles"][0]["starterMods"]["extras"] = json!([
            "xaeros-minimap",
            "xaeros-world-map",
            "appleskin",
            "3dskinlayers",
            "voicechat",
            "mousetweaks"
        ]);
        state["profiles"][0]["starterMods"]["shaders"] =
            json!(["complementary-reimagined", "bsl-shaders"]);
        atomic(&config, &serde_json::to_vec(&state).unwrap()).unwrap();
        prepare_with_progress(&store, "main", None, guard)
            .await
            .unwrap();
        let receipt = status(&store, "main").unwrap();
        let results = receipt["results"].as_array().unwrap();
        assert_eq!(results.len(), 16);
        assert!(
            results
                .iter()
                .all(|row| row["status"] == "installed" || row["status"] == "preserved"),
            "{receipt:#}"
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
                    .any(|row| row["modId"] == id && row["enabled"] == true),
                "Missing {id}: {installed:#}"
            );
        }
        let metadata = read_json(&root.join("mods/installed.json")).unwrap();
        let zoom_record = metadata
            .as_array()
            .unwrap()
            .iter()
            .find(|row| row["project_id"] == "w7ThoJFB")
            .expect("Zoomify root metadata");
        assert_ne!(
            zoom_record["id"], "D64cvU6G",
            "The multi-loader release requires a NeoForge-only dependency"
        );
        assert!(
            !metadata
                .as_array()
                .unwrap()
                .iter()
                .any(|row| row["project_id"] == "1vrSzlao"),
            "NeoForge-only Kotlin must not be installed into Fabric"
        );
        println!("VOTEX_STARTER_RESOLVED_VERSIONS {}", serde_json::to_string(&metadata.as_array().unwrap().iter().map(|row| json!({"project":row["project_id"],"version":row["id"],"filename":row["filename"]})).collect::<Vec<_>>()).unwrap());
        for record in metadata.as_array().unwrap() {
            let filename = record["filename"].as_str().unwrap();
            let declared = record["files"]
                .as_array()
                .unwrap()
                .iter()
                .find(|file| file["filename"] == filename)
                .unwrap();
            assert_eq!(
                crate::network::hash(
                    &std::fs::read(root.join("mods").join(filename)).unwrap(),
                    "sha512"
                )
                .unwrap(),
                declared["hashes"]["sha512"].as_str().unwrap()
            );
        }
        assert!(metadata
            .as_array()
            .unwrap()
            .iter()
            .any(|row| row["project_id"] == "9eGKb6K1" && row["id"] == "Ls232EsW"));
        for slug in ["complementary-reimagined", "bsl-shaders"] {
            let file = receipt["handled"][slug]["filename"].as_str().unwrap();
            assert_eq!(
                crate::network::hash(
                    &std::fs::read(root.join("shaderpacks").join(file)).unwrap(),
                    "sha512"
                )
                .unwrap(),
                receipt["handled"][slug]["sha512"].as_str().unwrap()
            );
        }
        let iris = root.join("config/iris.properties");
        assert!(std::fs::read_to_string(&iris).unwrap().contains(
            receipt["handled"]["complementary-reimagined"]["filename"]
                .as_str()
                .unwrap()
        ));
        let zoom = installed
            .as_array()
            .unwrap()
            .iter()
            .find(|row| row["modId"] == "zoomify")
            .unwrap()["filename"]
            .as_str()
            .unwrap()
            .to_owned();
        let skin = installed
            .as_array()
            .unwrap()
            .iter()
            .find(|row| row["modId"] == "skinlayers3d")
            .unwrap()["filename"]
            .as_str()
            .unwrap()
            .to_owned();
        crate::modrinth::remove(&store, "main", &zoom).unwrap();
        crate::modrinth::toggle(&store, "main", &skin).unwrap();
        let user_iris = b"shaderPack=MyChoice.zip\nenableShaders=false\n";
        atomic(&iris, user_iris).unwrap();
        prepare_with_progress(&store, "main", None, guard)
            .await
            .unwrap();
        assert!(!root.join("mods").join(&zoom).exists());
        assert!(!root.join("mods").join(&skin).exists());
        assert!(root.join("mods").join(format!("{skin}.disabled")).exists());
        assert_eq!(std::fs::read(&iris).unwrap(), user_iris);
        assert_eq!(std::fs::read(root.join("options.txt")).unwrap(), options);
        assert_eq!(
            std::fs::read(root.join("mods/votex-client-core-fixture.jar")).unwrap(),
            core
        );
        assert!(!root.join("config/votex/voice-microphone.json").exists());
        println!("VOTEX_STARTER_HTTPS_PASS selections=16 basics=4 performance=3 smp=6 configSupport=1 shaders=2 sha512=true voicePinned=true removalAndDisablePreserved=true irisChoicePreserved=true noGameOrMic=true");
    }
    fn profile() -> Profile {
        serde_json::from_value(
            serde_json::from_slice::<Value>(include_bytes!(
                "../../../../packages/shared-config/defaults.json"
            ))
            .unwrap()["profiles"][0]
                .clone(),
        )
        .unwrap()
    }
    #[test]
    fn defaults_opt_out_and_smp_are_distinct() {
        let mut p = profile();
        assert!(plan(&p).is_empty());
        p.starter_mods = Some(crate::storage::StarterMods {
            sodium: true,
            iris: true,
            modmenu: true,
            zoomify: true,
            more: false,
            extras: vec!["voicechat".into()],
            shaders: vec!["bsl-shaders".into()],
        });
        assert_eq!(
            plan(&p),
            vec![
                ("sodium", false),
                ("iris", false),
                ("modmenu", false),
                ("zoomify", false)
            ]
        );
        p.starter_mods.as_mut().unwrap().zoomify = false;
        p.starter_mods.as_mut().unwrap().more = true;
        p.shader_enabled = true;
        let selected = plan(&p);
        assert!(!selected.iter().any(|x| x.0 == "zoomify"));
        assert!(selected.contains(&("voicechat", false)));
        assert!(selected.contains(&("bsl-shaders", true)));
        p.starter_mods.as_mut().unwrap().extras.push("appleskin".into());
        let selected = plan(&p);
        let skin_index = selected.iter().position(|item| item.0 == "appleskin").unwrap();
        assert_eq!(selected[skin_index - 1], ("cloth-config", false));
        p.starter_mods.as_mut().unwrap().extras.retain(|id| id != "appleskin");
        assert!(!plan(&p).iter().any(|item| item.0 == "cloth-config"));
        p.loader = "vanilla".into();
        assert!(plan(&p).is_empty());
    }
}
