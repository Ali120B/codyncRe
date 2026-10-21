//! "Rewind to before this message": restore the turn's workspace checkpoints and remove
//! the message and everything after it in that lane (and any threads that branch from it).

use crate::agent::bot::Cmd;
use crate::agent::checkpoint;
use crate::hub::{BotStatus, Hub};
use crate::store::{EntryKind, Lane, now_ms};
use anyhow::{Context, Result, anyhow, bail};
use serde_json::{Value, json};
use std::collections::BTreeSet;
use std::sync::Arc;

pub async fn rewind(hub: &Arc<Hub>, bot_id: &str, entry_id: &str) -> Result<Value> {
    let row = hub.store.bot(bot_id)?.filter(|row| !row.deleted).ok_or_else(|| anyhow!("unknown chat"))?;
    let target = hub.store.entry(entry_id).ok_or_else(|| anyhow!("unknown message"))?;
    if target.bot_id != bot_id || target.kind != EntryKind::User.as_str() {
        bail!("only a message you sent can rewind a turn");
    }
    ensure_idle(hub, &row.config)?;

    let chat_root = target.thread_id.is_none() && target.data["chat"].as_bool() == Some(true);
    let lane = if let Some(root) = &target.thread_id {
        Lane::in_thread(bot_id, root)
    } else if chat_root {
        Lane::in_thread(bot_id, &target.id)
    } else {
        Lane::main(bot_id)
    };
    let restored = checkpoint::restore(bot_id, &lane, target.turn)
        .await
        .context("this turn has no file checkpoint")?;

    let mut ids = BTreeSet::new();
    if chat_root {
        ids.insert(target.id.clone());
        for entry in hub.store.lane_entries_after(&lane, 0, 5000)? {
            ids.insert(entry.id);
        }
    } else if target.thread_id.is_some() {
        for entry in hub.store.lane_entries_after(&lane, target.seq.saturating_sub(1), 5000)? {
            ids.insert(entry.id);
        }
    } else {
        for entry in hub.store.lane_entries_after(&lane, target.seq.saturating_sub(1), 5000)? {
            ids.insert(entry.id.clone());
            // A thread rooted in this part of the chat is part of the same branch.
            let thread = Lane::in_thread(bot_id, &entry.id);
            for reply in hub.store.lane_entries_after(&thread, 0, 5000)? {
                ids.insert(reply.id);
            }
        }
    }
    if ids.is_empty() {
        bail!("nothing to rewind");
    }
    let ids: Vec<String> = ids.into_iter().collect();
    let deleted = hub.delete_entries(&ids)?;

    // The agent must not remember the messages we just removed.
    if row.config.is_group() {
        for member in &row.config.members {
            let _ = hub.send_cmd(member, Cmd::ForgetLane { lane: lane.clone() });
        }
    } else {
        let _ = hub.send_cmd(bot_id, Cmd::ForgetLane { lane: lane.clone() });
    }
    let turn = hub.store.max_turn(bot_id);
    let _ = hub.add_entry(
        &lane,
        EntryKind::Notice,
        turn,
        &json!({
            "text": "Rewound. Files were restored to how they were before that message.",
            "style": "rewound",
            "at": now_ms(),
        }),
    );
    Ok(json!({"deleted": deleted, "restored": restored, "turn": target.turn, "ids": ids}))
}

fn ensure_idle(hub: &Hub, config: &crate::store::BotConfig) -> Result<()> {
    if config.is_group() {
        for member in &config.members {
            let status = hub.runtime(member).status;
            if matches!(status, BotStatus::Working | BotStatus::NeedsInput) {
                bail!("stop the room before rewinding it");
            }
        }
    } else if matches!(hub.runtime(&config.id).status, BotStatus::Working | BotStatus::NeedsInput) {
        bail!("stop the bot before rewinding it");
    }
    Ok(())
}
