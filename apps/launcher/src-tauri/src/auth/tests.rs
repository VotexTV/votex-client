use super::*;
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    net::TcpListener,
};

const CLIENT: &str = "c821c1f1-66c3-4b5b-81ae-15f4f8104d8a"; // Local fixture only; never used at Microsoft.
#[test]
fn account_skin_upgrades_only_the_official_texture_host_to_https() {
    assert_eq!(
        skin_url("http://textures.minecraft.net/texture/abc"),
        "https://textures.minecraft.net/texture/abc"
    );
    for raw in [
        "http://textures.minecraft.net.evil.test/texture/a",
        "https://user@textures.minecraft.net/texture/a",
        "https://textures.minecraft.net:444/texture/a",
        "https://other.test/texture/a",
        "file:///texture/a",
        "",
    ] {
        assert!(skin_url(raw).is_empty());
    }
}
fn device() -> Value {
    json!({"device_code":"private-device-fixture","user_code":"ABCD-EFGH",
        "verification_uri":"https://www.microsoft.com/link","expires_in":900,"interval":5})
}
fn pending() -> PendingLogin {
    let mut p = PendingLogin::from_response(CLIENT.into(), device(), Instant::now()).unwrap();
    p.interval = Duration::from_millis(2);
    p.deadline = Instant::now() + Duration::from_secs(15);
    p
}
async fn mock(
    responses: Vec<(u16, Value)>,
) -> (String, tokio::task::JoinHandle<Vec<(Instant, String)>>) {
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let address = format!("http://{}/token", listener.local_addr().unwrap());
    let task = tokio::spawn(async move {
        let mut requests = Vec::new();
        for (status, body) in responses {
            let (mut socket, _) = listener.accept().await.unwrap();
            let mut bytes = Vec::new();
            loop {
                let mut chunk = [0; 2048];
                let n = socket.read(&mut chunk).await.unwrap();
                assert!(n > 0 && bytes.len() < 16384);
                bytes.extend_from_slice(&chunk[..n]);
                if let Some(end) = bytes.windows(4).position(|b| b == b"\r\n\r\n") {
                    let headers = String::from_utf8_lossy(&bytes[..end]).to_lowercase();
                    let length: usize = headers
                        .lines()
                        .find_map(|line| {
                            line.strip_prefix("content-length:")
                                .map(|s| s.trim().parse().unwrap())
                        })
                        .unwrap();
                    if bytes.len() >= end + 4 + length {
                        break;
                    }
                }
            }
            requests.push((Instant::now(), String::from_utf8(bytes).unwrap()));
            let body = body.to_string();
            let reply = format!("HTTP/1.1 {status} Test\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}", body.len());
            socket.write_all(reply.as_bytes()).await.unwrap();
        }
        requests
    });
    (address, task)
}

#[tokio::test]
async fn device_expiry_is_from_request_time_and_private_code_stays_native() {
    let start = Instant::now() - Duration::from_secs(20);
    let mut data = device();
    data["expires_in"] = json!(60);
    data["interval"] = json!(1);
    let p = PendingLogin::from_response(CLIENT.into(), data, start).unwrap();
    assert_eq!(p.deadline, start + Duration::from_secs(60));
    assert_eq!(p.interval, Duration::from_secs(5));
    let public = p.public_view();
    assert!(public["expiresIn"].as_u64().unwrap() <= 40);
    assert!(!public.to_string().contains("private-device-fixture"));
    assert!(public.get("deviceCode").is_none());
    for expires in [0, 3601] {
        let mut data = device();
        data["expires_in"] = json!(expires);
        assert!(PendingLogin::from_response(CLIENT.into(), data, Instant::now()).is_err());
    }
}
#[test]
fn verification_address_rejects_spoofed_hosts_credentials_and_ports() {
    for url in [
        "http://microsoft.com/link",
        "https://microsoft.com.evil.test/link",
        "https://microsoft.com@evil.test/",
        "https://evil.test@microsoft.com/link",
        "https://microsoft.com:444/link",
        "file:///C:/anything",
    ] {
        assert!(verification_url(url).is_err(), "Accepted {url}");
    }
    for url in [
        "https://microsoft.com/link",
        "https://www.microsoft.com/devicelogin",
        "https://login.microsoftonline.com/consumers/oauth2/deviceauth",
    ] {
        assert!(verification_url(url).is_ok());
    }
}
#[test]
fn provider_errors_never_echo_error_descriptions() {
    let secret = "private-refresh-fixture";
    for code in [
        "invalid_client",
        "authorization_declined",
        "expired_token",
        "bad_verification_code",
        "invalid_grant",
        "unexpected",
    ] {
        let body = json!({"error":code,"error_description":secret,"access_token":secret});
        assert!(!oauth_error(&body, 400).contains(secret));
        assert!(token_reply(reqwest::StatusCode::BAD_REQUEST, body).is_err());
    }
    assert!(
        service_error("Xbox authorization", 401, &json!({"XErr":2148916233_u64}))
            .starts_with("XBOX_PROFILE_REQUIRED:")
    );
    assert!(
        service_error("Xbox authorization", 401, &json!({"XErr":2148916238_u64}))
            .starts_with("XBOX_FAMILY_REQUIRED:")
    );
    assert!(service_error("Minecraft authentication", 403, &json!({}))
        .starts_with("MINECRAFT_APP_NOT_APPROVED:"));
    assert!(token_reply(reqwest::StatusCode::OK, json!({"access_token":"x"})).is_err());
}
#[tokio::test]
async fn pending_then_authorized_posts_the_same_client_and_private_device_code() {
    let (url, server) = mock(vec![
        (400, json!({"error":"authorization_pending"})),
        (
            200,
            json!({"access_token":"access-fixture","refresh_token":"refresh-fixture"}),
        ),
    ])
    .await;
    let result = poll_token(&reqwest::Client::new(), &pending(), &url)
        .await
        .unwrap();
    assert_eq!(result, ("access-fixture".into(), "refresh-fixture".into()));
    let requests = server.await.unwrap();
    assert_eq!(requests.len(), 2);
    for (_, request) in requests {
        assert!(request.starts_with("POST /token HTTP/1.1"));
        assert!(request.contains(&format!("client_id={CLIENT}")));
        assert!(request.contains("device_code=private-device-fixture"));
        assert!(
            request.contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code")
        );
        assert!(!request.contains("client_secret"));
    }
}
#[tokio::test]
async fn slowdown_increases_poll_interval_by_at_least_five_seconds() {
    let (url, server) = mock(vec![
        (400, json!({"error":"slow_down"})),
        (200, json!({"access_token":"a","refresh_token":"r"})),
    ])
    .await;
    tokio::time::timeout(
        Duration::from_secs(12),
        poll_token(&reqwest::Client::new(), &pending(), &url),
    )
    .await
    .unwrap()
    .unwrap();
    let requests = server.await.unwrap();
    assert!(requests[1].0.duration_since(requests[0].0) >= Duration::from_secs(5));
}
#[tokio::test]
async fn rejected_authorization_stops_without_retrying() {
    let (url, server) = mock(vec![(400, json!({"error":"authorization_declined"}))]).await;
    let result = poll_token(&reqwest::Client::new(), &pending(), &url).await;
    assert!(result.unwrap_err().starts_with("LOGIN_DECLINED:"));
    assert_eq!(server.await.unwrap().len(), 1);
}
#[tokio::test]
async fn cancelled_before_poll_makes_no_network_request() {
    let p = pending();
    p.cancelled.send_replace(true);
    assert_eq!(
        poll_token(&reqwest::Client::new(), &p, "http://127.0.0.1:1/token")
            .await
            .unwrap_err(),
        CANCELLED
    );
}
#[tokio::test]
async fn cancellation_interrupts_an_inflight_http_request() {
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let url = format!("http://{}/token", listener.local_addr().unwrap());
    let p = Arc::new(pending());
    let worker = p.clone();
    let task =
        tokio::spawn(async move { poll_token(&reqwest::Client::new(), &worker, &url).await });
    let (_socket, _) = listener.accept().await.unwrap();
    p.cancelled.send_replace(true);
    assert_eq!(
        tokio::time::timeout(Duration::from_secs(1), task)
            .await
            .unwrap()
            .unwrap()
            .unwrap_err(),
        CANCELLED
    );
}
#[tokio::test]
async fn expiry_interrupts_an_inflight_http_request() {
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let url = format!("http://{}/token", listener.local_addr().unwrap());
    let mut p = pending();
    p.deadline = Instant::now() + Duration::from_millis(100);
    let task = tokio::spawn(async move { poll_token(&reqwest::Client::new(), &p, &url).await });
    let (_socket, _) = listener.accept().await.unwrap();
    assert_eq!(
        tokio::time::timeout(Duration::from_secs(1), task)
            .await
            .unwrap()
            .unwrap()
            .unwrap_err(),
        EXPIRED
    );
}
#[tokio::test]
async fn stale_cancellation_cannot_remove_a_new_login() {
    let auth = Auth::default();
    let first = Arc::new(pending());
    *auth.pending.lock().unwrap() = Some(first.clone());
    let lease = LoginLease {
        auth: &auth,
        session_id: first.session_id.clone(),
    };
    assert!(cancel(&auth, &first.session_id).unwrap());
    assert!(*first.cancelled.borrow());
    let next = Arc::new(pending());
    *auth.pending.lock().unwrap() = Some(next.clone());
    drop(lease);
    assert!(auth.session(&next.session_id).is_ok());
    assert!(!*next.cancelled.borrow());
    assert!(!cancel(&auth, &first.session_id).unwrap());
}
#[tokio::test]
async fn missing_registration_fails_locally_without_creating_a_login() {
    let temp = tempfile::tempdir().unwrap();
    let store = Store::new(temp.path().into()).unwrap();
    atomic(
        &store.root.join("config/launcher.json"),
        br#"{"clientId":"00000000-0000-0000-0000-000000000000"}"#,
    )
    .unwrap();
    let auth = Auth::default();
    assert!(start(&store, &auth)
        .await
        .unwrap_err()
        .starts_with("MICROSOFT_SETUP_REQUIRED:"));
    assert!(auth.pending.lock().unwrap().is_none());
    assert!(accounts(&store).unwrap()["items"]
        .as_array()
        .unwrap()
        .is_empty());
}
#[tokio::test]
async fn changed_application_requires_reauthentication_before_reading_credentials() {
    let temp = tempfile::tempdir().unwrap();
    let store = Store::new(temp.path().into()).unwrap();
    atomic(
        &store.root.join("config/launcher.json"),
        format!(r#"{{"clientId":"{CLIENT}"}}"#).as_bytes(),
    )
    .unwrap();
    save_metadata(&store, &json!({"schemaVersion":1,"selected":"fixture-account","items":[{"id":"fixture-account","clientId":"other-app"}]})).unwrap();
    let err = match identity(&store).await {
        Err(e) => e,
        Ok(_) => panic!("Accepted a token registered for another application"),
    };
    assert!(err.starts_with("MICROSOFT_REAUTH_REQUIRED:"));
}

#[tokio::test]
#[ignore = "Explicit online check against Votex's own registered app; no user sign-in"]
async fn live_registered_device_authorization() {
    let temp = tempfile::tempdir().unwrap();
    let store = Store::new(temp.path().into()).unwrap();
    atomic(
        &store.root.join("config/launcher.json"),
        br#"{"clientId":""}"#,
    )
    .unwrap();
    let auth = Auth::default();
    let view = start(&store, &auth)
        .await
        .expect("Microsoft device authorization did not succeed");
    assert!(!view["userCode"].as_str().unwrap().is_empty());
    assert!(view["expiresIn"].as_u64().unwrap() > 0);
    verification_url(view["verificationUri"].as_str().unwrap()).unwrap();
    assert!(view.get("device_code").is_none());
    assert!(cancel(&auth, view["sessionId"].as_str().unwrap()).unwrap());
    assert!(accounts(&store).unwrap()["items"]
        .as_array()
        .unwrap()
        .is_empty());
    println!("MICROSOFT_DEVICE_AUTHORIZATION_PASS: registered publisher ID accepted; temporary attempt cancelled without account authorization");
}
