//! One bot's browser: its engine, its tabs, and who is allowed to drive — the bot by default,
//! the user while they have taken over (released automatically after 50 s of no input).

use super::{Engine, Screenshot, TabInfo, launch_engine, normalize_url, profile_dir};
use crate::LockExt;
use crate::hub::Hub;
use anyhow::{Context, Result, anyhow, bail};
use serde_json::{Value, json};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};
use tokio::sync::Mutex as AsyncMutex;

/// The user's takeover while watching the Browser tab.
pub const AUTO_RELEASE: Duration = Duration::from_secs(50);

/// One captured frame with the active tab's identity.
pub struct Frame {
    pub shot: Screenshot,
    pub tab: String,
    pub url: String,
    pub title: String,
}

struct Shared {
    /// The tab the bot drives (and the panel shows); `None` until the first tab exists.
    active: Option<String>,
    /// The user has taken over from the desktop.
    user_control: bool,
    user_last: Instant,
}

/// One bot's browser: start on first use, stop on request or when the host exits.
pub struct BotBrowser {
    kind: &'static str,
    running: AsyncMutex<Option<Engine>>,
    shared: Mutex<Shared>,
}

impl BotBrowser {
    /// Starts the bot's browser (its profile is `~/.codync/browser/<bot>/<engine>`).
    pub async fn start(hub: &Arc<Hub>, bot: &str) -> Result<Self> {
        let row = hub.store.bot(bot)?.filter(|r| !r.deleted).ok_or_else(|| anyhow!("unknown bot"))?;
        if !row.config.browser {
            bail!("Turn on “Use a browser” for this bot first.");
        }
        let engine_name = super::engine_for(&row.config.browser_engine)?;
        let profile = profile_dir(bot);
        let (engine, engine_name) =
            launch_engine(&profile, engine_name).await.with_context(|| format!("starting this bot's {engine_name}"))?;
        Ok(Self {
            kind: engine_name,
            running: AsyncMutex::new(Some(engine)),
            shared: Mutex::new(Shared { active: None, user_control: false, user_last: Instant::now() }),
        })
    }

    pub fn engine(&self) -> &'static str {
        self.kind
    }

    /// The tab the bot drives, once one has been chosen.
    pub fn active(&self) -> Option<String> {
        self.shared.locked().active.clone()
    }

    pub fn alive(&self) -> bool {
        match self.running.try_lock() {
            Ok(mut guard) => guard.as_mut().is_some_and(Engine::alive),
            Err(_) => true,
        }
    }

    pub fn stop(&self) {
        if let Ok(mut guard) = self.running.try_lock()
            && let Some(engine) = guard.as_mut()
        {
            engine.stop();
        }
    }

    // MARK: control

    /// Releases a stale takeover: 50 s without input hands the browser back to the bot.
    fn tick(&self) {
        let mut shared = self.shared.locked();
        if shared.user_control && shared.user_last.elapsed() >= AUTO_RELEASE {
            shared.user_control = false;
        }
    }

    pub fn control(&self) -> Value {
        self.tick();
        let shared = self.shared.locked();
        let left = AUTO_RELEASE.saturating_sub(shared.user_last.elapsed());
        json!({
            "control": if shared.user_control { "user" } else { "bot" },
            "secondsLeft": if shared.user_control { left.as_secs() } else { 0 },
        })
    }

    pub fn set_user_control(&self, on: bool) {
        self.tick();
        let mut shared = self.shared.locked();
        shared.user_control = on;
        shared.user_last = Instant::now();
    }

    /// Every input from the panel counts as taking over and keeps the 50 s window open.
    fn user_acted(&self) {
        let mut shared = self.shared.locked();
        shared.user_control = true;
        shared.user_last = Instant::now();
    }

    /// The bot may act unless the user has the wheel.
    fn ensure_bot_may_act(&self) -> Result<()> {
        self.tick();
        if self.shared.locked().user_control {
            bail!(
                "The user is using the browser from their desktop right now. Wait a little (it comes back on its own), \
                 or ask them to hand it back."
            );
        }
        Ok(())
    }

    // MARK: tabs

    async fn running_engine(&self) -> Result<tokio::sync::MutexGuard<'_, Option<Engine>>> {
        let guard = self.running.lock().await;
        if guard.as_ref().is_none() {
            bail!("the browser stopped; open it again");
        }
        Ok(guard)
    }

    pub async fn tabs(&self) -> Result<Vec<TabInfo>> {
        self.running_engine().await?.as_ref().expect("checked").list().await
    }

    /// The tab the bot drives; creates `about:blank` when none exists or the old one is gone.
    async fn active_tab(&self, engine: &Engine) -> Result<String> {
        let tabs = engine.list().await?;
        {
            let mut shared = self.shared.locked();
            if let Some(active) = shared.active.clone().filter(|id| tabs.iter().any(|t| &t.id == id)) {
                return Ok(active);
            }
            if let Some(first) = tabs.first() {
                shared.active = Some(first.id.clone());
                return Ok(first.id.clone());
            }
        }
        let tab = engine.create("about:blank").await?;
        self.shared.locked().active = Some(tab.id.clone());
        Ok(tab.id)
    }

    pub async fn new_tab(&self, url: Option<&str>) -> Result<TabInfo> {
        self.ensure_bot_may_act()?;
        let url = match url {
            Some(url) => normalize_url(url)?,
            None => "about:blank".to_owned(),
        };
        let engine_guard = self.running_engine().await?;
        let engine = engine_guard.as_ref().expect("checked");
        let tab = engine.create(&url).await?;
        self.shared.locked().active = Some(tab.id.clone());
        Ok(tab)
    }

    pub async fn close_tab(&self, id: &str) -> Result<()> {
        self.ensure_bot_may_act()?;
        let engine_guard = self.running_engine().await?;
        let engine = engine_guard.as_ref().expect("checked");
        if engine.list().await?.len() <= 1 {
            bail!("the browser needs at least one tab open");
        }
        engine.close(id).await?;
        let mut shared = self.shared.locked();
        if shared.active.as_deref() == Some(id) {
            shared.active = None;
        }
        Ok(())
    }

    pub async fn select_tab(&self, id: &str) -> Result<()> {
        let engine_guard = self.running_engine().await?;
        let engine = engine_guard.as_ref().expect("checked");
        if !engine.list().await?.iter().any(|t| t.id == id) {
            bail!("unknown tab");
        }
        self.shared.locked().active = Some(id.to_owned());
        Ok(())
    }

    /// Navigates the active tab (`url` with no scheme gets one).
    pub async fn navigate(&self, url: &str) -> Result<TabInfo> {
        self.ensure_bot_may_act()?;
        let url = normalize_url(url)?;
        let engine_guard = self.running_engine().await?;
        let engine = engine_guard.as_ref().expect("checked");
        let id = self.active_tab(engine).await?;
        engine.navigate(&id, &url).await?;
        // The engine still lists the old page for a moment; report where it's going.
        Ok(TabInfo { id, title: String::new(), url })
    }

    pub async fn reload(&self) -> Result<()> {
        self.ensure_bot_may_act()?;
        let engine_guard = self.running_engine().await?;
        let engine = engine_guard.as_ref().expect("checked");
        let id = self.active_tab(engine).await?;
        engine.reload(&id).await
    }

    /// Back/forward from the panel; using the toolbar is itself a takeover.
    pub async fn history(&self, back: bool) -> Result<()> {
        self.user_acted();
        let engine_guard = self.running_engine().await?;
        let engine = engine_guard.as_ref().expect("checked");
        let id = self.active_tab(engine).await?;
        engine.evaluate(&id, if back { "history.back()" } else { "history.forward()" }).await?;
        Ok(())
    }

    // MARK: watching and driving

    /// The active tab's frame for the panel. Watching never counts as input.
    pub async fn frame(&self) -> Result<Frame> {
        self.tick();
        let engine_guard = self.running_engine().await?;
        let engine = engine_guard.as_ref().expect("checked");
        let id = self.active_tab(engine).await?;
        let info = engine.list().await?.into_iter().find(|t| t.id == id);
        let shot = engine.screenshot(&id).await?;
        Ok(Frame {
            shot,
            tab: id,
            url: info.as_ref().map(|t| t.url.clone()).unwrap_or_default(),
            title: info.map(|t| t.title).unwrap_or_default(),
        })
    }

    pub async fn click(&self, x: f64, y: f64) -> Result<()> {
        self.user_acted();
        let engine_guard = self.running_engine().await?;
        let engine = engine_guard.as_ref().expect("checked");
        let id = self.active_tab(engine).await?;
        engine.click(&id, x, y).await
    }

    pub async fn type_text(&self, text: &str) -> Result<()> {
        self.user_acted();
        let engine_guard = self.running_engine().await?;
        let engine = engine_guard.as_ref().expect("checked");
        let id = self.active_tab(engine).await?;
        engine.type_text(&id, text).await
    }

    pub async fn press(&self, key: &str) -> Result<()> {
        self.user_acted();
        let engine_guard = self.running_engine().await?;
        let engine = engine_guard.as_ref().expect("checked");
        let id = self.active_tab(engine).await?;
        engine.press(&id, key).await
    }

    pub async fn scroll(&self, x: f64, y: f64, delta_y: f64) -> Result<()> {
        self.user_acted();
        let engine_guard = self.running_engine().await?;
        let engine = engine_guard.as_ref().expect("checked");
        let id = self.active_tab(engine).await?;
        engine.scroll(&id, x, y, delta_y).await
    }

    // MARK: the bot's own tools

    /// Opens `url` in a new tab when asked, else the active one.
    pub async fn open(&self, url: &str, new_tab: bool) -> Result<TabInfo> {
        self.ensure_bot_may_act()?;
        if new_tab {
            return self.new_tab(Some(url)).await;
        }
        self.navigate(url).await
    }

    pub async fn agent_evaluate(&self, js: &str) -> Result<Value> {
        self.ensure_bot_may_act()?;
        let engine_guard = self.running_engine().await?;
        let engine = engine_guard.as_ref().expect("checked");
        let id = self.active_tab(engine).await?;
        engine.evaluate(&id, js).await
    }

    pub async fn agent_click(&self, x: f64, y: f64) -> Result<()> {
        self.ensure_bot_may_act()?;
        let engine_guard = self.running_engine().await?;
        let engine = engine_guard.as_ref().expect("checked");
        let id = self.active_tab(engine).await?;
        engine.click(&id, x, y).await
    }

    pub async fn agent_type(&self, text: &str) -> Result<()> {
        self.ensure_bot_may_act()?;
        let engine_guard = self.running_engine().await?;
        let engine = engine_guard.as_ref().expect("checked");
        let id = self.active_tab(engine).await?;
        engine.type_text(&id, text).await
    }

    pub async fn agent_press(&self, key: &str) -> Result<()> {
        self.ensure_bot_may_act()?;
        let engine_guard = self.running_engine().await?;
        let engine = engine_guard.as_ref().expect("checked");
        let id = self.active_tab(engine).await?;
        engine.press(&id, key).await
    }

    pub async fn agent_scroll(&self, x: f64, y: f64, delta_y: f64) -> Result<()> {
        self.ensure_bot_may_act()?;
        let engine_guard = self.running_engine().await?;
        let engine = engine_guard.as_ref().expect("checked");
        let id = self.active_tab(engine).await?;
        engine.scroll(&id, x, y, delta_y).await
    }

    /// A screenshot for the bot (its own content, same viewport as the panel).
    pub async fn agent_screenshot(&self) -> Result<Screenshot> {
        let engine_guard = self.running_engine().await?;
        let engine = engine_guard.as_ref().expect("checked");
        let id = self.active_tab(engine).await?;
        engine.screenshot(&id).await
    }

    /// The active tab's visible text, trimmed for an agent prompt.
    pub async fn agent_read(&self) -> Result<String> {
        let text = self.agent_evaluate("document.body ? document.body.innerText : ''").await?;
        Ok(text.as_str().unwrap_or_default().to_owned())
    }
}
