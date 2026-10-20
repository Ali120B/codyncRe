//! Browser methods: the Computer panel's window into a bot's headless browser (`browser*`),
//! plus the `browser` MCP server's calls (`browserCall`).

use super::str_arg;
use crate::browser;
use crate::hub::Hub;
use anyhow::{Result, anyhow};
use base64::Engine as _;
use serde_json::{Value, json};
use std::ops::ControlFlow;
use std::sync::Arc;

pub(super) async fn call(hub: &Arc<Hub>, method: &str, b: Value) -> Result<ControlFlow<Value, Value>> {
    Ok(ControlFlow::Break(match method {
        // What the panel polls: whether the bot may use a browser, the engine, the tabs,
        // the active tab, and who is driving.
        "browserState" => {
            let bot = str_arg(&b, "botId")?;
            let row = hub.store.bot(bot)?.filter(|r| !r.deleted).ok_or_else(|| anyhow!("unknown bot"))?;
            let (engine, engine_error) = match browser::engine_for(&row.config.browser_engine) {
                Ok(name) => (Some(name), None),
                Err(e) => (None, Some(e.to_string())),
            };
            // A bot can browse in a room whose group switch is on even when its own is off.
            let enabled = row.config.browser || hub.store.room_tools(bot)?.0;
            let mut state = json!({
                "enabled": enabled,
                "engine": engine,
                "engineError": engine_error,
                "running": false,
                "tabs": [],
                "activeTab": null,
            });
            if enabled
                && engine.is_some()
                && let Some(session) = hub.browser.running(bot)
            {
                // Only report a session that's already running: state never starts Chrome.
                if session.alive() {
                    let tabs = session.tabs().await.unwrap_or_default();
                    let control = session.control();
                    let active = session.active().or_else(|| tabs.first().map(|t| t.id.clone()));
                    let info = active.as_ref().and_then(|id| tabs.iter().find(|t| &t.id == id));
                    state["engine"] = session.engine().into();
                    state["running"] = true.into();
                    state["tabs"] = serde_json::to_value(&tabs)?;
                    state["activeTab"] = json!(active);
                    state["control"] = control["control"].clone();
                    state["secondsLeft"] = control["secondsLeft"].clone();
                    state["url"] = json!(info.map(|t| t.url.clone()).unwrap_or_default());
                    state["title"] = json!(info.map(|t| t.title.clone()).unwrap_or_default());
                }
            }
            state
        }
        // One frame of the active tab, base64 (JPEG from Chrome, PNG from Firefox).
        "browserFrame" => {
            let session = session(hub, &b).await?;
            let frame = session.frame().await?;
            json!({
                "data": base64::engine::general_purpose::STANDARD.encode(&frame.shot.data),
                "format": frame.shot.format,
                "tab": frame.tab,
                "url": frame.url,
                "title": frame.title,
                "width": 1280,
                "height": 800,
                "control": session.control()["control"].clone(),
            })
        }
        // Starts the browser (first call) or moves the active tab.
        "browserOpen" => {
            let session = session(hub, &b).await?;
            session.user_open(str_arg(&b, "url")?, b["newTab"] == true).await?;
            json!({})
        }
        "browserNewTab" => {
            let session = session(hub, &b).await?;
            session.user_new_tab(b["url"].as_str()).await?;
            json!({})
        }
        "browserCloseTab" => {
            let session = session(hub, &b).await?;
            session.user_close_tab(str_arg(&b, "tabId")?).await?;
            json!({})
        }
        "browserSelectTab" => {
            let session = session(hub, &b).await?;
            session.user_select_tab(str_arg(&b, "tabId")?).await?;
            json!({})
        }
        "browserReload" => {
            let session = session(hub, &b).await?;
            session.user_reload().await?;
            json!({})
        }
        "browserBack" | "browserForward" => {
            let session = session(hub, &b).await?;
            session.history(method == "browserBack").await?;
            json!({})
        }
        // What the user does in the panel: real input into the page.
        "browserClick" => {
            let session = session(hub, &b).await?;
            session.click(number(&b, "x")?, number(&b, "y")?).await?;
            json!({})
        }
        "browserType" => {
            let session = session(hub, &b).await?;
            session.type_text(str_arg(&b, "text")?).await?;
            json!({})
        }
        "browserPress" => {
            let session = session(hub, &b).await?;
            session.press(str_arg(&b, "key")?).await?;
            json!({})
        }
        "browserScroll" => {
            let session = session(hub, &b).await?;
            session
                .scroll(b["x"].as_f64().unwrap_or(640.0), b["y"].as_f64().unwrap_or(400.0), number(&b, "deltaY")?)
                .await?;
            json!({})
        }
        // Take-control switch ("user" / "bot").
        "browserControl" => {
            let session = session(hub, &b).await?;
            session.set_user_control(str_arg(&b, "control")? == "user");
            session.control()
        }
        // Closes the browser (and its profile process).
        "browserStop" => {
            hub.browser.stop(str_arg(&b, "botId")?);
            json!({})
        }
        // The `browser` MCP server's one entry point.
        "browserCall" => {
            let args = b.get("arguments").filter(|a| a.is_object()).cloned().unwrap_or_else(|| json!({}));
            browser::tools::browser(hub, str_arg(&b, "botId")?, str_arg(&b, "name")?, args).await?
        }
        _ => return Ok(ControlFlow::Continue(b)),
    }))
}

/// The bot's running session; the panel's frame/action calls keep it as is.
async fn session(hub: &Arc<Hub>, b: &Value) -> Result<Arc<browser::BotBrowser>> {
    hub.browser.session(hub, str_arg(b, "botId")?).await
}

fn number(b: &Value, key: &str) -> Result<f64> {
    b[key].as_f64().ok_or_else(|| anyhow!("{key} must be a number"))
}
