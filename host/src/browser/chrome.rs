//! Headless Chrome over the `DevTools` protocol: launched with its own profile, driven over one
//! WebSocket (browser-level; each tab is an attached target).

use super::{Screenshot, TabInfo};
use crate::LockExt;
use anyhow::{Context, Result, anyhow, bail};
use futures::{SinkExt, StreamExt};
use serde_json::{Value, json};
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::process::Stdio;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Duration;
use tokio::sync::{mpsc, oneshot};
use tokio_tungstenite::tungstenite::Message;

/// Every page renders at this viewport, so screenshots and input share one coordinate space.
pub const VIEWPORT: (u32, u32) = (1280, 800);
const CALL_TIMEOUT: Duration = Duration::from_secs(20);
const LAUNCH_TIMEOUT: Duration = Duration::from_secs(20);

/// Chrome on this computer (`google-chrome-stable`, `google-chrome`, or Chromium).
pub fn binary() -> Option<PathBuf> {
    ["google-chrome-stable", "google-chrome", "chromium", "chromium-browser", "chrome"]
        .into_iter()
        .find_map(crate::agent::backends::which)
}

/// Headless Chrome with its own profile under `profile`.
pub struct Chrome {
    child: tokio::process::Child,
    cdp: Cdp,
    /// targetId -> attached sessionId.
    attachments: Mutex<HashMap<String, String>>,
}

impl Chrome {
    pub async fn launch(profile: &Path) -> Result<Self> {
        let bin = binary().ok_or_else(|| anyhow!("Chrome isn't installed on this computer"))?;
        tokio::fs::create_dir_all(profile).await?;
        let active_port = profile.join("DevToolsActivePort");
        let _ = std::fs::remove_file(&active_port);
        let child = tokio::process::Command::new(&bin)
            .arg("--headless=new")
            .arg("--remote-debugging-port=0")
            .arg("--remote-allow-origins=*")
            .arg(format!("--user-data-dir={}", profile.display()))
            .arg(format!("--window-size={},{}", VIEWPORT.0, VIEWPORT.1))
            .args([
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-dev-shm-usage",
                "--disable-background-networking",
                "--disable-features=Translate,MediaRouter",
                "--disable-sync",
                "--metrics-recording-only",
                "--mute-audio",
                "--hide-scrollbars",
                "--password-store=basic",
                "about:blank",
            ])
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .kill_on_drop(true)
            .spawn()
            .with_context(|| format!("starting {}", bin.display()))?;
        let mut chrome = Self { child, cdp: Cdp::detached(), attachments: Mutex::new(HashMap::new()) };
        chrome.cdp = Cdp::connect(&wait_for_ws(&active_port, &mut chrome.child).await?).await?;
        Ok(chrome)
    }

    pub fn alive(&mut self) -> bool {
        matches!(self.child.try_wait(), Ok(None))
    }

    pub fn stop(&mut self) {
        let _ = self.child.start_kill();
    }

    pub async fn list(&self) -> Result<Vec<TabInfo>> {
        let res = self.cdp.send("Target.getTargets", json!({}), None).await?;
        Ok(res["targetInfos"]
            .as_array()
            .into_iter()
            .flatten()
            .filter(|t| t["type"] == "page" && !t["url"].as_str().unwrap_or_default().starts_with("devtools://"))
            .map(|t| TabInfo {
                id: t["targetId"].as_str().unwrap_or_default().to_owned(),
                title: t["title"].as_str().unwrap_or_default().to_owned(),
                url: t["url"].as_str().unwrap_or_default().to_owned(),
            })
            .collect())
    }

    pub async fn create(&self, url: &str) -> Result<TabInfo> {
        let res = self.cdp.send("Target.createTarget", json!({"url": url}), None).await?;
        let id = res["targetId"].as_str().ok_or_else(|| anyhow!("Chrome didn't open a tab"))?.to_owned();
        self.attach(&id).await?;
        self.list().await?.into_iter().find(|t| t.id == id).ok_or_else(|| anyhow!("the new tab disappeared"))
    }

    pub async fn close(&self, id: &str) -> Result<()> {
        self.cdp.send("Target.closeTarget", json!({"targetId": id}), None).await?;
        self.attachments.locked().remove(id);
        Ok(())
    }

    async fn attach(&self, target: &str) -> Result<()> {
        if self.attachments.locked().contains_key(target) {
            return Ok(());
        }
        let res = self.cdp.send("Target.attachToTarget", json!({"targetId": target, "flatten": true}), None).await?;
        let session = res["sessionId"].as_str().ok_or_else(|| anyhow!("Chrome refused to attach"))?.to_owned();
        self.cdp.send("Page.enable", json!({}), Some(&session)).await?;
        // One fixed viewport: screenshot pixels == the coordinates input uses.
        self.cdp
            .send(
                "Emulation.setDeviceMetricsOverride",
                json!({"width": VIEWPORT.0, "height": VIEWPORT.1, "deviceScaleFactor": 1, "mobile": false}),
                Some(&session),
            )
            .await?;
        self.attachments.locked().insert(target.to_owned(), session);
        Ok(())
    }

    /// The tab's attached session, attaching first when needed.
    async fn session(&self, id: &str) -> Result<String> {
        if let Some(s) = self.attachments.locked().get(id) {
            return Ok(s.clone());
        }
        self.attach(id).await?;
        self.attachments.locked().get(id).cloned().ok_or_else(|| anyhow!("unknown tab"))
    }

    pub async fn navigate(&self, id: &str, url: &str) -> Result<()> {
        let session = self.session(id).await?;
        self.cdp.send("Page.navigate", json!({"url": url}), Some(&session)).await?;
        Ok(())
    }

    pub async fn reload(&self, id: &str) -> Result<()> {
        let session = self.session(id).await?;
        self.cdp.send("Page.reload", json!({}), Some(&session)).await?;
        Ok(())
    }

    pub async fn screenshot(&self, id: &str) -> Result<Screenshot> {
        let session = self.session(id).await?;
        let res =
            self.cdp.send("Page.captureScreenshot", json!({"format": "jpeg", "quality": 70}), Some(&session)).await?;
        let data = base64::Engine::decode(
            &base64::engine::general_purpose::STANDARD,
            res["data"].as_str().ok_or_else(|| anyhow!("Chrome sent no frame"))?,
        )?;
        Ok(Screenshot { data, format: "jpeg" })
    }

    pub async fn click(&self, id: &str, x: f64, y: f64) -> Result<()> {
        let session = self.session(id).await?;
        self.cdp
            .send("Input.dispatchMouseEvent", json!({"type": "mouseMoved", "x": x, "y": y}), Some(&session))
            .await?;
        for kind in ["mousePressed", "mouseReleased"] {
            self.cdp
                .send(
                    "Input.dispatchMouseEvent",
                    json!({"type": kind, "x": x, "y": y, "button": "left", "clickCount": 1}),
                    Some(&session),
                )
                .await?;
        }
        Ok(())
    }

    pub async fn type_text(&self, id: &str, text: &str) -> Result<()> {
        let session = self.session(id).await?;
        self.cdp.send("Input.insertText", json!({"text": text}), Some(&session)).await?;
        Ok(())
    }

    pub async fn press(&self, id: &str, key: &str) -> Result<()> {
        let session = self.session(id).await?;
        let (key, code, vk) = key_codes(key)?;
        for kind in ["keyDown", "keyUp"] {
            self.cdp
                .send(
                    "Input.dispatchKeyEvent",
                    json!({"type": kind, "key": key, "code": code, "windowsVirtualKeyCode": vk, "nativeVirtualKeyCode": vk}),
                    Some(&session),
                )
                .await?;
        }
        Ok(())
    }

    pub async fn scroll(&self, id: &str, x: f64, y: f64, delta_y: f64) -> Result<()> {
        let session = self.session(id).await?;
        self.cdp
            .send(
                "Input.dispatchMouseEvent",
                json!({"type": "mouseWheel", "x": x, "y": y, "deltaX": 0, "deltaY": delta_y}),
                Some(&session),
            )
            .await?;
        Ok(())
    }

    pub async fn evaluate(&self, id: &str, js: &str) -> Result<Value> {
        let session = self.session(id).await?;
        let res = self
            .cdp
            .send(
                "Runtime.evaluate",
                json!({"expression": js, "returnByValue": true, "awaitPromise": true}),
                Some(&session),
            )
            .await?;
        if let Some(error) = res["exceptionDetails"]["exception"]["description"].as_str() {
            bail!("{error}");
        }
        Ok(res["result"]["value"].clone())
    }
}

/// `Enter`, `Tab`, arrows and friends as Chrome's input wants them.
fn key_codes(key: &str) -> Result<(&'static str, &'static str, i64)> {
    Ok(match key {
        "Enter" => ("Enter", "Enter", 13),
        "Tab" => ("Tab", "Tab", 9),
        "Escape" => ("Escape", "Escape", 27),
        "Backspace" => ("Backspace", "Backspace", 8),
        "Delete" => ("Delete", "Delete", 46),
        "ArrowUp" => ("ArrowUp", "ArrowUp", 38),
        "ArrowDown" => ("ArrowDown", "ArrowDown", 40),
        "ArrowLeft" => ("ArrowLeft", "ArrowLeft", 37),
        "ArrowRight" => ("ArrowRight", "ArrowRight", 39),
        "PageUp" => ("PageUp", "PageUp", 33),
        "PageDown" => ("PageDown", "PageDown", 34),
        "Home" => ("Home", "Home", 36),
        "End" => ("End", "End", 35),
        " " => (" ", "Space", 32),
        other => bail!(
            "unsupported key `{other}` (Enter, Tab, Escape, Backspace, Delete, arrows, PageUp/Down, Home, End, space)"
        ),
    })
}

/// Waits for Chrome to write `DevToolsActivePort` (port on line 1, WebSocket path on line 2).
async fn wait_for_ws(active_port: &Path, child: &mut tokio::process::Child) -> Result<String> {
    let deadline = tokio::time::Instant::now() + LAUNCH_TIMEOUT;
    loop {
        if let Ok(text) = std::fs::read_to_string(active_port) {
            let mut lines = text.lines();
            if let (Some(port), Some(path)) = (lines.next(), lines.next()) {
                return Ok(format!("ws://127.0.0.1:{port}{path}"));
            }
        }
        if let Ok(Some(status)) = child.try_wait() {
            bail!("Chrome exited while starting ({status})");
        }
        if tokio::time::Instant::now() > deadline {
            bail!("Chrome didn't start within {} s", LAUNCH_TIMEOUT.as_secs());
        }
        tokio::time::sleep(Duration::from_millis(100)).await;
    }
}

/// Requests by id waiting for their answer.
type Pending = Arc<Mutex<HashMap<u64, oneshot::Sender<Result<Value, String>>>>>;

/// A `DevTools` WebSocket: requests are awaited by id, events are dropped.
struct Cdp {
    tx: mpsc::UnboundedSender<Message>,
    pending: Pending,
    next: AtomicU64,
}

impl Cdp {
    fn detached() -> Self {
        let (tx, _) = mpsc::unbounded_channel();
        Self { tx, pending: Arc::default(), next: AtomicU64::new(0) }
    }

    async fn connect(url: &str) -> Result<Self> {
        let (ws, _) = tokio_tungstenite::connect_async(url).await.context("connecting to Chrome")?;
        let (mut sink, mut stream) = ws.split();
        let (tx, mut rx) = mpsc::unbounded_channel::<Message>();
        tokio::spawn(async move {
            while let Some(msg) = rx.recv().await {
                if sink.send(msg).await.is_err() {
                    break;
                }
            }
        });
        let pending: Pending = Arc::default();
        let reader = pending.clone();
        tokio::spawn(async move {
            while let Some(Ok(Message::Text(text))) = stream.next().await {
                let Ok(msg) = serde_json::from_str::<Value>(&text) else { continue };
                if let Some(id) = msg["id"].as_u64()
                    && let Some(done) = reader.locked().remove(&id)
                {
                    let result = match msg.get("error") {
                        Some(error) => Err(error["message"].as_str().unwrap_or("Chrome error").to_owned()),
                        None => Ok(msg["result"].clone()),
                    };
                    let _ = done.send(result);
                }
            }
            // The socket closed: fail everything still waiting.
            for (_, done) in reader.locked().drain() {
                let _ = done.send(Err("Chrome's connection closed".into()));
            }
        });
        Ok(Self { tx, pending, next: AtomicU64::new(0) })
    }

    async fn send(&self, method: &str, params: Value, session: Option<&str>) -> Result<Value> {
        let id = self.next.fetch_add(1, Ordering::Relaxed) + 1;
        let mut msg = json!({"id": id, "method": method, "params": params});
        if let Some(session) = session {
            msg["sessionId"] = session.into();
        }
        let (tx, rx) = oneshot::channel();
        self.pending.locked().insert(id, tx);
        self.tx.send(Message::text(msg.to_string())).map_err(|_| anyhow!("Chrome's connection closed"))?;
        match tokio::time::timeout(CALL_TIMEOUT, rx).await {
            Ok(Ok(Ok(value))) => Ok(value),
            Ok(Ok(Err(message))) => Err(anyhow!("{message}")),
            Ok(Err(_)) => Err(anyhow!("Chrome's connection closed")),
            Err(_) => {
                self.pending.locked().remove(&id);
                Err(anyhow!("Chrome didn't answer {method} within {} s", CALL_TIMEOUT.as_secs()))
            }
        }
    }
}
