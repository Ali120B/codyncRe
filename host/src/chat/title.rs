//! Chat titles: when a chat starts, the bot's own agent names it on a throwaway
//! process — a real AI title, and the chat's own context is never touched.

use crate::agent::acp::{self, Incoming};
use crate::hub::Hub;
use anyhow::{Context, Result, anyhow};
use serde_json::json;
use std::collections::HashSet;
use std::sync::{Arc, Mutex, OnceLock};
use std::time::Duration;

const ASK: &str =
    "Name this conversation for a chat list. Reply with only the name: 2 to 5 plain words, no quotes, no period.";

/// Names the chat in the background, once, if it has no name yet.
pub(crate) fn spawn(hub: Arc<Hub>, bot: String, chat: String, seed: String) {
    // A burst of messages must not start several naming calls for the same chat.
    let key = format!("{bot}@{chat}");
    if !in_flight().lock().is_ok_and(|mut keys| keys.insert(key.clone())) {
        return;
    }
    tokio::spawn(async move {
        if let Err(error) = generate(&hub, &bot, &chat, &seed).await {
            tracing::info!(bot, error = format!("{error:#}"), "couldn't name a chat");
        }
        if let Ok(mut keys) = in_flight().lock() {
            keys.remove(&key);
        }
    });
}

fn in_flight() -> &'static Mutex<HashSet<String>> {
    static KEYS: OnceLock<Mutex<HashSet<String>>> = OnceLock::new();
    KEYS.get_or_init(|| Mutex::new(HashSet::new()))
}

async fn generate(hub: &Arc<Hub>, bot: &str, chat: &str, seed: &str) -> Result<()> {
    let seed = seed.trim();
    if seed.is_empty() || hub.store.chat_title(bot, chat).is_some() {
        return Ok(());
    }
    let Some(owner) = hub.store.bot(bot)?.filter(|r| !r.deleted) else { return Ok(()) };
    // A group has no agent of its own: its first member with one names the chat.
    let row = if owner.config.is_group() {
        let Some(member) = owner
            .config
            .members
            .iter()
            .find_map(|id| hub.store.bot(id).ok().flatten().filter(|r| !r.deleted && !r.config.backend.is_empty()))
        else {
            return Ok(());
        };
        member
    } else {
        owner
    };
    let (acp, mut inbox, _, _) = crate::agent::auth::start(&hub.store, &row.config.backend).await?;
    let ask = format!("{ASK}\n\nIt starts with: {}", seed.chars().take(600).collect::<String>());
    let result = tokio::time::timeout(Duration::from_secs(60), async {
        let session = acp.request("session/new", json!({"cwd": row.config.cwd, "mcpServers": []})).await?;
        let sid = session["sessionId"].as_str().ok_or_else(|| anyhow!("agent returned no sessionId"))?.to_owned();
        // The throwaway process starts on the agent's default model; use the bot's own.
        crate::agent::bot::select_model(&acp, &session, &sid, &row.config).await?;
        let request =
            acp.request("session/prompt", json!({"sessionId": sid, "prompt": [{"type": "text", "text": ask}]}));
        tokio::pin!(request);
        let mut out = String::new();
        loop {
            tokio::select! {
                answer = &mut request => {
                    answer?;
                    break;
                }
                incoming = inbox.recv() => match incoming {
                    None | Some(Incoming::Closed { .. }) => break,
                    Some(Incoming::Notification { method, params }) => {
                        let u = &params["update"];
                        if method == "session/update" && u["sessionUpdate"] == "agent_message_chunk" {
                            out.push_str(&acp::content_text(&u["content"]));
                        }
                    }
                    Some(Incoming::Request { id, .. }) => {
                        let _ = acp.respond_error(id, -32601, "not supported").await;
                    }
                }
            }
        }
        Ok::<String, anyhow::Error>(out)
    })
    .await;
    acp.kill().await;
    let out = result.context("naming timed out")??;
    let Some(title) = tidy(&out) else { return Ok(()) };
    tracing::debug!(bot, chat, title, "named a chat");
    hub.store.set_chat_title(bot, chat, &title)?;
    let _ = hub.events.send(json!({"type": "chatTitle", "botId": bot, "chatId": chat, "title": title}));
    Ok(())
}

/// The first line, unquoted, without a "Title:" label or trailing period; 2–60 characters or nothing.
fn tidy(text: &str) -> Option<String> {
    let line = text.lines().map(str::trim).find(|line| !line.is_empty())?;
    let line = line.trim_start_matches(['#', '*', ' ']);
    let line = ["title:", "chat title:", "name:"]
        .iter()
        .find_map(|label| {
            line.get(..label.len()).filter(|head| head.eq_ignore_ascii_case(label)).map(|_| &line[label.len()..])
        })
        .unwrap_or(line)
        .trim_start_matches(['*', ' ', ':']);
    let clean = line.trim_matches(['"', '\'', '\u{201c}', '\u{201d}', '.', ' ']).trim_end_matches(['.', '*']).trim();
    if clean.chars().count() < 2 {
        return None;
    }
    Some(clean.chars().take(60).collect())
}
