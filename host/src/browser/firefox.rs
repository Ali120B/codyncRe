//! Headless Firefox over `WebDriver BiDi`: launched with its own profile, driven over one
//! WebSocket (one session per connection; tabs are browsing contexts).

use super::{Screenshot, TabInfo};
use crate::LockExt;
use anyhow::{Context, Result, anyhow, bail};
use futures::{SinkExt, StreamExt};
use serde_json::{Value, json};
use std::collections::HashMap;
use std::net::TcpListener;
use std::path::{Path, PathBuf};
use std::process::Stdio;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::Duration;
use tokio::sync::{mpsc, oneshot};
use tokio_tungstenite::tungstenite::Message;

/// Same fixed viewport as Chrome, so screenshots and input share one coordinate space.
pub const VIEWPORT: (u32, u32) = (1280, 800);
const CALL_TIMEOUT: Duration = Duration::from_secs(30);
const LAUNCH_TIMEOUT: Duration = Duration::from_secs(30);

/// Firefox on this computer.
pub fn binary() -> Option<PathBuf> {
    crate::agent::backends::which("firefox")
}

/// Headless Firefox with its own profile under `profile`.
pub struct Firefox {
    child: tokio::process::Child,
    bidi: Bidi,
}

impl Firefox {
    pub async fn launch(profile: &Path) -> Result<Self> {
        let bin = binary().ok_or_else(|| anyhow!("Firefox isn't installed on this computer"))?;
        tokio::fs::create_dir_all(profile).await?;
        // Firefox has no port=0, so take one now and hand it over.
        let port = free_port()?;
        let child = tokio::process::Command::new(&bin)
            .arg("--headless")
            .arg("--no-remote")
            .arg("--new-instance")
            .arg("--profile")
            .arg(profile)
            .arg("--remote-debugging-port")
            .arg(port.to_string())
            .arg("about:blank")
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .kill_on_drop(true)
            .spawn()
            .with_context(|| format!("starting {}", bin.display()))?;
        let mut child = child;
        let bidi = connect_bidi(port, &mut child).await?;
        let firefox = Self { child, bidi };
        firefox.set_viewport(None).await.ok();
        Ok(firefox)
    }

    pub fn alive(&mut self) -> bool {
        matches!(self.child.try_wait(), Ok(None))
    }

    pub fn stop(&mut self) {
        let _ = self.child.start_kill();
    }

    pub async fn list(&self) -> Result<Vec<TabInfo>> {
        let res = self.bidi.send("browsingContext.getTree", json!({})).await?;
        let contexts = res["contexts"].as_array().cloned().unwrap_or_default();
        let mut tabs = Vec::with_capacity(contexts.len());
        for context in contexts {
            let id = context["context"].as_str().unwrap_or_default().to_owned();
            let url = context["url"].as_str().unwrap_or_default().to_owned();
            let title = self.title(&id).await.unwrap_or_default();
            tabs.push(TabInfo { id, title, url });
        }
        Ok(tabs)
    }

    async fn title(&self, id: &str) -> Result<String> {
        Ok(self.evaluate(id, "document.title").await?.as_str().unwrap_or_default().to_owned())
    }

    pub async fn create(&self, url: &str) -> Result<TabInfo> {
        let res = self.bidi.send("browsingContext.create", json!({"type": "tab"})).await?;
        let id = res["context"].as_str().ok_or_else(|| anyhow!("Firefox didn't open a tab"))?.to_owned();
        self.set_viewport(Some(&id)).await.ok();
        if url != "about:blank" {
            self.navigate(&id, url).await?;
        }
        Ok(TabInfo { id, title: String::new(), url: url.to_owned() })
    }

    pub async fn close(&self, id: &str) -> Result<()> {
        self.bidi.send("browsingContext.close", json!({"context": id})).await?;
        Ok(())
    }

    async fn set_viewport(&self, id: Option<&str>) -> Result<()> {
        let ids: Vec<String> = match id {
            Some(id) => vec![id.to_owned()],
            None => self.list().await?.into_iter().map(|t| t.id).collect(),
        };
        for id in ids {
            self.bidi
                .send(
                    "browsingContext.setViewport",
                    json!({"context": id, "viewport": {"width": VIEWPORT.0, "height": VIEWPORT.1}}),
                )
                .await?;
        }
        Ok(())
    }

    pub async fn navigate(&self, id: &str, url: &str) -> Result<()> {
        self.bidi.send("browsingContext.navigate", json!({"context": id, "url": url, "wait": "none"})).await?;
        Ok(())
    }

    pub async fn reload(&self, id: &str) -> Result<()> {
        self.bidi.send("browsingContext.reload", json!({"context": id})).await?;
        Ok(())
    }

    pub async fn screenshot(&self, id: &str) -> Result<Screenshot> {
        // Firefox only captures the active tab.
        let _ = self.bidi.send("browsingContext.activate", json!({"context": id})).await;
        let res = self.bidi.send("browsingContext.captureScreenshot", json!({"context": id})).await?;
        let data = base64::Engine::decode(
            &base64::engine::general_purpose::STANDARD,
            res["data"].as_str().ok_or_else(|| anyhow!("Firefox sent no frame"))?,
        )?;
        Ok(Screenshot { data, format: "png" })
    }

    pub async fn click(&self, id: &str, x: f64, y: f64) -> Result<()> {
        self.perform(id, pointer_actions(x, y)).await
    }

    /// Types `text` into the focused element, one key event per character (`\n` = Enter).
    pub async fn type_text(&self, id: &str, text: &str) -> Result<()> {
        let mut actions = Vec::new();
        for ch in text.chars() {
            let value = if ch == '\n' { "Enter".to_owned() } else { ch.to_string() };
            actions.push(json!({"type": "keyDown", "value": value}));
            actions.push(json!({"type": "keyUp", "value": value}));
            if actions.len() >= 512 {
                self.perform(id, key_actions(&actions)).await?;
                actions.clear();
            }
        }
        if !actions.is_empty() {
            self.perform(id, key_actions(&actions)).await?;
        }
        Ok(())
    }

    pub async fn press(&self, id: &str, key: &str) -> Result<()> {
        let action = match key {
            " " => "Space".to_owned(),
            other => other.to_owned(),
        };
        self.perform(
            id,
            key_actions(&[json!({"type": "keyDown", "value": action}), json!({"type": "keyUp", "value": action})]),
        )
        .await
    }

    pub async fn scroll(&self, id: &str, x: f64, y: f64, delta_y: f64) -> Result<()> {
        let wheel = json!([{
            "type": "wheel", "id": "wheel",
            "actions": [{"type": "scroll", "x": x, "y": y, "deltaX": 0, "deltaY": delta_y}],
        }]);
        if self.perform(id, wheel).await.is_err() {
            // Older builds without wheel actions: page-level scroll is better than nothing.
            self.evaluate(id, &format!("window.scrollBy(0, {delta_y})")).await?;
        }
        Ok(())
    }

    pub async fn evaluate(&self, id: &str, js: &str) -> Result<Value> {
        let res = self
            .bidi
            .send(
                "script.evaluate",
                json!({"expression": js, "target": {"context": id}, "awaitPromise": true, "resultOwnership": "root"}),
            )
            .await?;
        if res["type"] == "exception" || res["exceptionDetails"].is_object() {
            bail!("{}", res["exceptionDetails"]["text"].as_str().unwrap_or("the page script failed"));
        }
        Ok(res["result"]["value"].clone())
    }

    async fn perform(&self, id: &str, actions: Value) -> Result<()> {
        let res = self.bidi.send("input.performActions", json!({"context": id, "actions": actions})).await;
        res.map(|_| ())
    }
}

fn pointer_actions(x: f64, y: f64) -> Value {
    json!([{
        "type": "pointer", "id": "mouse", "parameters": {"pointerType": "mouse"},
        "actions": [
            {"type": "pointerMove", "x": x, "y": y},
            {"type": "pointerDown", "button": 0},
            {"type": "pointerUp", "button": 0},
        ],
    }])
}

fn key_actions(actions: &[Value]) -> Value {
    json!([{"type": "key", "id": "keyboard", "actions": actions}])
}

fn free_port() -> Result<u16> {
    let listener = TcpListener::bind("127.0.0.1:0")?;
    Ok(listener.local_addr()?.port())
}

/// Waits until Firefox accepts `BiDi` connections, then creates the session.
async fn connect_bidi(port: u16, child: &mut tokio::process::Child) -> Result<Bidi> {
    let deadline = tokio::time::Instant::now() + LAUNCH_TIMEOUT;
    loop {
        if tokio::net::TcpStream::connect(("127.0.0.1", port)).await.is_ok() {
            return Bidi::connect(port).await;
        }
        if let Ok(Some(status)) = child.try_wait() {
            bail!("Firefox exited while starting ({status})");
        }
        if tokio::time::Instant::now() > deadline {
            bail!("Firefox didn't start within {} s", LAUNCH_TIMEOUT.as_secs());
        }
        tokio::time::sleep(Duration::from_millis(150)).await;
    }
}

/// Requests by id waiting for their answer.
type Pending = Arc<Mutex<HashMap<u64, oneshot::Sender<Result<Value, String>>>>>;

/// A `BiDi` WebSocket: one session per connection, requests awaited by id, events dropped.
struct Bidi {
    tx: mpsc::UnboundedSender<Message>,
    pending: Pending,
    next: AtomicU64,
}

impl Bidi {
    async fn connect(port: u16) -> Result<Self> {
        let (ws, _) = tokio_tungstenite::connect_async(format!("ws://127.0.0.1:{port}/session"))
            .await
            .context("connecting to Firefox")?;
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
                    let result = if msg["type"] == "error" {
                        Err(msg["message"].as_str().unwrap_or("Firefox error").to_owned())
                    } else {
                        Ok(msg["result"].clone())
                    };
                    let _ = done.send(result);
                }
            }
            for (_, done) in reader.locked().drain() {
                let _ = done.send(Err("Firefox's connection closed".into()));
            }
        });
        let bidi = Self { tx, pending, next: AtomicU64::new(0) };
        // A fresh Firefox needs one session before any other command.
        bidi.send("session.new", json!({"capabilities": {}})).await.context("opening a Firefox session")?;
        Ok(bidi)
    }

    async fn send(&self, method: &str, params: Value) -> Result<Value> {
        let id = self.next.fetch_add(1, Ordering::Relaxed) + 1;
        let msg = json!({"id": id, "method": method, "params": params});
        let (tx, rx) = oneshot::channel();
        self.pending.locked().insert(id, tx);
        self.tx.send(Message::text(msg.to_string())).map_err(|_| anyhow!("Firefox's connection closed"))?;
        match tokio::time::timeout(CALL_TIMEOUT, rx).await {
            Ok(Ok(Ok(value))) => Ok(value),
            Ok(Ok(Err(message))) => Err(anyhow!("{message}")),
            Ok(Err(_)) => Err(anyhow!("Firefox's connection closed")),
            Err(_) => {
                self.pending.locked().remove(&id);
                Err(anyhow!("Firefox didn't answer {method} within {} s", CALL_TIMEOUT.as_secs()))
            }
        }
    }
}
