use crate::storage::{atomic, relative, Context, Result, Store};
use serde_json::{json, Value};
pub fn inspect(store: &Store, id: &str) -> Result<Value> {
    store.profile(id)?;
    let root = relative(&store.instance(id)?, "config/votex")?;
    let config_path = relative(&root, "performance.json")?;
    let config = if config_path.exists() {
        let bytes = std::fs::read(&config_path).context("Read performance settings")?;
        if bytes.len() > 16_000 { return Err("Performance configuration exceeds limit".into()); }
        let value: Value = serde_json::from_slice(&bytes).context("Parse performance settings")?;
        validate(&value)?; value
    } else { json!({"schemaVersion":1,"textCache":true,"reusableMotion":true,"adaptive":"DISABLED","targetFps":120}) };
    let report_path = relative(&root, "performance-latest.json")?;
    let report = if report_path.exists() {
        if std::fs::metadata(&report_path).context("Inspect benchmark")?.len() > 8_000_000 { return Err("Benchmark exceeds limit".into()); }
        let value: Value = serde_json::from_slice(&std::fs::read(report_path).context("Read benchmark")?).context("Parse benchmark")?;
        if value["schemaVersion"] != 1 { return Err("Unsupported benchmark schema".into()); }
        json!({"created":value["created"],"scenario":value["scenario"],"frames":value["frames"],"environment":value["environment"],"votex":value["votex"],"unavailable":value["unavailable"],"truncated":value["truncated"],"systemLast":value["system"].as_array().and_then(|a|a.last())})
    } else { Value::Null };
    Ok(json!({"config":config,"report":report}))
}
pub(crate) fn validate(value: &Value) -> Result<()> {
    if value["schemaVersion"] != 1 || !value["textCache"].is_boolean() || !value["reusableMotion"].is_boolean()
      || !matches!(value["adaptive"].as_str(), Some("DISABLED"|"CONSERVATIVE"|"BALANCED"|"AGGRESSIVE"))
      || !value["targetFps"].as_u64().is_some_and(|n|(30..=360).contains(&n)) {
       return Err("Invalid performance settings; original preserved".into());
    }
    Ok(())
}
pub fn save(store: &Store, id: &str, value: Value) -> Result<()> {
    validate(&value)?;
    store.profile(id)?;
    let root=relative(&store.instance(id)?,"config/votex")?;
    std::fs::create_dir_all(&root).context("Create performance configuration directory")?;
    atomic(&relative(&root,"performance.json")?,&serde_json::to_vec_pretty(&value).context("Encode performance configuration")?)?;
    crate::game_options::sync_engine(store,id)?;
    Ok(())
}
#[cfg(test)] mod tests { use super::*;
 #[test] fn rejects_invalid_modes_and_target_without_mutating(){let mut v=json!({"schemaVersion":1,"textCache":true,"reusableMotion":false,"adaptive":"DISABLED","targetFps":120});assert!(validate(&v).is_ok());v["adaptive"]=json!("pretend-fast");assert!(validate(&v).is_err());v["adaptive"]=json!("BALANCED");v["targetFps"]=json!(-1);assert!(validate(&v).is_err());}
}
