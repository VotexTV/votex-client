use crate::{
    auth, network,
    storage::{Context, Result, Store},
};
use reqwest::Method;
use serde_json::{json, Value};
use std::sync::Arc;
use tauri::State;

const API: &str = "https://votex-client-project.adoredpeach257.chatgpt.site/api/community";
const MAX_RESPONSE: usize = 3_145_728;
type Db<'a> = State<'a, Arc<Store>>;

async fn request(
    path: &str,
    method: Method,
    token: Option<&str>,
    body: Option<Value>,
) -> Result<Value> {
    let url = format!("{API}{path}");
    network::allowed(&url)?;
    let client = network::client()?;
    let mut builder = client
        .request(method, &url)
        .timeout(std::time::Duration::from_secs(20));
    if let Some(token) = token {
        if token.len() > 5000 || token.bytes().any(|b| b == b'\r' || b == b'\n') {
            return Err("Minecraft community token rejected".into());
        }
        builder = builder.bearer_auth(token);
    }
    if let Some(body) = body {
        builder = builder.json(&body);
    }
    let response = builder.send().await.context("Connect to Votex Community")?;
    if response
        .content_length()
        .is_some_and(|n| n > MAX_RESPONSE as u64)
    {
        return Err("Community response too large".into());
    }
    let status = response.status();
    let bytes = response
        .bytes()
        .await
        .context("Read Votex Community response")?;
    if bytes.len() > MAX_RESPONSE {
        return Err("Community response too large".into());
    }
    let value: Value =
        serde_json::from_slice(&bytes).context("Invalid Votex Community response")?;
    if !status.is_success() {
        return Err(value["error"]
            .as_str()
            .filter(|s| s.len() < 300)
            .unwrap_or("Community request failed")
            .to_string());
    }
    Ok(value)
}

fn item_id(id: &str) -> Result<String> {
    uuid::Uuid::parse_str(id)
        .map(|value| value.to_string())
        .map_err(|_| "Invalid community item ID".into())
}

fn requested_visibility(
    visibility: Option<&str>,
    published: Option<bool>,
    required: bool,
) -> Result<&'static str> {
    let selected = match visibility {
        Some("private") => Some("private"),
        Some("unlisted") => Some("unlisted"),
        Some("public") => Some("public"),
        Some(_) => return Err("Invalid community visibility".into()),
        None => None,
    };
    if let (Some(selected), Some(published)) = (selected, published) {
        if published != (selected == "public") {
            return Err("Conflicting community visibility values".into());
        }
    }
    if let Some(selected) = selected {
        return Ok(selected);
    }
    match published {
        Some(true) => Ok("public"),
        Some(false) => Ok("private"),
        None if !required => Ok("public"),
        None => Err("Community visibility is required".into()),
    }
}

#[tauri::command]
pub async fn community_list(kind: String, offset: Option<u32>) -> Result<Value> {
    if !matches!(kind.as_str(), "cosmetic" | "emote" | "cape") {
        return Err("Invalid community category".into());
    }
    request(
        &format!(
            "/items?kind={kind}&offset={}",
            offset.unwrap_or(0).min(10_000)
        ),
        Method::GET,
        None,
        None,
    )
    .await
}

#[tauri::command]
pub async fn community_share(token: String) -> Result<Value> {
    if token.len() != 72 || !token.bytes().all(|b| b.is_ascii_hexdigit() || b == b'-') {
        return Err("Invalid community share link".into());
    }
    request(&format!("/share/{token}"), Method::GET, None, None).await
}

#[tauri::command]
pub async fn community_mine(store: Db<'_>) -> Result<Value> {
    let identity = auth::identity(&store).await?;
    request("/items/mine", Method::GET, Some(&identity.token), None).await
}

#[tauri::command]
pub async fn community_publish(
    store: Db<'_>,
    kind: String,
    name: String,
    data: Value,
    visibility: Option<String>,
    published: Option<bool>,
) -> Result<Value> {
    if !matches!(kind.as_str(), "cosmetic" | "emote" | "cape") {
        return Err("Invalid community category".into());
    }
    let visibility = requested_visibility(visibility.as_deref(), published, false)?;
    let identity = auth::identity(&store).await?;
    request("/items", Method::POST, Some(&identity.token), Some(json!({"kind":kind,"name":name,"data":data,"visibility":visibility,"published":visibility == "public"}))).await
}

#[tauri::command]
pub async fn community_visibility(
    store: Db<'_>,
    id: String,
    visibility: Option<String>,
    published: Option<bool>,
) -> Result<Value> {
    let id = item_id(&id)?;
    let visibility = requested_visibility(visibility.as_deref(), published, true)?;
    let identity = auth::identity(&store).await?;
    request(
        &format!("/items/{id}"),
        Method::PATCH,
        Some(&identity.token),
        Some(json!({"visibility":visibility,"published":visibility == "public"})),
    )
    .await
}

#[tauri::command]
pub async fn community_delete(store: Db<'_>, id: String) -> Result<Value> {
    let id = item_id(&id)?;
    let identity = auth::identity(&store).await?;
    request(
        &format!("/items/{id}"),
        Method::DELETE,
        Some(&identity.token),
        None,
    )
    .await
}

#[tauri::command]
pub async fn community_report(
    store: Db<'_>,
    id: String,
    reason: String,
    details: String,
) -> Result<Value> {
    let id = item_id(&id)?;
    if !matches!(reason.as_str(), "inappropriate" | "copyright" | "other")
        || details.chars().count() > 500
    {
        return Err("Invalid report".into());
    }
    let identity = auth::identity(&store).await?;
    request(
        &format!("/items/{id}/report"),
        Method::POST,
        Some(&identity.token),
        Some(json!({"reason":reason,"details":details})),
    )
    .await
}
