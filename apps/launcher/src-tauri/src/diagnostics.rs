use serde_json::{json, Value};
use std::{fs, path::Path, time::SystemTime};
pub fn inspect(game: &Path, log: &Path, started: SystemTime, secret: &str) -> Value {
    let log_text = fs::read_to_string(log).unwrap_or_default();
    let mut report = String::new();
    let mut name = None;
    if let Ok(entries) = fs::read_dir(game.join("crash-reports")) {
        let latest = entries
            .filter_map(|e| e.ok())
            .filter(|e| e.path().extension().and_then(|s| s.to_str()) == Some("txt"))
            .filter_map(|e| {
                let m = fs::symlink_metadata(e.path()).ok()?;
                if !m.is_file() || m.file_type().is_symlink() || m.len() > 1_048_576 {
                    return None;
                }
                let t = m.modified().ok()?;
                if t < started {
                    return None;
                }
                Some((t, e.path()))
            })
            .max_by_key(|(t, _)| *t);
        if let Some((_, path)) = latest {
            if let Ok(text) = fs::read_to_string(&path) {
                report = text.chars().take(20_000).collect();
                name = path.file_name().map(|s| s.to_string_lossy().to_string());
            }
        }
    }
    let combined = format!("{log_text}\n{report}");
    let signals = signals(&combined);
    if !secret.is_empty() {
        report = report.replace(secret, "[REDACTED]")
    }
    json!({"signals":signals,"reportName":name,"reportExcerpt":report})
}
fn signals(text: &str) -> Vec<&'static str> {
    let mut found = vec![];
    for (pattern, message) in [
        (
            "UnsupportedClassVersionError",
            "Log evidence: a class requires a different Java version.",
        ),
        (
            "OutOfMemoryError",
            "Log evidence: Java reported an out-of-memory error.",
        ),
        (
            "Incompatible mods found!",
            "Log evidence: Fabric reported incompatible or missing mod dependencies.",
        ),
        (
            "GLFW error",
            "Log evidence: GLFW reported a window or graphics initialization error.",
        ),
    ] {
        if text.contains(pattern) {
            found.push(message)
        }
    }
    found
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn evidence_only() {
        assert!(signals("exit code 1").is_empty());
        assert_eq!(signals("java.lang.OutOfMemoryError: heap space").len(), 1);
    }
    #[test]
    fn ignores_stale_reports_and_redacts_tokens() {
        let t = tempfile::tempdir().unwrap();
        fs::create_dir(t.path().join("crash-reports")).unwrap();
        fs::write(t.path().join("log.txt"), "Incompatible mods found!").unwrap();
        fs::write(
            t.path().join("crash-reports/crash.txt"),
            "token is secret-token",
        )
        .unwrap();
        let d = inspect(
            t.path(),
            &t.path().join("log.txt"),
            SystemTime::UNIX_EPOCH,
            "secret-token",
        );
        assert_eq!(d["signals"].as_array().unwrap().len(), 1);
        assert!(d["reportExcerpt"].as_str().unwrap().contains("[REDACTED]"));
        let d = inspect(
            t.path(),
            &t.path().join("log.txt"),
            SystemTime::now() + std::time::Duration::from_secs(10),
            "secret-token",
        );
        assert!(d["reportName"].is_null());
    }
}
