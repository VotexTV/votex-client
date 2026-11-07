use crate::storage::{atomic, relative, safe_id, Context, Result, Store};
use chrono::{DateTime, SecondsFormat, Utc};
use serde_json::{json, Value};
use std::{
    fs,
    net::{Ipv4Addr, Ipv6Addr},
    path::Path,
};

/// A server address is one Minecraft argument, never shell text or a URL.
pub fn server_address(value: &str) -> Result<String> {
    if value.bytes().any(|byte| byte.is_ascii_control()) {
        return Err("Ungültige Minecraft-Serveradresse.".into());
    }
    let value = value.trim();
    if value.is_empty()
        || value.len() > 255
        || !value.is_ascii()
        || value
            .bytes()
            .any(|b| b.is_ascii_control() || b.is_ascii_whitespace() || b"/\\@?#\"'=".contains(&b))
    {
        return Err("Ungültige Minecraft-Serveradresse.".into());
    }
    let (host, suffix) = if let Some(bracketed) = value.strip_prefix('[') {
        let (host, rest) = bracketed
            .split_once(']')
            .ok_or("IPv6-Adressen benötigen geschlossene eckige Klammern.")?;
        let ip: Ipv6Addr = host.parse().map_err(|_| "Ungültige IPv6-Serveradresse.")?;
        (format!("[{ip}]"), rest)
    } else {
        let (host, suffix) = value
            .split_once(':')
            .map(|(h, _)| (h, &value[h.len()..]))
            .unwrap_or((value, ""));
        if host.is_empty()
            || host.len() > 253
            || host.starts_with('-')
            || host.ends_with('-')
            || host.split('.').any(|label| {
                label.is_empty()
                    || label.len() > 63
                    || label.starts_with('-')
                    || label.ends_with('-')
                    || !label
                        .bytes()
                        .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
            })
        {
            return Err("Ungültiger Minecraft-Hostname.".into());
        }
        if host.bytes().all(|b| b.is_ascii_digit() || b == b'.')
            && host.parse::<Ipv4Addr>().is_err()
        {
            return Err("Ungültige IPv4-Serveradresse.".into());
        }
        (host.to_ascii_lowercase(), suffix)
    };
    if suffix.is_empty() {
        return Ok(host);
    }
    let port = suffix
        .strip_prefix(':')
        .filter(|p| !p.is_empty() && p.bytes().all(|b| b.is_ascii_digit()))
        .ok_or("Ungültiger Minecraft-Port.")?;
    let port: u16 = port
        .parse()
        .map_err(|_| "Der Minecraft-Port muss zwischen 1 und 65535 liegen.")?;
    if port == 0 {
        return Err("Der Minecraft-Port muss zwischen 1 und 65535 liegen.".into());
    }
    Ok(format!("{host}:{port}"))
}

pub fn direct_join_arguments(metadata: &Value, address: Option<&str>) -> Result<Vec<String>> {
    let Some(address) = address else {
        return Ok(vec![]);
    };
    let address = server_address(address)?;
    // The official version metadata advertises this feature. Old versions keep
    // their existing normal-launch behavior and get a clear unsupported result.
    let supported = metadata["arguments"]["game"]
        .as_array()
        .is_some_and(|arguments| {
            arguments.iter().any(|argument| {
                argument["value"].as_array().is_some_and(|values| {
                    values
                        .iter()
                        .any(|value| value.as_str() == Some("--quickPlayMultiplayer"))
                })
            })
        });
    if !supported {
        return Err("Diese Minecraft-Version unterstützt den direkten Serverbeitritt nicht. Starte sie und wähle den Server im Multiplayer-Menü.".into());
    }
    Ok(vec!["--quickPlayMultiplayer".into(), address])
}

pub fn record_launched(store: &Store, id: &str) -> Result<()> {
    safe_id(id)?;
    let _lock = store.lock.lock().map_err(|_| "Play history lock failed")?;
    let value = json!({"schemaVersion":1,"lastProfileId":id,"launchedAt":Utc::now().to_rfc3339_opts(SecondsFormat::Millis,true)});
    atomic(
        &store.root.join("config/play-history.json"),
        &serde_json::to_vec_pretty(&value).context("Encode play history")?,
    )
}

fn bounded_json(root: &Path, path: &Path) -> Option<Value> {
    let metadata = fs::symlink_metadata(path).ok()?;
    if !metadata.is_file() || metadata.file_type().is_symlink() || metadata.len() > 16_384 {
        return None;
    }
    let real = fs::canonicalize(path).ok()?;
    if !real.starts_with(fs::canonicalize(root).ok()?) {
        return None;
    }
    serde_json::from_slice(&fs::read(real).ok()?).ok()
}

pub fn inspect(store: &Store) -> Result<Value> {
    if !store.root.join("config/launcher.json").exists() {
        return Ok(json!({}));
    }
    let state = store.state()?;
    let profiles = state["profiles"].as_array().ok_or("Missing profiles")?;
    let ids: Vec<&str> = profiles.iter().filter_map(|p| p["id"].as_str()).collect();
    let mut result = json!({});
    if let Some(history) = bounded_json(&store.root, &store.root.join("config/play-history.json")) {
        if history["schemaVersion"] == 1
            && history["lastProfileId"]
                .as_str()
                .is_some_and(|id| ids.contains(&id))
        {
            result["lastProfileId"] = history["lastProfileId"].clone();
        }
    }
    let mut latest: Option<(i64, Value)> = None;
    for id in ids.into_iter().take(128) {
        let path = relative(&store.instance(id)?, "config/votex/last-server.json")?;
        let Some(value) = bounded_json(&store.root, &path) else {
            continue;
        };
        if value["schemaVersion"] != 1 || value["profileId"] != id {
            continue;
        }
        let Some(address) = value["address"]
            .as_str()
            .and_then(|address| server_address(address).ok())
        else {
            continue;
        };
        let Some(joined) = value["joinedAt"]
            .as_str()
            .and_then(|time| DateTime::parse_from_rfc3339(time).ok())
        else {
            continue;
        };
        let stamp = joined.timestamp_millis();
        if stamp < 0 || stamp > Utc::now().timestamp_millis() + 300_000 {
            continue;
        }
        let name = value["name"]
            .as_str()
            .filter(|name| {
                !name.trim().is_empty()
                    && name.chars().count() <= 80
                    && !name.chars().any(char::is_control)
            })
            .unwrap_or(&address);
        let item = json!({"name":name,"address":address,"profileId":id,"joinedAt":joined.to_rfc3339_opts(SecondsFormat::Millis,true)});
        if latest
            .as_ref()
            .is_none_or(|(previous, _)| stamp > *previous)
        {
            latest = Some((stamp, item));
        }
    }
    if let Some((_, server)) = latest {
        result["lastServer"] = server;
    }
    Ok(result)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn direct_join_is_one_validated_value_and_optional() {
        let metadata = json!({"arguments":{"game":[{"rules":[{"features":{"is_quick_play_multiplayer":true}}],"value":["--quickPlayMultiplayer","${quickPlayMultiplayer}"]}]}});
        assert!(direct_join_arguments(&metadata, None).unwrap().is_empty());
        assert_eq!(
            direct_join_arguments(&metadata, Some("MC.Example.org:25566")).unwrap(),
            vec!["--quickPlayMultiplayer", "mc.example.org:25566"]
        );
        assert!(direct_join_arguments(&json!({}), Some("example.org")).is_err());
        for bad in [
            "--server",
            "https://example.org",
            "example.org --demo",
            "example.org:0",
            "example.org:65536",
            "example.org:1:2",
            "[not-ipv6]",
            "example.org\n--username",
            "localhost\n",
            "a@host",
            "999.2.3.4",
        ] {
            assert!(server_address(bad).is_err(), "{bad}");
        }
        assert_eq!(
            server_address("[2001:db8::1]:25565").unwrap(),
            "[2001:db8::1]:25565"
        );
    }
    #[test]
    fn history_uses_only_successful_known_profile_records_and_does_not_change_state() {
        let temp = tempfile::tempdir().unwrap();
        let store = Store::new(temp.path().join("data")).unwrap();
        let state =
            json!({"schemaVersion":1,"profiles":[{"id":"a"},{"id":"b"}],"activeProfile":"b"});
        atomic(
            &store.root.join("config/launcher.json"),
            &serde_json::to_vec(&state).unwrap(),
        )
        .unwrap();
        record_launched(&store, "a").unwrap();
        let older = store
            .instance("a")
            .unwrap()
            .join("config/votex/last-server.json");
        let newer = store
            .instance("b")
            .unwrap()
            .join("config/votex/last-server.json");
        atomic(&older,&serde_json::to_vec(&json!({"schemaVersion":1,"profileId":"a","name":"Alpha","address":"play.example.org","joinedAt":"2026-01-01T00:00:00Z"})).unwrap()).unwrap();
        atomic(&newer,&serde_json::to_vec(&json!({"schemaVersion":1,"profileId":"b","name":"Beta","address":"localhost:25566","joinedAt":"2026-02-01T00:00:00Z"})).unwrap()).unwrap();
        let history = inspect(&store).unwrap();
        assert_eq!(history["lastProfileId"], "a");
        assert_eq!(history["lastServer"]["profileId"], "b");
        assert_eq!(history["lastServer"]["address"], "localhost:25566");
        assert_eq!(store.state().unwrap(), state);
        atomic(&newer,b"{\"schemaVersion\":1,\"profileId\":\"a\",\"address\":\"wrong.example.org\",\"joinedAt\":\"2026-03-01T00:00:00Z\"}").unwrap();
        assert_eq!(inspect(&store).unwrap()["lastServer"]["profileId"], "a");
    }
}
