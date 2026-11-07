use crate::{
    network,
    storage::{Context, Profile, Result},
};
use serde_json::Value;
fn entry() -> Result<keyring::Entry> {
    keyring::Entry::new("VotexClient.CurseForge", "api-key")
        .map_err(|_| "Windows-Anmeldeinformationsverwaltung nicht verfügbar".into())
}
pub fn configured() -> bool {
    entry()
        .and_then(|e| e.get_password().map_err(|_| "Missing key".into()))
        .map(|k| !k.is_empty())
        .unwrap_or(false)
}
pub fn save(key: &str) -> Result<()> {
    if key.is_empty() {
        match entry()?.delete_credential() {
            Ok(()) | Err(keyring::Error::NoEntry) => return Ok(()),
            Err(_) => return Err("Schlüssel konnte nicht entfernt werden".into()),
        }
    }
    if key.len() > 512 || key.len() < 10 || key.bytes().any(|b| b < 33 || b > 126) {
        return Err("Ungültiges Schlüsselformat".into());
    }
    entry()?
        .set_password(key)
        .map_err(|_| "Schlüssel konnte nicht sicher gespeichert werden".into())
}
/// A saved user key may be handed to the child game in its private environment.
/// It is never returned through a Tauri command, written into profiles or added to Java arguments.
pub fn game_key() -> Option<String> {
    entry().ok()?.get_password().ok().filter(|key| key.len() >= 10 && key.len() <= 512 && key.bytes().all(|b| (33..=126).contains(&b)))
}
pub fn download_key() -> Result<String> {
    game_key().ok_or_else(|| "CURSEFORGE_KEY_REQUIRED: Hinterlege deinen eigenen CurseForge-API-Schlüssel unter Quellen.".into())
}
pub async fn get(path: &str, query: &[(String, String)]) -> Result<Value> {
    let key=entry()?.get_password().map_err(|_|"CURSEFORGE_KEY_REQUIRED: Hinterlege deinen eigenen CurseForge-API-Schlüssel unter Quellen. Ohne Schlüssel bleibt Modrinth verfügbar.")?;
    let response = network::client()?
        .get(format!("https://api.curseforge.com/v1/{path}"))
        .header("x-api-key", key)
        .query(query)
        .send()
        .await
        .context("CurseForge nicht erreichbar")?;
    if !response.status().is_success() {
        return Err(format!(
            "CurseForge antwortet mit HTTP {}. Prüfe den Schlüssel oder versuche es später erneut.",
            response.status().as_u16()
        ));
    }
    response
        .json()
        .await
        .context("Ungültige CurseForge-Antwort")
}
pub fn loader(p: &Profile) -> &str {
    match p.loader.as_str() {
        "forge" => "1",
        "fabric" => "4",
        "quilt" => "5",
        "neoforge" => "6",
        _ => "0",
    }
}
pub async fn file(project: u64, file: u64) -> Result<Value> {
    let data = get(&format!("mods/{project}/files/{file}"), &[]).await?;
    let f = data["data"].clone();
    if f["modId"] != project || f["id"] != file {
        return Err("CurseForge file identity mismatch".into());
    }
    download_info(&f)?;
    Ok(f)
}
pub async fn content_folder(project: u64) -> Result<&'static str> {
    let response = get(&format!("mods/{project}"), &[]).await?;
    let m = &response["data"];
    if m["gameId"] != 432 {
        return Err("Das Projekt gehört nicht zu Minecraft Java".into());
    }
    match m["classId"].as_u64() {
        Some(6) => Ok("mods"),
        Some(12) => Ok("resourcepacks"),
        Some(6552) => Ok("shaderpacks"),
        _ => Err("Nicht unterstützter Inhaltstyp im CurseForge-Modpack".into()),
    }
}
pub fn download_info(f: &Value) -> Result<(&str, &str, &str)> {
    let name = f["fileName"]
        .as_str()
        .ok_or("Missing CurseForge filename")?;
    let url=f["downloadUrl"].as_str().filter(|s|!s.is_empty()).ok_or("DISTRIBUTION_RESTRICTED: Der Autor erlaubt für diese Datei keinen Download über Drittanbieter. Lade sie auf der offiziellen Projektseite selbst herunter.")?;
    let sum = f["hashes"]
        .as_array()
        .and_then(|a| a.iter().find(|h| h["algo"] == 1))
        .and_then(|h| h["value"].as_str())
        .ok_or("CurseForge file has no SHA-1 checksum")?;
    if name.contains('/')
        || name.contains('\\')
        || sum.len() != 40
        || !sum.bytes().all(|b| b.is_ascii_hexdigit())
    {
        return Err("Invalid CurseForge file metadata".into());
    }
    if !network::curseforge_file_url(url) {
        return Err("CurseForge download left its official file CDN".into());
    }
    Ok((name, url, sum))
}
pub async fn compatible(project: u64, p: &Profile) -> Result<Value> {
    compatible_asset(project, p, true).await
}
pub async fn compatible_asset(project: u64, p: &Profile, filter_loader: bool) -> Result<Value> {
    let mut query = vec![
        ("gameVersion".into(), p.minecraft.clone()),
        ("pageSize".into(), "50".into()),
    ];
    if filter_loader {
        query.push(("modLoaderType".into(), loader(p).into()));
    }
    let r = get(&format!("mods/{project}/files"), &query).await?;
    r["data"]
        .as_array()
        .and_then(|a| {
            a.iter()
                .find(|f| f["isAvailable"] == true && f["releaseType"] == 1)
        })
        .cloned()
        .ok_or("Keine kompatible CurseForge-Veröffentlichung gefunden".into())
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;
    #[test]
    fn download_metadata_requires_an_authorized_url_and_sha1() {
        let file=json!({"fileName":"fixture.jar","downloadUrl":"https://edge.forgecdn.net/files/1234/56/fixture.jar","hashes":[{"algo":1,"value":"a9993e364706816aba3e25717850c26c9cd0d89d"}]});
        assert!(download_info(&file).is_ok());
        let mut rejected=file.clone();rejected["downloadUrl"]=json!(null);
        assert!(download_info(&rejected).unwrap_err().contains("DISTRIBUTION_RESTRICTED"));
        rejected=file.clone();rejected["downloadUrl"]=json!("https://cdn.modrinth.com/data/fixture.jar");assert!(download_info(&rejected).is_err());
        rejected=file;rejected["hashes"]=json!([{ "algo":2, "value":"900150983cd24fb0d6963f7d28e17f72"}]);assert!(download_info(&rejected).is_err());
    }
}
