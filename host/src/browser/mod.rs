//! Per-bot headless browsers: a bot with **Use a browser** on gets its own Chrome or Firefox
//! profile under `~/.codync/browser/<bot>/`, the `browser` MCP tools drive it, and the
//! desktop's Computer panel can watch, take over and hand it back.

mod chrome;
mod firefox;
mod session;
pub mod tools;

pub use session::BotBrowser;

use crate::LockExt;
use crate::hub::Hub;
use anyhow::{Result, anyhow};
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};

/// One tab: its engine id, plus what the strip shows.
#[derive(Clone, Debug, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TabInfo {
    pub id: String,
    pub title: String,
    pub url: String,
}

/// A captured frame: JPEG from Chrome, PNG from Firefox.
pub struct Screenshot {
    pub data: Vec<u8>,
    pub format: &'static str,
}

/// Every bot's browser session, started on first use and kept until stopped or the host exits.
#[derive(Default)]
pub struct Browser {
    sessions: Mutex<HashMap<String, Arc<BotBrowser>>>,
}

impl Browser {
    /// The bot's browser, started and connected on first use. `cfg` gates it behind
    /// **Use a browser**.
    pub async fn session(&self, hub: &Arc<Hub>, bot: &str) -> Result<Arc<BotBrowser>> {
        if let Some(session) = self.sessions.locked().get(bot).cloned() {
            if session.alive() {
                return Ok(session);
            }
            self.sessions.locked().remove(bot);
        }
        let session = Arc::new(BotBrowser::start(hub, bot).await?);
        self.sessions.locked().insert(bot.to_owned(), session.clone());
        Ok(session)
    }

    pub fn stop(&self, bot: &str) {
        if let Some(session) = self.sessions.locked().remove(bot) {
            session.stop();
        }
    }

    /// The bot's session when one is already running (state calls never start a browser).
    pub fn running(&self, bot: &str) -> Option<Arc<BotBrowser>> {
        self.sessions.locked().get(bot).cloned()
    }

    /// Called on shutdown so no Chrome/Firefox outlives the host.
    pub fn stop_all(&self) {
        for (_, session) in self.sessions.locked().drain() {
            session.stop();
        }
    }
}

/// The browser's profile folder for one bot.
pub fn profile_dir(bot: &str) -> PathBuf {
    crate::service::data_dir().join("browser").join(bot)
}

/// A URL the user (or a bot) asked for, with a scheme when they typed none.
pub fn normalize_url(raw: &str) -> Result<String> {
    let raw = raw.trim();
    if raw.is_empty() {
        return Err(anyhow!("a url is required"));
    }
    if raw.contains("://") {
        return Ok(raw.to_owned());
    }
    if raw.starts_with("about:") || raw.starts_with("file:") || raw.starts_with("data:") {
        return Ok(raw.to_owned());
    }
    let host = raw.split(['/', '?', '#']).next().unwrap_or_default();
    let local = host.starts_with("localhost")
        || host.starts_with("127.")
        || host.starts_with("0.0.0.0")
        || host.to_ascii_lowercase().ends_with(".local");
    Ok(format!("{}://{raw}", if local { "http" } else { "https" }))
}

/// The engine a bot's browser uses: its own choice when it made one, else the automatic pick.
/// `CODYNC_BROWSER_ENGINE=chrome|firefox` overrides both (for testing or a box with both).
pub fn engine_for(preference: &str) -> Result<&'static str> {
    if let Ok(forced) = std::env::var("CODYNC_BROWSER_ENGINE")
        && !forced.is_empty()
    {
        return installed(&forced);
    }
    if !preference.is_empty() {
        return installed(preference);
    }
    if chrome::binary().is_some() {
        return Ok("chrome");
    }
    if firefox::binary().is_some() {
        return Ok("firefox");
    }
    Err(anyhow!(
        "No Chrome or Firefox found on this computer. Install Google Chrome (or Chromium, or Firefox) and try again."
    ))
}

/// The automatic pick, for the panel to show before any session exists.
pub fn available_engine() -> Result<&'static str> {
    engine_for("")
}

/// One engine by name, ensuring this computer has it.
fn installed(engine: &str) -> Result<&'static str> {
    match engine {
        "chrome" if chrome::binary().is_some() => Ok("chrome"),
        "firefox" if firefox::binary().is_some() => Ok("firefox"),
        "chrome" => Err(anyhow!("Chrome isn't installed on this computer.")),
        "firefox" => Err(anyhow!("Firefox isn't installed on this computer.")),
        other => Err(anyhow!("Unknown browser engine `{other}` (chrome or firefox).")),
    }
}

/// Picks the engine for a new session: Chrome when present (CDP covers every action), else Firefox.
pub(super) async fn launch_engine(profile_root: &Path, engine: &str) -> Result<(Engine, &'static str)> {
    match engine {
        "chrome" => Ok((Engine::Chrome(chrome::Chrome::launch(&profile_root.join("chrome")).await?), "chrome")),
        "firefox" => Ok((Engine::Firefox(firefox::Firefox::launch(&profile_root.join("firefox")).await?), "firefox")),
        other => Err(anyhow!("unknown browser engine `{other}`")),
    }
}

/// The running browser: one engine, told apart only by which protocol it speaks.
pub(super) enum Engine {
    Chrome(chrome::Chrome),
    Firefox(firefox::Firefox),
}

impl Engine {
    pub(super) fn alive(&mut self) -> bool {
        match self {
            Self::Chrome(c) => c.alive(),
            Self::Firefox(f) => f.alive(),
        }
    }

    pub(super) fn stop(&mut self) {
        match self {
            Self::Chrome(c) => c.stop(),
            Self::Firefox(f) => f.stop(),
        }
    }

    pub(super) async fn list(&self) -> Result<Vec<TabInfo>> {
        match self {
            Self::Chrome(c) => c.list().await,
            Self::Firefox(f) => f.list().await,
        }
    }

    pub(super) async fn create(&self, url: &str) -> Result<TabInfo> {
        match self {
            Self::Chrome(c) => c.create(url).await,
            Self::Firefox(f) => f.create(url).await,
        }
    }

    pub(super) async fn close(&self, id: &str) -> Result<()> {
        match self {
            Self::Chrome(c) => c.close(id).await,
            Self::Firefox(f) => f.close(id).await,
        }
    }

    pub(super) async fn navigate(&self, id: &str, url: &str) -> Result<()> {
        match self {
            Self::Chrome(c) => c.navigate(id, url).await,
            Self::Firefox(f) => f.navigate(id, url).await,
        }
    }

    pub(super) async fn reload(&self, id: &str) -> Result<()> {
        match self {
            Self::Chrome(c) => c.reload(id).await,
            Self::Firefox(f) => f.reload(id).await,
        }
    }

    pub(super) async fn screenshot(&self, id: &str) -> Result<Screenshot> {
        match self {
            Self::Chrome(c) => c.screenshot(id).await,
            Self::Firefox(f) => f.screenshot(id).await,
        }
    }

    pub(super) async fn click(&self, id: &str, x: f64, y: f64) -> Result<()> {
        match self {
            Self::Chrome(c) => c.click(id, x, y).await,
            Self::Firefox(f) => f.click(id, x, y).await,
        }
    }

    pub(super) async fn type_text(&self, id: &str, text: &str) -> Result<()> {
        match self {
            Self::Chrome(c) => c.type_text(id, text).await,
            Self::Firefox(f) => f.type_text(id, text).await,
        }
    }

    pub(super) async fn press(&self, id: &str, key: &str) -> Result<()> {
        match self {
            Self::Chrome(c) => c.press(id, key).await,
            Self::Firefox(f) => f.press(id, key).await,
        }
    }

    pub(super) async fn scroll(&self, id: &str, x: f64, y: f64, delta_y: f64) -> Result<()> {
        match self {
            Self::Chrome(c) => c.scroll(id, x, y, delta_y).await,
            Self::Firefox(f) => f.scroll(id, x, y, delta_y).await,
        }
    }

    pub(super) async fn evaluate(&self, id: &str, js: &str) -> Result<serde_json::Value> {
        match self {
            Self::Chrome(c) => c.evaluate(id, js).await,
            Self::Firefox(f) => f.evaluate(id, js).await,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn urls_get_the_scheme_they_deserve() {
        assert_eq!(normalize_url("example.com/a?b=1").unwrap(), "https://example.com/a?b=1");
        assert_eq!(normalize_url("localhost:3000").unwrap(), "http://localhost:3000");
        assert_eq!(normalize_url("127.0.0.1:8080/x").unwrap(), "http://127.0.0.1:8080/x");
        assert_eq!(normalize_url("http://a.b").unwrap(), "http://a.b");
        assert_eq!(normalize_url("about:blank").unwrap(), "about:blank");
        assert!(normalize_url("  ").is_err());
    }
}
