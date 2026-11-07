use crate::{
    curseforge, modrinth, network, recovery,
    storage::{atomic, read_json, relative, Context, Result, Store},
};
use serde_json::{json, Value};
use std::collections::{HashSet, VecDeque};
pub async fn search(
    store: &Store,
    id: &str,
    provider: &str,
    kind: &str,
    query: &str,
    offset: u32,
    sort: &str,
    category: &str,
) -> Result<Value> {
    let p = store.profile(id)?;
    if query.len() > 200
        || offset > 100_000
        || !matches!(kind, "mod" | "modpack" | "shader" | "resourcepack")
    {
        return Err("Ungültige Suchparameter".into());
    }
    if provider == "modrinth" {
        let mut facets = vec![
            vec![format!("project_type:{kind}")],
            vec![format!("versions:{}", p.minecraft)],
        ];
        if kind == "mod" || kind == "modpack" {
            if p.loader != "vanilla" {
                facets.push(vec![format!("categories:{}", p.loader)]);
            }
        }
        if !category.is_empty() {
            if !matches!(
                category,
                "optimization" | "adventure" | "decoration" | "utility" | "technology" | "worldgen"
            ) {
                return Err("Ungültige Kategorie".into());
            }
            facets.push(vec![format!("categories:{category}")]);
        }
        let index = match sort {
            "updated" => "updated",
            "relevance" => "relevance",
            _ => "downloads",
        };
        let r = network::client()?
            .get("https://api.modrinth.com/v2/search")
            .query(&[
                ("query", query.to_string()),
                ("facets", json!(facets).to_string()),
                ("limit", "30".into()),
                ("offset", offset.to_string()),
                ("index", index.into()),
            ])
            .send()
            .await
            .context("Modrinth-Suche nicht erreichbar")?
            .error_for_status()
            .context("Modrinth-Suche fehlgeschlagen")?
            .json::<Value>()
            .await
            .context("Ungültige Modrinth-Suche")?;
        Ok(json!({"hits":r["hits"],"total":r["total_hits"],"offset":offset,"provider":"modrinth"}))
    } else if provider == "curseforge" {
        if offset >= 9970 {
            return Ok(json!({"hits":[],"total":offset,"offset":offset,"provider":provider}));
        }
        let class = match kind {
            "mod" => "6",
            "modpack" => "4471",
            "resourcepack" => "12",
            _ => "6552",
        };
        let mut q = vec![
            ("gameId".into(), "432".into()),
            ("classId".into(), class.into()),
            ("searchFilter".into(), query.into()),
            ("gameVersion".into(), p.minecraft.clone()),
            ("index".into(), offset.to_string()),
            ("pageSize".into(), "30".into()),
            ("sortOrder".into(), "desc".into()),
            (
                "sortField".into(),
                if sort == "updated" { "3" } else { "6" }.into(),
            ),
        ];
        if kind == "mod" || kind == "modpack" {
            q.push(("modLoaderType".into(), curseforge::loader(&p).into()));
        }
        let r = curseforge::get("mods/search", &q).await?;
        let hits:Vec<_>=r["data"].as_array().ok_or("Invalid CurseForge search")?.iter().map(|h|json!({"project_id":h["id"].to_string(),"slug":h["slug"],"title":h["name"],"description":h["summary"],"icon_url":h["logo"]["thumbnailUrl"],"downloads":h["downloadCount"],"author":h["authors"][0]["name"],"versions":[p.minecraft],"url":h["links"]["websiteUrl"]})).collect();
        Ok(
            json!({"hits":hits,"total":r["pagination"]["totalCount"],"offset":offset,"provider":provider}),
        )
    } else {
        Err("Unbekannte Quelle".into())
    }
}
pub async fn install(
    store: &Store,
    id: &str,
    provider: &str,
    kind: &str,
    project: &str,
    app: &tauri::AppHandle,
) -> Result<Value> {
    crate::storage::safe_id(project)?;
    if provider == "modrinth" && kind == "mod" {
        return modrinth::install(store, id, project, app).await;
    }
    if kind == "modpack" {
        return crate::modpacks::from_project(store, id, provider, project, app).await;
    }
    if !matches!(kind, "mod" | "shader" | "resourcepack") {
        return Err("Unbekannter Inhaltstyp".into());
    }
    let p = store.profile(id)?;
    let c = network::client()?;
    let mut plan = vec![];
    if provider == "curseforge" {
        let mut queue = VecDeque::from([project
            .parse::<u64>()
            .map_err(|_| "Ungültige CurseForge-ID")?]);
        let mut seen = HashSet::new();
        while let Some(project) = queue.pop_front() {
            if !seen.insert(project) {
                continue;
            }
            if seen.len() > 100 {
                return Err("Zu viele erforderliche Abhängigkeiten".into());
            }
            let file = curseforge::compatible_asset(project, &p, kind == "mod").await?;
            if kind == "mod" {
                if let Some(deps) = file["dependencies"].as_array() {
                    for d in deps {
                        if d["relationType"] == 3 {
                            queue.push_back(d["modId"].as_u64().ok_or("Invalid dependency")?);
                        }
                    }
                }
            }
            let (name, url, sum) = curseforge::download_info(&file)?;
            let cache = relative(&store.root.join("cache/catalog"), &format!("{sum}.bin"))?;
            network::download(&c, url, &cache, Some(("sha1", sum))).await?;
            plan.push((name.to_string(),cache,json!({"project_id":format!("cf-{project}"),"version_number":file["displayName"],"name":file["displayName"],"provider":provider})));
        }
    } else if provider == "modrinth" {
        let r = c
            .get(format!(
                "https://api.modrinth.com/v2/project/{project}/version"
            ))
            .query(&[("game_versions", json!([p.minecraft]).to_string())])
            .send()
            .await
            .context("Read content versions")?
            .error_for_status()
            .context("Content versions unavailable")?
            .json::<Value>()
            .await
            .context("Invalid content versions")?;
        let v = r
            .as_array()
            .and_then(|a| {
                a.iter()
                    .find(|v| v["version_type"] == "release")
                    .or_else(|| a.first())
            })
            .ok_or("Keine passende Veröffentlichung")?;
        let f = v["files"]
            .as_array()
            .and_then(|a| {
                a.iter()
                    .find(|f| f["primary"] == true)
                    .or_else(|| a.first())
            })
            .ok_or("Keine passende Datei")?;
        let name = f["filename"].as_str().ok_or("Missing content filename")?;
        let sum = f["hashes"]["sha512"]
            .as_str()
            .ok_or("Missing content checksum")?;
        let cache = relative(&store.root.join("cache/catalog"), &format!("{sum}.zip"))?;
        network::download(
            &c,
            f["url"].as_str().ok_or("Missing content URL")?,
            &cache,
            Some(("sha512", sum)),
        )
        .await?;
        plan.push((
            name.to_string(),
            cache,
            json!({"project_id":project,"name":v["name"],"version_number":v["version_number"]}),
        ));
    } else {
        return Err("Unbekannte Quelle".into());
    }
    let folder = match kind {
        "shader" => "shaderpacks",
        "resourcepack" => "resourcepacks",
        _ => "mods",
    };
    let root = store.instance(id)?.join(folder);
    let before = recovery::create(store, id, "pre-mod-installation")?;
    let result = (|| -> Result<()> {
        let meta = root.join("installed.json");
        let mut items = if meta.exists() {
            read_json(&meta)?
                .as_array()
                .ok_or("Invalid installed metadata")?
                .clone()
        } else {
            vec![]
        };
        for (name, cache, mut record) in plan {
            if (kind == "mod" && !name.ends_with(".jar"))
                || (kind != "mod" && !name.ends_with(".zip"))
                || name.contains('/')
                || name.contains('\\')
            {
                return Err("Unsafe content filename".into());
            }
            for old in items
                .iter()
                .filter(|m| m["project_id"] == record["project_id"])
            {
                if let Some(old_name) = old["filename"].as_str() {
                    if old_name != name {
                        let old_path = relative(&root, old_name)?;
                        if old_path.exists() {
                            let backup = store
                                .root
                                .join("backups/quarantine")
                                .join(id)
                                .join(&before.id)
                                .join(old_name);
                            atomic(
                                &backup,
                                &std::fs::read(&old_path).context("Preserve content")?,
                            )?;
                            std::fs::remove_file(old_path).context("Move old content")?;
                        }
                    }
                }
            }
            atomic(
                &relative(&root, &name)?,
                &std::fs::read(cache).context("Read verified content")?,
            )?;
            items.retain(|m| m["project_id"] != record["project_id"]);
            record["filename"] = json!(name);
            items.push(record);
        }
        atomic(
            &meta,
            &serde_json::to_vec_pretty(&items).context("Save content receipt")?,
        )?;
        Ok(())
    })();
    if let Err(e) = result {
        if kind == "mod" {
            recovery::restore(store, id, &before.id)?;
        }
        return Err(e);
    }
    Ok(json!({"installed":true,"kind":kind}))
}
