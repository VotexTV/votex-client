use crate::storage::{atomic, Context, Result};
use reqwest::Client;
use sha1::{Digest, Sha1};
use sha2::{Sha256, Sha512};
use std::{path::Path, time::Duration};
use tokio::io::AsyncWriteExt;
pub fn client() -> Result<Client> {
    Client::builder()
        .user_agent("VotexClient/0.2.0-alpha (local desktop launcher)")
        .connect_timeout(Duration::from_secs(20))
        .timeout(Duration::from_secs(180))
        .https_only(true)
        .redirect(reqwest::redirect::Policy::none())
        .build()
        .context("Create HTTPS client")
}
pub fn allowed(url: &str) -> Result<()> {
    let u = reqwest::Url::parse(url).context("Invalid download URL")?;
    let h = u.host_str().ok_or("Missing download host")?;
    if u.scheme() != "https"
        || !u.username().is_empty()
        || u.password().is_some()
        || ![
            "piston-meta.mojang.com",
            "piston-data.mojang.com",
            "launchermeta.mojang.com",
            "launcher.mojang.com",
            "libraries.minecraft.net",
            "resources.download.minecraft.net",
            "meta.fabricmc.net",
            "meta.quiltmc.org",
            "maven.quiltmc.org",
            "maven.minecraftforge.net",
            "files.minecraftforge.net",
            "maven.neoforged.net",
            "maven.parchmentmc.org",
            "maven.su5ed.dev",
            "maven.fabricmc.net",
            "repo.maven.apache.org",
            "cdn.modrinth.com",
            "edge.forgecdn.net",
            "mediafilez.forgecdn.net",
            "mediafiles.forgecdn.net",
            "raw.githubusercontent.com",
            "api.github.com",
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
            "api.adoptium.net",
            "api.azul.com",
            "cdn.azul.com",
            "textures.minecraft.net",
            "votex-client-project.adoredpeach257.chatgpt.site",
        ]
        .contains(&h)
    {
        return Err("Download source is not allowed".into());
    }
    Ok(())
}
fn download_client() -> Result<Client> {
    static DOWNLOADS: std::sync::OnceLock<std::result::Result<Client, String>> =
        std::sync::OnceLock::new();
    DOWNLOADS
        .get_or_init(|| {
            Client::builder()
                .user_agent("VotexClient/0.2-preview (local desktop launcher)")
                .connect_timeout(Duration::from_secs(20))
                .timeout(Duration::from_secs(300))
                .https_only(true)
                .redirect(reqwest::redirect::Policy::custom(|attempt| {
                    if attempt.previous().len() >= 3 {
                        attempt.error("Too many download redirects")
                    } else if allowed(attempt.url().as_str()).is_err() {
                        attempt.error("Redirect to an unapproved download host")
                    } else {
                        attempt.follow()
                    }
                }))
                .build()
                .context("Create download client")
        })
        .clone()
}
pub fn curseforge_file_url(url: &str) -> bool {
    reqwest::Url::parse(url).map(|url| {
        let segments: Vec<_> = url.path_segments().map(|parts| parts.collect()).unwrap_or_default();
        url.scheme() == "https" && url.username().is_empty() && url.password().is_none()
            && url.port().is_none() && url.query().is_none() && url.fragment().is_none()
            && matches!(url.host_str(), Some("edge.forgecdn.net" | "mediafilez.forgecdn.net" | "mediafiles.forgecdn.net"))
            && segments.len() == 4 && segments[0] == "files"
            && !segments[1].is_empty() && segments[1].bytes().all(|b| b.is_ascii_digit())
            && !segments[2].is_empty() && segments[2].bytes().all(|b| b.is_ascii_digit())
            && !segments[3].is_empty()
    }).unwrap_or(false)
}
fn curseforge_download_client() -> Result<Client> {
    let key = crate::curseforge::download_key()?;
    let mut value = reqwest::header::HeaderValue::from_str(&key).context("Ungültiger CurseForge-Schlüssel")?;
    value.set_sensitive(true);
    let mut headers = reqwest::header::HeaderMap::new();
    headers.insert("x-api-key", value);
    Client::builder().user_agent("VotexClient/0.4.1 (local desktop launcher)")
        .connect_timeout(Duration::from_secs(20)).timeout(Duration::from_secs(300)).https_only(true)
        .default_headers(headers)
        .redirect(reqwest::redirect::Policy::custom(|attempt| {
            if attempt.previous().len() >= 3 { attempt.error("Too many CurseForge redirects") }
            else if !curseforge_file_url(attempt.url().as_str()) { attempt.error("CurseForge redirect left its approved file CDN") }
            else { attempt.follow() }
        })).build().context("Create authenticated CurseForge download client")
}
pub async fn json(client: &Client, url: &str) -> Result<serde_json::Value> {
    allowed(url)?;
    client
        .get(url)
        .send()
        .await
        .context("Network request")?
        .error_for_status()
        .context("HTTP request")?
        .json()
        .await
        .context("Invalid server response")
}
pub fn hash(bytes: &[u8], algorithm: &str) -> Result<String> {
    match algorithm {
        "sha1" => Ok(hex::encode(Sha1::digest(bytes))),
        "sha512" => Ok(hex::encode(Sha512::digest(bytes))),
        "sha256" => Ok(hex::encode(Sha256::digest(bytes))),
        _ => Err("Unsupported hash".into()),
    }
}
pub async fn download(
    _client: &Client,
    url: &str,
    path: &Path,
    expected: Option<(&str, &str)>,
) -> Result<()> {
    download_limited(_client, url, path, expected, 2_147_483_648).await
}
pub async fn download_limited(
    _client: &Client,
    url: &str,
    path: &Path,
    expected: Option<(&str, &str)>,
    max_bytes: u64,
) -> Result<()> {
    if max_bytes == 0 || max_bytes > 2_147_483_648 {
        return Err("Invalid download size limit".into());
    }
    allowed(url)?;
    let client = if curseforge_file_url(url) { curseforge_download_client()? } else { download_client()? };
    if path.exists() {
        if let (Some((algo, sum)), Ok(metadata)) = (expected, tokio::fs::metadata(path).await) {
            if metadata.len() > max_bytes {
                let _ = tokio::fs::remove_file(path).await;
            } else if hash(
                &tokio::fs::read(path).await.context("Read cached file")?,
                algo,
            )? == sum
            {
                return Ok(());
            }
        }
    }
    tokio::fs::create_dir_all(path.parent().ok_or("Invalid download destination")?)
        .await
        .context("Create download folder")?;
    let part = path.with_extension("part");
    let mut offset = tokio::fs::metadata(&part)
        .await
        .map(|m| m.len())
        .unwrap_or(0);
    if offset > max_bytes {
        tokio::fs::remove_file(&part).await.context("Reset oversized partial file")?;
        offset = 0;
    }
    let mut request = client.get(url);
    if offset > 0 {
        request = request.header(reqwest::header::RANGE, format!("bytes={offset}-"));
    }
    let mut response = request
        .send()
        .await
        .context("Download failed; partial file retained for retry")?;
    if response.status() == reqwest::StatusCode::RANGE_NOT_SATISFIABLE {
        tokio::fs::remove_file(&part)
            .await
            .context("Reset partial download")?;
        offset = 0;
        response = client.get(url).send().await.context("Restart download")?;
    }
    response = response.error_for_status().context("Download rejected")?;
    if response.content_length().unwrap_or(0) > max_bytes {
        return Err("Download exceeds its size limit".into());
    }
    if response.status() != reqwest::StatusCode::PARTIAL_CONTENT {
        offset = 0;
    } else if offset > 0
        && !response
            .headers()
            .get(reqwest::header::CONTENT_RANGE)
            .and_then(|v| v.to_str().ok())
            .map(|v| v.starts_with(&format!("bytes {offset}-")))
            .unwrap_or(false)
    {
        return Err("Invalid download resume range".into());
    }
    let mut file = tokio::fs::OpenOptions::new()
        .create(true)
        .write(true)
        .append(offset > 0)
        .truncate(offset == 0)
        .open(&part)
        .await
        .context("Open partial download")?;
    let mut received = offset;
    while let Some(chunk) = response
        .chunk()
        .await
        .context("Download interrupted; retry to resume")?
    {
        received = received.saturating_add(chunk.len() as u64);
        if received > max_bytes {
            return Err("Download exceeds its size limit".into());
        }
        file.write_all(&chunk).await.context("Write download")?;
    }
    file.sync_all().await.context("Flush download")?;
    drop(file);
    let bytes = tokio::fs::read(&part)
        .await
        .context("Read completed download")?;
    if let Some((algo, sum)) = expected {
        if hash(&bytes, algo)? != sum {
            let _ = tokio::fs::remove_file(&part).await;
            return Err("Download checksum mismatch; file rejected".into());
        }
    }
    atomic(path, &bytes)?;
    tokio::fs::remove_file(part)
        .await
        .context("Remove download temporary file")?;
    Ok(())
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn trusted_sources_only() {
        assert!(allowed("https://cdn.modrinth.com/data/a.jar").is_ok());
        for s in [
            "http://cdn.modrinth.com/a",
            "https://evil.test/a",
            "https://cdn.modrinth.com.evil.test/a",
            "https://user@cdn.modrinth.com/a",
        ] {
            assert!(allowed(s).is_err());
        }
    }
    #[test]
    fn curseforge_authentication_stays_on_its_file_cdn() {
        assert!(curseforge_file_url("https://edge.forgecdn.net/files/1234/56/a.jar"));
        assert!(curseforge_file_url("https://mediafilez.forgecdn.net/files/1234/56/a.jar"));
        for url in ["http://edge.forgecdn.net/files/1/2/a.jar", "https://edge.forgecdn.net.evil.test/files/1/2/a.jar", "https://user@edge.forgecdn.net/files/1/2/a.jar", "https://edge.forgecdn.net/files/1/2/a.jar?apiKey=fixture", "https://api.curseforge.com/v1/mods/1", "https://cdn.modrinth.com/files/1/2/a.jar", "https://edge.forgecdn.net/avatars/x.png", "https://edge.forgecdn.net/files/1/2/../a.jar"] {
            assert!(!curseforge_file_url(url), "rejected source {url}");
        }
    }
    #[test]
    fn validates_hashes() {
        assert_eq!(
            hash(b"abc", "sha1").unwrap(),
            "a9993e364706816aba3e25717850c26c9cd0d89d"
        );
    }
}
