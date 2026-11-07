use crate::{
    network,
    storage::{atomic, read_json, Context, Result, Store},
};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::{
    future::Future,
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc, Mutex,
    },
    time::Duration,
};
use tokio::{sync::watch, time::Instant};

const AUTH: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0";
const SCOPE: &str = "XboxLive.signin offline_access";
const CANCELLED: &str = "LOGIN_CANCELLED: Microsoft sign-in cancelled.";
const EXPIRED: &str = "LOGIN_EXPIRED: The Microsoft code has expired. Start sign-in again.";

struct PendingLogin {
    session_id: String,
    client_id: String,
    device_code: String,
    user_code: String,
    verification_uri: String,
    interval: Duration,
    deadline: Instant,
    cancelled: watch::Sender<bool>,
    finishing: AtomicBool,
}
#[derive(Default)]
pub struct Auth {
    pending: Mutex<Option<Arc<PendingLogin>>>,
    starting: tokio::sync::Mutex<()>,
}
#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Account {
    pub id: String,
    pub name: String,
    pub skin: String,
    pub model: String,
}
pub struct Identity {
    pub account: Account,
    pub token: String,
    pub xuid: String,
}

// Provider descriptions can echo credentials or personal data; return only known errors.
fn oauth_error(body: &Value, status: u16) -> String {
    match body["error"].as_str() {
        Some("invalid_client" | "unauthorized_client" | "invalid_scope") =>
            "MICROSOFT_SETUP_REQUIRED: Check Votex's registered Application ID, personal-account support and public-client flow configuration.".into(),
        Some("authorization_declined" | "access_denied") =>
            "LOGIN_DECLINED: Microsoft sign-in was declined. You can try again.".into(),
        Some("expired_token" | "code_expired") => EXPIRED.into(),
        Some("bad_verification_code") =>
            "LOGIN_EXPIRED: Microsoft no longer recognizes this code. Start sign-in again.".into(),
        Some("invalid_grant" | "interaction_required") =>
            "MICROSOFT_REAUTH_REQUIRED: Sign in to this Minecraft account again.".into(),
        _ => format!("MICROSOFT_UNAVAILABLE: Microsoft sign-in failed (HTTP {status}). Try again later."),
    }
}
fn service_error(label: &str, status: u16, body: &Value) -> String {
    if label == "Xbox authorization" {
        match body["XErr"].as_u64() {
            Some(2148916233) => return "XBOX_PROFILE_REQUIRED: Create an Xbox profile for this Microsoft account at xbox.com, then try again.".into(),
            Some(2148916235) => return "XBOX_REGION_RESTRICTED: Xbox Live is unavailable for this account's region.".into(),
            Some(2148916236 | 2148916237) => return "XBOX_VERIFICATION_REQUIRED: Complete the account verification requested by Xbox in your browser.".into(),
            Some(2148916238) => return "XBOX_FAMILY_REQUIRED: This account needs the required Xbox family/parental setup before it can sign in.".into(),
            _ => {}
        }
    }
    if label == "Minecraft authentication" && status == 403 {
        return "MINECRAFT_APP_NOT_APPROVED: Minecraft rejected Votex's application registration. The publisher must check Minecraft API access approval.".into();
    }
    if label == "Minecraft Java profile" && status == 404 {
        return "MINECRAFT_PROFILE_REQUIRED: This Microsoft account has no Minecraft Java profile. Complete the Java profile setup at minecraft.net.".into();
    }
    format!("{label} failed (HTTP {status}). Check the account and Votex application registration.")
}
async fn response(r: reqwest::Response, label: &str) -> Result<Value> {
    let status = r.status();
    let body: Value = r
        .json()
        .await
        .context("Invalid authentication service response")?;
    if !status.is_success() {
        return Err(service_error(label, status.as_u16(), &body));
    }
    Ok(body)
}
fn field<'a>(v: &'a Value, key: &str) -> Result<&'a str> {
    v[key]
        .as_str()
        .filter(|s| !s.is_empty())
        .ok_or(format!("Authentication response missing {key}"))
}
fn client_id(store: &Store) -> Result<String> {
    let state = store.state()?;
    let bundled: Value = serde_json::from_str(include_str!(
        "../../../../packages/shared-config/microsoft.json"
    ))
    .context("Invalid bundled Microsoft application configuration")?;
    let configured = state["clientId"].as_str().unwrap_or("").trim();
    let id = if configured.is_empty() {
        bundled["clientId"].as_str().unwrap_or("").trim()
    } else {
        configured
    };
    let parsed = uuid::Uuid::parse_str(id).ok().filter(|id| !id.is_nil());
    parsed.map(|id| id.to_string()).ok_or_else(||
        "MICROSOFT_SETUP_REQUIRED: Votex needs its own registered Microsoft Application ID before Microsoft sign-in is available.".into())
}
fn verification_url(url: &str) -> Result<()> {
    let url = reqwest::Url::parse(url).context("Invalid Microsoft verification address")?;
    if url.scheme() != "https"
        || !url.username().is_empty()
        || url.password().is_some()
        || url.port().is_some_and(|p| p != 443)
        || !matches!(
            url.host_str(),
            Some("microsoft.com" | "www.microsoft.com" | "login.microsoftonline.com")
        )
    {
        return Err("Unexpected Microsoft verification address".into());
    }
    Ok(())
}
impl PendingLogin {
    fn from_response(client_id: String, data: Value, requested_at: Instant) -> Result<Self> {
        let expires_in = data["expires_in"]
            .as_u64()
            .filter(|v| (1..=3600).contains(v))
            .ok_or("Invalid Microsoft code expiry")?;
        let user_code = field(&data, "user_code")?.to_string();
        let device_code = field(&data, "device_code")?.to_string();
        let verification_uri = field(&data, "verification_uri")?.to_string();
        verification_url(&verification_uri)?;
        if user_code.len() > 32
            || !user_code
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || b == b'-')
            || device_code.len() > 8192
        {
            return Err("Invalid Microsoft device authorization response".into());
        }
        let (cancelled, _) = watch::channel(false);
        Ok(Self {
            session_id: uuid::Uuid::new_v4().to_string(),
            client_id,
            device_code,
            user_code,
            verification_uri,
            interval: Duration::from_secs(data["interval"].as_u64().unwrap_or(5).max(5)),
            deadline: requested_at + Duration::from_secs(expires_in),
            cancelled,
            finishing: AtomicBool::new(false),
        })
    }
    fn public_view(&self) -> Value {
        json!({"sessionId":self.session_id,"userCode":self.user_code,
            "verificationUri":self.verification_uri,
            "expiresIn":self.deadline.saturating_duration_since(Instant::now()).as_secs()})
    }
}
impl Auth {
    fn session(&self, id: &str) -> Result<Arc<PendingLogin>> {
        self.pending
            .lock()
            .map_err(|_| "Authentication lock failed")?
            .as_ref()
            .filter(|p| p.session_id == id)
            .cloned()
            .ok_or_else(|| CANCELLED.into())
    }
}
// An old request must never remove a newer login attempt when it finishes.
struct LoginLease<'a> {
    auth: &'a Auth,
    session_id: String,
}
impl Drop for LoginLease<'_> {
    fn drop(&mut self) {
        let _ = cancel(self.auth, &self.session_id);
    }
}
pub async fn start(store: &Store, auth: &Auth) -> Result<Value> {
    let _start = auth
        .starting
        .try_lock()
        .map_err(|_| "A Microsoft sign-in is already starting")?;
    let id = client_id(store)?;
    // An explicit new attempt also recovers from a reloaded/closed WebView.
    if let Some(previous) = auth
        .pending
        .lock()
        .map_err(|_| "Authentication lock failed")?
        .take()
    {
        previous.cancelled.send_replace(true);
    }
    let c = network::client()?;
    let requested_at = Instant::now();
    let r = c
        .post(format!("{AUTH}/devicecode"))
        .timeout(Duration::from_secs(30))
        .form(&[("client_id", id.as_str()), ("scope", SCOPE)])
        .send()
        .await
        .context("Microsoft sign-in unavailable")?;
    let status = r.status();
    let data: Value = r
        .json()
        .await
        .context("Invalid Microsoft authorization response")?;
    if !status.is_success() {
        return Err(oauth_error(&data, status.as_u16()));
    }
    let pending = Arc::new(PendingLogin::from_response(id, data, requested_at)?);
    if Instant::now() >= pending.deadline {
        return Err(EXPIRED.into());
    }
    let view = pending.public_view();
    *auth
        .pending
        .lock()
        .map_err(|_| "Authentication lock failed")? = Some(pending);
    Ok(view)
}
pub fn open_login(auth: &Auth, session_id: &str) -> Result<()> {
    let p = auth.session(session_id)?;
    if Instant::now() >= p.deadline {
        return Err(EXPIRED.into());
    }
    open::that(&p.verification_uri).context("Open Microsoft in the system browser")
}
pub fn cancel(auth: &Auth, session_id: &str) -> Result<bool> {
    let mut active = auth
        .pending
        .lock()
        .map_err(|_| "Authentication lock failed")?;
    if active.as_ref().is_some_and(|p| p.session_id == session_id) {
        if let Some(p) = active.take() {
            p.cancelled.send_replace(true);
        }
        Ok(true)
    } else {
        Ok(false)
    }
}
async fn interruptible<T>(
    p: &PendingLogin,
    cancel: &mut watch::Receiver<bool>,
    work: impl Future<Output = Result<T>>,
) -> Result<T> {
    if *cancel.borrow() {
        return Err(CANCELLED.into());
    }
    if Instant::now() >= p.deadline {
        return Err(EXPIRED.into());
    }
    tokio::select! {
        biased;
        _ = cancel.changed() => Err(CANCELLED.into()),
        _ = tokio::time::sleep_until(p.deadline) => Err(EXPIRED.into()),
        result = work => result,
    }
}
enum TokenReply {
    Pending,
    SlowDown,
    Authorized { access: String, refresh: String },
}
fn token_reply(status: reqwest::StatusCode, data: Value) -> Result<TokenReply> {
    match data["error"].as_str() {
        Some("authorization_pending") if status == reqwest::StatusCode::BAD_REQUEST => {
            Ok(TokenReply::Pending)
        }
        Some("slow_down") if status == reqwest::StatusCode::BAD_REQUEST => Ok(TokenReply::SlowDown),
        Some(_) => Err(oauth_error(&data, status.as_u16())),
        None if status.is_success() => Ok(TokenReply::Authorized {
            access: field(&data, "access_token")?.to_string(),
            refresh: field(&data, "refresh_token")?.to_string(),
        }),
        None => Err(oauth_error(&data, status.as_u16())),
    }
}
async fn poll_token(c: &reqwest::Client, p: &PendingLogin, url: &str) -> Result<(String, String)> {
    let mut cancel = p.cancelled.subscribe();
    let mut interval = p.interval;
    loop {
        interruptible(p, &mut cancel, async {
            tokio::time::sleep(interval).await;
            Ok(())
        })
        .await?;
        let reply = interruptible(p, &mut cancel, async {
            let r = c
                .post(url)
                .timeout(Duration::from_secs(30))
                .form(&[
                    ("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                    ("client_id", p.client_id.as_str()),
                    ("device_code", p.device_code.as_str()),
                ])
                .send()
                .await
                .context("Microsoft sign-in request failed")?;
            let status = r.status();
            token_reply(
                status,
                r.json().await.context("Invalid Microsoft token response")?,
            )
        })
        .await?;
        match reply {
            TokenReply::Pending => {}
            TokenReply::SlowDown => interval = interval.saturating_add(Duration::from_secs(5)),
            TokenReply::Authorized { access, refresh } => return Ok((access, refresh)),
        }
    }
}
fn credential_name(client_id: &str, account_id: &str) -> String {
    format!("{client_id}:{account_id}")
}
fn skin_url(raw: &str) -> String {
    // The profile service can still return legacy HTTP texture URLs.
    let Ok(mut url) = reqwest::Url::parse(raw) else {
        return String::new();
    };
    if url.host_str() != Some("textures.minecraft.net")
        || !url.username().is_empty()
        || url.password().is_some()
        || url.port().is_some()
        || !url.path().starts_with("/texture/")
    {
        return String::new();
    }
    if url.scheme() == "http" {
        let _ = url.set_scheme("https");
    }
    if url.scheme() != "https" {
        return String::new();
    }
    url.into()
}
fn save_metadata(store: &Store, value: &Value) -> Result<()> {
    atomic(
        &store.root.join("accounts/metadata.json"),
        &serde_json::to_vec_pretty(value).context("Serialize account metadata")?,
    )
}
pub async fn finish(store: &Store, auth: &Auth, session_id: &str) -> Result<Account> {
    let pending = auth.session(session_id)?;
    if pending.finishing.swap(true, Ordering::SeqCst) {
        return Err("This Microsoft sign-in is already being checked".into());
    }
    let _lease = LoginLease {
        auth,
        session_id: session_id.into(),
    };
    let c = network::client()?;
    let (access, refresh) = poll_token(&c, &pending, &format!("{AUTH}/token")).await?;
    let identity = interruptible(
        &pending,
        &mut pending.cancelled.subscribe(),
        exchange(&c, &access),
    )
    .await?;
    let _guard = store.lock.lock().map_err(|_| "Configuration lock failed")?;
    let busy = store.busy.lock().map_err(|_| "Operation lock failed")?;
    if *busy {
        return Err("Stop Minecraft and downloads before changing accounts".into());
    }
    let active = auth
        .pending
        .lock()
        .map_err(|_| "Authentication lock failed")?;
    if !active.as_ref().is_some_and(|p| p.session_id == session_id) {
        return Err(CANCELLED.into());
    }
    if Instant::now() >= pending.deadline {
        return Err(EXPIRED.into());
    }
    if client_id(store)? != pending.client_id {
        return Err("MICROSOFT_REAUTH_REQUIRED: Votex's Application ID changed during sign-in. Start again.".into());
    }
    let mut meta = accounts(store)?;
    let mut item = serde_json::to_value(&identity.account).context("Account metadata")?;
    item["clientId"] = json!(pending.client_id);
    let items = meta["items"]
        .as_array_mut()
        .ok_or("Invalid accounts metadata")?;
    items.retain(|x| x["id"] != identity.account.id);
    items.push(item);
    meta["selected"] = json!(identity.account.id);
    keyring::Entry::new(
        "VotexClient",
        &credential_name(&pending.client_id, &identity.account.id),
    )
    .context("Windows credential storage")?
    .set_password(&refresh)
    .context("Save refresh token securely")?;
    save_metadata(store, &meta)?;
    Ok(identity.account)
}
async fn exchange(c: &reqwest::Client, access: &str) -> Result<Identity> {
    let xbl = response(c.post("https://user.auth.xboxlive.com/user/authenticate")
        .json(&json!({"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":format!("d={access}")},"RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}))
        .send().await.context("Xbox authentication network error")?, "Xbox authentication").await?;
    let xsts = response(c.post("https://xsts.auth.xboxlive.com/xsts/authorize")
        .json(&json!({"Properties":{"SandboxId":"RETAIL","UserTokens":[field(&xbl,"Token")?]},"RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}))
        .send().await.context("Xbox authorization network error")?, "Xbox authorization").await?;
    let uhs = xsts["DisplayClaims"]["xui"][0]["uhs"]
        .as_str()
        .ok_or("Missing Xbox user hash")?;
    let mc = response(
        c.post("https://api.minecraftservices.com/authentication/login_with_xbox")
            .json(&json!({"identityToken":format!("XBL3.0 x={uhs};{}",field(&xsts,"Token")?)}))
            .send()
            .await
            .context("Minecraft authentication network error")?,
        "Minecraft authentication",
    )
    .await?;
    let token = field(&mc, "access_token")?.to_string();
    let entitlement = response(
        c.get("https://api.minecraftservices.com/entitlements/mcstore")
            .bearer_auth(&token)
            .send()
            .await
            .context("Minecraft ownership request failed")?,
        "Minecraft ownership verification",
    )
    .await?;
    if !entitlement["items"].as_array().is_some_and(|a| {
        a.iter().any(|v| {
            matches!(
                v["name"].as_str(),
                Some("game_minecraft" | "product_minecraft")
            )
        })
    }) {
        return Err(
            "MINECRAFT_NOT_OWNED: No Java Edition entitlement was returned for this account."
                .into(),
        );
    }
    let profile = response(
        c.get("https://api.minecraftservices.com/minecraft/profile")
            .bearer_auth(&token)
            .send()
            .await
            .context("Minecraft profile request failed")?,
        "Minecraft Java profile",
    )
    .await?;
    let skin = profile["skins"]
        .as_array()
        .and_then(|s| s.iter().find(|s| s["state"] == "ACTIVE"));
    let id = field(&profile, "id")?.to_string();
    if id.len() != 32 || !id.bytes().all(|b| b.is_ascii_hexdigit()) {
        return Err("Invalid Minecraft UUID".into());
    }
    Ok(Identity {
        account: Account {
            id,
            name: field(&profile, "name")?.to_string(),
            skin: skin_url(skin.and_then(|s| s["url"].as_str()).unwrap_or("")),
            model: skin
                .and_then(|s| s["variant"].as_str())
                .unwrap_or("CLASSIC")
                .into(),
        },
        token,
        xuid: xsts["DisplayClaims"]["xui"][0]["xid"]
            .as_str()
            .unwrap_or("")
            .into(),
    })
}
pub fn accounts(store: &Store) -> Result<Value> {
    let path = store.root.join("accounts/metadata.json");
    if path.exists() {
        read_json(&path)
    } else {
        Ok(json!({"schemaVersion":1,"selected":"","items":[]}))
    }
}
pub async fn identity(store: &Store) -> Result<Identity> {
    let meta = accounts(store)?;
    let id = meta["selected"]
        .as_str()
        .filter(|s| !s.is_empty())
        .ok_or("Sign in with a Microsoft Minecraft account first")?;
    let client_id = client_id(store)?;
    let item = meta["items"]
        .as_array()
        .and_then(|a| a.iter().find(|a| a["id"] == id))
        .ok_or("Unknown account")?;
    if item["clientId"] != client_id {
        return Err("MICROSOFT_REAUTH_REQUIRED: Sign in to this account again using Votex's current Application ID.".into());
    }
    let entry = keyring::Entry::new("VotexClient", &credential_name(&client_id, id))
        .context("Windows credentials")?;
    let refresh = entry
        .get_password()
        .context("Saved account unavailable; sign in again")?;
    let c = network::client()?;
    let r = c
        .post(format!("{AUTH}/token"))
        .timeout(Duration::from_secs(30))
        .form(&[
            ("grant_type", "refresh_token"),
            ("client_id", client_id.as_str()),
            ("refresh_token", refresh.as_str()),
            ("scope", SCOPE),
        ])
        .send()
        .await
        .context("Refresh authentication failed")?;
    let status = r.status();
    let token: Value = r
        .json()
        .await
        .context("Invalid Microsoft refresh response")?;
    if !status.is_success() || token["error"].is_string() {
        return Err(oauth_error(&token, status.as_u16()));
    }
    // Keep rotation even when a downstream Xbox/Minecraft request fails.
    if let Some(new) = token["refresh_token"].as_str().filter(|s| !s.is_empty()) {
        entry
            .set_password(new)
            .context("Refresh secure credentials")?;
    }
    let result = exchange(&c, field(&token, "access_token")?).await?;
    if result.account.id != id {
        return Err("Refreshed account identity mismatch".into());
    }
    Ok(result)
}
pub fn select(store: &Store, id: &str) -> Result<()> {
    let _guard = store.lock.lock().map_err(|_| "Account lock failed")?;
    let mut meta = accounts(store)?;
    if !meta["items"]
        .as_array()
        .ok_or("Invalid accounts")?
        .iter()
        .any(|a| a["id"] == id)
    {
        return Err("Unknown account".into());
    }
    meta["selected"] = json!(id);
    save_metadata(store, &meta)
}
pub fn remove(store: &Store, id: &str) -> Result<()> {
    let _guard = store.lock.lock().map_err(|_| "Account lock failed")?;
    let mut meta = accounts(store)?;
    let item = meta["items"]
        .as_array()
        .and_then(|a| a.iter().find(|a| a["id"] == id))
        .ok_or("Unknown account")?;
    let name = item["clientId"]
        .as_str()
        .map(|client| credential_name(client, id))
        .unwrap_or_else(|| id.into());
    let entry = keyring::Entry::new("VotexClient", &name).context("Windows credentials")?;
    match entry.delete_credential() {
        Ok(()) | Err(keyring::Error::NoEntry) => {}
        Err(e) => return Err(format!("Remove saved Microsoft credentials: {e}")),
    }
    let items = meta["items"]
        .as_array_mut()
        .ok_or("Invalid accounts metadata")?;
    items.retain(|a| a["id"] != id);
    if meta["selected"] == id {
        meta["selected"] = json!("");
    }
    save_metadata(store, &meta)
}

#[cfg(test)]
mod tests;
