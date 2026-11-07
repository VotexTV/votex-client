use crate::{
    network,
    storage::{atomic, read_json, relative, Context, Profile, Result, Store},
};
use serde_json::{json, Value};
use std::{path::Path, process::Stdio, time::Duration};
use tokio::process::Command;
async fn text(url: &str) -> Result<String> {
    network::allowed(url)?;
    network::client()?
        .get(url)
        .send()
        .await
        .context("Loader metadata request")?
        .error_for_status()
        .context("Loader metadata unavailable")?
        .text()
        .await
        .context("Read loader metadata")
}
fn safe_version(v: &str) -> bool {
    !v.is_empty()
        && v.len() < 80
        && v.bytes()
            .all(|b| b.is_ascii_alphanumeric() || b"._+-".contains(&b))
}
pub async fn release(loader: &str, mc: &str) -> Result<String> {
    crate::storage::safe_id(&mc.replace('.', "-"))?;
    match loader {
        "fabric" | "quilt" => {
            let base = if loader == "fabric" {
                "https://meta.fabricmc.net/v2"
            } else {
                "https://meta.quiltmc.org/v3"
            };
            let data =
                network::json(&network::client()?, &format!("{base}/versions/loader/{mc}")).await?;
            let a = data.as_array().ok_or("Invalid loader catalog")?;
            let v = a
                .iter()
                .find(|v| loader == "quilt" || v["loader"]["stable"] == true)
                .and_then(|v| v["loader"]["version"].as_str())
                .ok_or("Keine passende Loader-Version veröffentlicht")?;
            if !safe_version(v) {
                return Err("Invalid loader version".into());
            }
            Ok(v.into())
        }
        "forge" => {
            let p = network::json(
                &network::client()?,
                "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json",
            )
            .await?;
            let v = p["promos"][format!("{mc}-recommended")]
                .as_str()
                .or(p["promos"][format!("{mc}-latest")].as_str())
                .ok_or("Keine passende Forge-Version veröffentlicht")?;
            Ok(format!("{mc}-{v}"))
        }
        "neoforge" => {
            let prefix = if let Some(v) = mc.strip_prefix("1.") {
                format!("{}.", v)
            } else {
                format!("{mc}.")
            };
            let xml = text(
                "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml",
            )
            .await?;
            let v = xml
                .split("<version>")
                .skip(1)
                .filter_map(|s| s.split("</version>").next())
                .filter(|s| {
                    s.starts_with(&prefix)
                        && safe_version(s)
                        && !s.contains("alpha")
                        && !s.contains("beta")
                })
                .last()
                .ok_or("Keine passende NeoForge-Version veröffentlicht")?;
            Ok(v.into())
        }
        _ => Err("Unknown loader".into()),
    }
}
pub async fn choices(mc: &str) -> Result<Value> {
    let mut list = vec![
        json!({"id":"vanilla","name":"Vanilla","description":"Das originale Minecraft ohne Mods.","available":true}),
    ];
    for (id, name, description) in [
        (
            "fabric",
            "Fabric",
            "Leichtgewichtiger Loader für Client- und Performance-Mods.",
        ),
        (
            "quilt",
            "Quilt",
            "Offener Loader mit eigenem Mod-Ökosystem.",
        ),
        (
            "forge",
            "Forge",
            "Für viele umfangreiche Mods und Modpacks.",
        ),
        (
            "neoforge",
            "NeoForge",
            "Der Loader für das NeoForge-Ökosystem.",
        ),
    ] {
        match release(id,mc).await{Ok(v)=>list.push(json!({"id":id,"name":name,"description":description,"available":true,"version":v})),Err(_)=>list.push(json!({"id":id,"name":name,"description":description,"available":false,"reason":"Keine passende Veröffentlichung erreichbar"}))}
    }
    Ok(json!(list))
}
pub async fn lightweight(p: &Profile) -> Result<(Value, String)> {
    let v = if p.loader_version.is_empty() {
        release(&p.loader, &p.minecraft).await?
    } else {
        p.loader_version.clone()
    };
    let base = if p.loader == "fabric" {
        "https://meta.fabricmc.net/v2"
    } else {
        "https://meta.quiltmc.org/v3"
    };
    let data = network::json(
        &network::client()?,
        &format!("{base}/versions/loader/{}/{v}/profile/json", p.minecraft),
    )
    .await?;
    Ok((data, v))
}
#[cfg(test)]
mod tests {
    use super::*;
    #[tokio::test]
    #[ignore = "Explicit official metadata smoke test"]
    async fn live_lightweight_loaders() {
        let mut p: Profile = serde_json::from_value(
            serde_json::from_slice::<Value>(include_bytes!(
                "../../../../packages/shared-config/defaults.json"
            ))
            .unwrap()["profiles"][0]
                .clone(),
        )
        .unwrap();
        p.minecraft = "1.20.1".into();
        for loader in ["fabric", "quilt"] {
            p.loader = loader.into();
            let (data, version) = lightweight(&p).await.unwrap();
            assert!(!version.is_empty());
            assert!(data["mainClass"].as_str().unwrap().contains("Client"));
            assert!(!data["libraries"].as_array().unwrap().is_empty());
        }
    }
    #[tokio::test]
    #[ignore = "Explicit official installer smoke test; isolated staging, no account or game launch"]
    async fn live_forge_installer() {
        let temp = tempfile::tempdir().unwrap();
        let store = Store::new(temp.path().join("votex")).unwrap();
        let mut p: Profile = serde_json::from_value(
            serde_json::from_slice::<Value>(include_bytes!(
                "../../../../packages/shared-config/defaults.json"
            ))
            .unwrap()["profiles"][0]
                .clone(),
        )
        .unwrap();
        p.minecraft = "1.20.1".into();
        p.loader = "forge".into();
        p.java = "C:/Program Files/Java/jdk-27/bin/java.exe".into();
        let manifest = crate::minecraft::versions(&store).await.unwrap();
        let entry = manifest["versions"]
            .as_array()
            .unwrap()
            .iter()
            .find(|v| v["id"] == p.minecraft)
            .unwrap();
        let c = network::client().unwrap();
        let vanilla = network::json(&c, entry["url"].as_str().unwrap())
            .await
            .unwrap();
        let jar = store.root.join("cache/base.jar");
        network::download(
            &c,
            vanilla["downloads"]["client"]["url"].as_str().unwrap(),
            &jar,
            Some((
                "sha1",
                vanilla["downloads"]["client"]["sha1"].as_str().unwrap(),
            )),
        )
        .await
        .unwrap();
        let (data, version) = installer(&store, &p, &vanilla, &jar).await.unwrap();
        assert!(version.starts_with("1.20.1-"));
        assert_eq!(data["inheritsFrom"], "1.20.1");
        assert!(data["libraries"].as_array().unwrap().len() > 5);
    }
}
pub async fn installer(
    store: &Store,
    p: &Profile,
    vanilla: &Value,
    client_jar: &Path,
) -> Result<(Value, String)> {
    let runtime = crate::runtime::resolve(
        store,
        &p.java,
        vanilla["javaVersion"]["majorVersion"].as_u64().unwrap_or(8),
    )
    .await?;
    let v = if p.loader_version.is_empty() {
        release(&p.loader, &p.minecraft).await?
    } else {
        p.loader_version.clone()
    };
    if !safe_version(&v) {
        return Err("Invalid loader version".into());
    }
    let (base, group, artifact) = if p.loader == "forge" {
        (
            "https://maven.minecraftforge.net",
            "net/minecraftforge",
            "forge",
        )
    } else {
        (
            "https://maven.neoforged.net/releases",
            "net/neoforged",
            "neoforge",
        )
    };
    let url = format!("{base}/{group}/{artifact}/{v}/{artifact}-{v}-installer.jar");
    let checksum = text(&format!("{url}.sha1")).await?;
    let checksum = checksum
        .split_whitespace()
        .next()
        .ok_or("Missing installer checksum")?;
    if checksum.len() != 40 || !checksum.bytes().all(|b| b.is_ascii_hexdigit()) {
        return Err("Invalid installer checksum".into());
    }
    let work = relative(
        &store.root,
        &format!("cache/loader-install/{}-{}", p.loader, v),
    )?;
    let jar = work.join("installer.jar");
    network::download(&network::client()?, &url, &jar, Some(("sha1", checksum))).await?;
    // Verify Minecraft target before running any official processor.
    {
        let file = std::fs::File::open(&jar).context("Read loader installer")?;
        let mut zip = zip::ZipArchive::new(file).context("Invalid loader installer")?;
        let mut entry = zip
            .by_name("install_profile.json")
            .context("Missing installer profile")?;
        use std::io::Read;
        let mut bytes = Vec::new();
        entry
            .by_ref()
            .take(4_000_000)
            .read_to_end(&mut bytes)
            .context("Read installer profile")?;
        let profile: Value = serde_json::from_slice(&bytes).context("Invalid installer profile")?;
        if profile["minecraft"] != p.minecraft {
            return Err("Loader installer targets another Minecraft version".into());
        }
    }
    let root = work.join("client");
    atomic(&root.join("launcher_profiles.json"), b"{\"profiles\":{}}")?;
    let version = root.join("versions").join(&p.minecraft);
    atomic(
        &version.join(format!("{}.json", p.minecraft)),
        &serde_json::to_vec(vanilla).context("Stage Minecraft metadata")?,
    )?;
    atomic(
        &version.join(format!("{}.jar", p.minecraft)),
        &std::fs::read(client_jar).context("Stage Minecraft client")?,
    )?;
    let mut cmd = Command::new(runtime);
    cmd.arg("-Djava.awt.headless=true")
        .arg("-jar")
        .arg(&jar)
        .arg("--installClient")
        .arg(&root)
        .current_dir(&work)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    #[cfg(windows)]
    cmd.creation_flags(0x08000000);
    cmd.kill_on_drop(true);
    let output = tokio::time::timeout(Duration::from_secs(600), cmd.output())
        .await
        .map_err(|_| "Loader installation timed out")?
        .context("Start official loader installer")?;
    if !output.status.success() {
        return Err("Der offizielle Modloader-Installer ist fehlgeschlagen. Prüfe Java und die gewählte Version; das bestehende Profil wurde nicht verändert.".into());
    }
    let mut selected = None;
    for entry in std::fs::read_dir(root.join("versions")).context("Read installed loader")? {
        let d = entry.context("Read loader entry")?.path();
        if !d.is_dir() {
            continue;
        }
        for f in std::fs::read_dir(d).context("Read loader metadata")? {
            let path = f.context("Read loader file")?.path();
            if path.extension().and_then(|s| s.to_str()) == Some("json") {
                let data = read_json(&path)?;
                if data["inheritsFrom"] == p.minecraft && !data["mainClass"].is_null() {
                    selected = Some(data);
                }
            }
        }
    }
    let mut data = selected.ok_or("Official installer did not create a launch profile")?;
    for lib in data["libraries"]
        .as_array_mut()
        .ok_or("Missing loader libraries")?
    {
        let name = lib["name"].as_str().ok_or("Missing loader library")?;
        let path = crate::minecraft::maven(name)?;
        let local = relative(&root.join("libraries"), &path)?;
        if local.exists() {
            let bytes = std::fs::read(local).context("Read processor output")?;
            let sum = network::hash(&bytes, "sha1")?;
            atomic(
                &relative(&store.root.join("cache/libraries"), &path)?,
                &bytes,
            )?;
            lib["downloads"]["artifact"] =
                json!({"path":path,"sha1":sum,"url":format!("{base}/{path}")});
        }
    }
    Ok((data, v))
}
