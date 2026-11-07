use crate::{
    minecraft, network,
    storage::{atomic, read_json, relative, Context, Result, Store},
};
use serde_json::{json, Value};
use std::{
    io::Read,
    path::{Path, PathBuf},
};

struct Package {
    sum: String,
    url: String,
    distribution: &'static str,
}

impl Package {
    fn new(sum: &str, url: &str, size: u64, distribution: &'static str) -> Result<Self> {
        if sum.len() != 64 || !sum.bytes().all(|b| b.is_ascii_hexdigit()) {
            return Err("Invalid Java checksum".into());
        }
        let parsed = reqwest::Url::parse(url).context("Invalid Java download")?;
        let source = match distribution {
            "Eclipse Temurin" => {
                parsed.host_str() == Some("github.com") && parsed.path().starts_with("/adoptium/")
            }
            "Azul Zulu" => {
                parsed.host_str() == Some("cdn.azul.com") && parsed.path().starts_with("/zulu/bin/")
            }
            _ => false,
        };
        if !source
            || parsed.scheme() != "https"
            || !parsed.username().is_empty()
            || parsed.password().is_some()
            || parsed.port().is_some()
            || parsed.query().is_some()
            || parsed.fragment().is_some()
            || !parsed.path().ends_with(".zip")
        {
            return Err("Unapproved Java distribution source".into());
        }
        if size == 0 || size > 134_217_728 {
            return Err("Java package exceeds the 128 MiB download limit".into());
        }
        Ok(Self {
            sum: sum.to_ascii_lowercase(),
            url: url.into(),
            distribution,
        })
    }
    async fn download(&self, root: &Path) -> Result<()> {
        network::download(
            &network::client()?,
            &self.url,
            &root.join(format!("{}.zip", self.sum)),
            Some(("sha256", &self.sum)),
        )
        .await
    }
}

async fn temurin(major: u64) -> Result<Package> {
    let metadata = network::json(&network::client()?, &format!("https://api.adoptium.net/v3/assets/latest/{major}/hotspot?architecture=x64&image_type=jre&os=windows&vendor=eclipse")).await?;
    let package = &metadata
        .as_array()
        .and_then(|a| a.first())
        .ok_or("Keine passende kostenlose Java-Laufzeit verfügbar")?["binary"]["package"];
    Package::new(
        package["checksum"]
            .as_str()
            .ok_or("Missing Java checksum")?,
        package["link"].as_str().ok_or("Missing Java download")?,
        package["size"].as_u64().unwrap_or(u64::MAX),
        "Eclipse Temurin",
    )
}

fn zulu_package(value: &Value, major: u64, uuid: &str) -> Result<Package> {
    if value["package_uuid"] != uuid
        || value["java_version"][0] != major
        || value["os"] != "windows"
        || value["arch"] != "x86"
        || value["hw_bitness"] != 64
        || value["archive_type"] != "zip"
        || value["java_package_type"] != "jre"
        || value["availability_type"] != "CA"
        || value["release_status"] != "ga"
        || value["javafx_bundled"] != false
        || value["product"] != "zulu"
        || !value["certifications"]
            .as_array()
            .is_some_and(|a| a.iter().any(|v| v == "tck"))
    {
        return Err("Azul metadata does not match the required Windows x64 Java runtime".into());
    }
    Package::new(
        value["sha256_hash"]
            .as_str()
            .ok_or("Missing Azul Java checksum")?,
        value["download_url"]
            .as_str()
            .ok_or("Missing Azul Java download")?,
        value["size"].as_u64().unwrap_or(u64::MAX),
        "Azul Zulu",
    )
}

async fn zulu(major: u64) -> Result<Package> {
    let client = network::client()?;
    let metadata = network::json(&client, &format!("https://api.azul.com/metadata/v1/zulu/packages/?java_version={major}&os=windows&arch=x86&hw_bitness=64&archive_type=zip&java_package_type=jre&javafx_bundled=false&release_status=ga&availability_types=CA&certifications=tck&latest=true&page_size=10")).await?;
    let candidate = metadata
        .as_array()
        .and_then(|a| a.first())
        .ok_or("Keine alternative kostenlose Java-Laufzeit verfügbar")?;
    let uuid = candidate["package_uuid"]
        .as_str()
        .ok_or("Missing Azul package identity")?;
    uuid::Uuid::parse_str(uuid).context("Invalid Azul package identity")?;
    let details = network::json(
        &client,
        &format!("https://api.azul.com/metadata/v1/zulu/packages/{uuid}"),
    )
    .await?;
    zulu_package(&details, major, uuid)
}

async fn package(root: &Path, major: u64) -> Result<Package> {
    // A failed primary CDN must not require a manual Java installation. Each source
    // supplies its own HTTPS metadata and checksum; extraction is shared below.
    let primary = async {
        let selected = temurin(major).await?;
        selected.download(root).await?;
        Ok::<_, String>(selected)
    }
    .await;
    match primary {
        Ok(selected) => Ok(selected),
        Err(first) => {
            eprintln!("Portable Temurin runtime unavailable; trying official Azul Zulu: {first}");
            let alternate = async {
                let selected = zulu(major).await?;
                selected.download(root).await?;
                Ok::<_, String>(selected)
            }
            .await;
            alternate.map_err(|second| format!("Die automatische Java-Installation ist bei beiden offiziellen Quellen fehlgeschlagen. Temurin: {first}; Azul Zulu: {second}"))
        }
    }
}
/// A per-application portable runtime. Never changes PATH, registry, system Java or licenses.
pub async fn resolve(store: &Store, manual: &str, major: u64) -> Result<PathBuf> {
    if !manual.is_empty() {
        minecraft::java(manual).await?;
        return Ok(PathBuf::from(manual));
    }
    if !(8..=40).contains(&major) {
        return Err("Unsupported Java requirement".into());
    }
    let root = relative(&store.root, "cache/java")?;
    let receipt = root.join(format!("java-{major}.json"));
    if let Ok(r) = read_json(&receipt) {
        if let Some(path) = r["executable"].as_str() {
            if let Ok(path) = relative(&root, path) {
                if path.is_file() {
                    let info = minecraft::java(&path.to_string_lossy()).await?;
                    if info["major"] == major {
                        return Ok(path);
                    }
                }
            }
        }
    }
    let selected = package(&root, major).await?;
    let sum = selected.sum.as_str();
    let url = selected.url.as_str();
    let archive = root.join(format!("{sum}.zip"));
    if std::fs::metadata(&archive)
        .context("Read Java package size")?
        .len()
        > 134_217_728
    {
        return Err("Java package too large".into());
    }
    let destination = root.join(sum);
    let stage = root.join(format!("staging-{}", uuid::Uuid::new_v4()));
    std::fs::create_dir_all(&stage).context("Create Java staging")?;
    let mut zip = zip::ZipArchive::new(std::fs::File::open(&archive).context("Read Java package")?)
        .context("Invalid Java archive")?;
    if zip.len() > 10_000 {
        return Err("Java archive contains too many entries".into());
    }
    let mut total = 0u64;
    let mut executable = None;
    let mut names = std::collections::HashSet::new();
    for i in 0..zip.len() {
        let mut file = zip.by_index(i).context("Read Java archive entry")?;
        if file.is_dir() {
            continue;
        }
        let name = file.name().to_string();
        if file.enclosed_name().is_none()
            || name.contains('\\')
            || file
                .unix_mode()
                .map(|m| m & 0o170000 == 0o120000)
                .unwrap_or(false)
            || !names.insert(name.to_lowercase())
        {
            return Err("Unsafe Java archive path".into());
        }
        let path = relative(&stage, &name)?;
        total += file.size();
        if file.size() > 134_217_728 || total > 536_870_912 {
            return Err("Java archive exceeds extraction limits".into());
        }
        let mut bytes = Vec::new();
        file.by_ref()
            .take(134_217_729)
            .read_to_end(&mut bytes)
            .context("Read Java runtime file")?;
        if bytes.len() as u64 != file.size() {
            return Err("Invalid Java file size".into());
        }
        atomic(&path, &bytes)?;
        if name.ends_with("/bin/java.exe") {
            if executable.is_some() {
                return Err("Ambiguous Java executable".into());
            }
            executable = Some(name);
        }
    }
    let exe = executable.ok_or("Java runtime has no executable")?;
    if destination.exists() {
        return Err(
            "Java cache exists without a valid receipt; preserve it and repair the cache first"
                .into(),
        );
    }
    let staged_exe = relative(&stage, &exe)?;
    let info = minecraft::java(&staged_exe.to_string_lossy()).await?;
    if info["major"] != major {
        return Err("Downloaded Java major version mismatch".into());
    }
    std::fs::rename(&stage, &destination).context("Commit verified Java runtime")?;
    let relative_exe = format!("{sum}/{exe}");
    let chosen = relative(&root, &relative_exe)?;
    atomic(&receipt,&serde_json::to_vec_pretty(&json!({"major":major,"executable":relative_exe,"sha256":sum,"distribution":selected.distribution,"source":url,"license":"GPL-2.0-with-classpath-exception"})).context("Save runtime receipt")?)?;
    Ok(chosen)
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn rejects_substituted_runtime_metadata() {
        let uuid = "679c7eb0-dbc2-4869-843c-f51b78c8b219";
        let valid = json!({"package_uuid":uuid,"java_version":[25,0,5],"os":"windows","arch":"x86","hw_bitness":64,"archive_type":"zip","java_package_type":"jre","availability_type":"CA","release_status":"ga","javafx_bundled":false,"product":"zulu","certifications":["tck"],"sha256_hash":"a".repeat(64),"download_url":"https://cdn.azul.com/zulu/bin/runtime.zip","size":58_000_000});
        assert!(zulu_package(&valid, 25, uuid).is_ok());
        for (key, replacement) in [
            ("java_version", json!([21, 0, 1])),
            ("hw_bitness", json!(32)),
            ("os", json!("linux")),
            ("java_package_type", json!("jdk")),
            ("availability_type", json!("SA")),
            ("release_status", json!("ea")),
            ("certifications", json!([])),
            ("package_uuid", json!("another-package")),
            (
                "download_url",
                json!("https://cdn.azul.com.evil.test/zulu/bin/runtime.zip"),
            ),
            (
                "download_url",
                json!("https://cdn.azul.com/zulu/bin/runtime.zip?redirect=other"),
            ),
            ("sha256_hash", json!("bad checksum")),
            ("size", json!(134_217_729)),
        ] {
            let mut invalid = valid.clone();
            invalid[key] = replacement;
            assert!(
                zulu_package(&invalid, 25, uuid).is_err(),
                "Accepted invalid {key}"
            );
        }
        assert!(Package::new(
            &"A".repeat(64),
            "https://github.com/adoptium/temurin25-binaries/releases/download/a/runtime.zip",
            58_000_000,
            "Eclipse Temurin"
        )
        .is_ok());
        assert!(Package::new(
            &"a".repeat(64),
            "https://github.com/other/runtime.zip",
            58_000_000,
            "Eclipse Temurin"
        )
        .is_err());
    }
    #[tokio::test]
    #[ignore = "Explicit official portable Java 25 download and version verification"]
    async fn live_java_25() {
        let temp = tempfile::tempdir().unwrap();
        let store = Store::new(temp.path().join("votex")).unwrap();
        let p = resolve(&store, "", 25).await.unwrap();
        assert!(p.starts_with(&store.root));
        let info = minecraft::java(&p.to_string_lossy()).await.unwrap();
        assert_eq!(info["major"], 25);
        assert_eq!(resolve(&store, "", 25).await.unwrap(), p);
        let receipt = read_json(&store.root.join("cache/java/java-25.json")).unwrap();
        assert!(matches!(
            receipt["distribution"].as_str(),
            Some("Eclipse Temurin" | "Azul Zulu")
        ));
        println!("VOTEX_RUNTIME_HTTPS_PASS major=25 distribution={} sha256=true extractedVersionChecked=true cached=true game=false", receipt["distribution"]);
    }
}
