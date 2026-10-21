//! Group chats (Grok Bot's design): several bots and the user in one conversation.
//!
//! A group is a roster row (`BotKind::Group`) with a member list and its own
//! transcript, but no agent. When the user writes, the group runs a *room turn*:
//! up to [`MAX_ROUNDS`] rounds in which the members answer one at a time, each in
//! its **own** session (a member's history is unified across its chats), told
//! what was said since it last spoke. Everyone answers unless the new messages
//! @-mention someone; a member with nothing to add says `(pass)`, and a round
//! where nobody speaks ends the turn. A newer message in the same lane (or Stop)
//! moves the lane's epoch, which ends a running room turn before its next speaker.
//!
//! Threads work the same way inside a group: the room turn runs in the thread's
//! lane and only sees the thread.

use crate::LockExt;
use crate::agent::bot::Cmd;
use crate::agent::checkpoint;
use crate::hub::{BotStatus, Hub};
use crate::remote::push::{self, AlertKind};
use crate::store::{BotConfig, BotKind, Entry, EntryKind, Lane, Store, now_ms};
use anyhow::{Result, anyhow, bail};
use std::collections::{HashMap, HashSet};
use std::sync::{Arc, Mutex};
use tokio::sync::oneshot;

const MAX_ROUNDS: usize = 3;
/// Replies per room turn, across all rounds.
const MAX_REPLIES: usize = 10;
/// Room lines a member is shown per turn.
const HISTORY_LINES: i64 = 24;
/// A bot's line in another member's prompt is clipped (the user's never is).
const LINE_CHARS: usize = 2000;
const PASS: &str = "(pass)";

/// One member's turn in a room, queued on its actor like any other turn.
pub struct GroupTurn {
    pub lane: Lane,
    pub prompt: String,
    /// The room turn that asked; a member skips it once a newer one started (see [`Rooms::current`]).
    pub epoch: u64,
    /// A standup answer stays in the trace; the room gets one merged summary instead.
    pub standup: bool,
    /// The reply the room sees, or `None` for a pass.
    pub reply: oneshot::Sender<Result<Option<String>>>,
}

/// Room turn epochs per lane (`Lane::key`).
#[derive(Default)]
pub struct Rooms(Mutex<HashMap<String, u64>>);

impl Rooms {
    fn next(&self, lane: &Lane) -> u64 {
        let mut map = self.0.locked();
        let epoch = map.entry(lane.key()).or_default();
        *epoch += 1;
        *epoch
    }

    pub fn current(&self, lane: &Lane, epoch: u64) -> bool {
        self.0.locked().get(&lane.key()).copied() == Some(epoch)
    }

    /// New session on a group: the room starts from a fresh context — every member forgets
    /// its sessions for this group (their own chats keep theirs). The transcript stays.
    pub fn new_session(&self, hub: &Hub, group: &str) {
        self.stop(hub, group);
        let Ok(Some(row)) = hub.store.bot(group) else { return };
        for member in &row.config.members {
            let _ = hub.send_cmd(member, Cmd::GroupNewSession { group: group.to_owned() });
        }
        let lane = Lane::main(group);
        let turn = hub.store.max_turn(group);
        hub.add_entry(
            &lane,
            EntryKind::Notice,
            turn,
            &serde_json::json!({"text": "New session — the room starts with a fresh context.", "style": "divider"}),
        );
    }

    /// Stop: ends every room turn in `group` (main chat and threads) and the member turns in it.
    pub fn stop(&self, hub: &Hub, group: &str) {
        let thread_prefix = format!("{group}/");
        let mut stopped = false;
        for (key, epoch) in self.0.locked().iter_mut() {
            if key == group || key.starts_with(&thread_prefix) {
                *epoch += 1;
                stopped = true;
            }
        }
        if !stopped {
            return;
        }
        let Ok(Some(row)) = hub.store.bot(group) else { return };
        for member in &row.config.members {
            let _ = hub.send_cmd(member, Cmd::CancelGroup { chat: group.to_owned() });
        }
    }
}

/// Existing, distinct agent bots (not groups, not the group itself).
pub fn valid_members(store: &Store, group_id: &str, ids: &[String]) -> Result<Vec<String>> {
    let mut out: Vec<String> = vec![];
    for id in ids.iter().map(|i| i.trim()) {
        if id.is_empty() || id == group_id || out.iter().any(|o| o == id) {
            continue;
        }
        match store.bot(id)? {
            Some(row) if !row.deleted && row.config.kind == BotKind::Agent => out.push(id.to_owned()),
            Some(row) if row.config.is_group() => bail!("a group can't be a member of another group"),
            _ => {}
        }
    }
    if out.len() < 2 {
        bail!("a group needs at least 2 bots");
    }
    if out.len() > 50 {
        bail!("a group can have at most 50 bots");
    }
    Ok(out)
}

/// A group whose members are exactly `members`.
pub fn with_members(store: &Store, members: &[String]) -> Result<Option<String>> {
    let mut wanted = members.to_vec();
    wanted.sort();
    Ok(store.bots()?.into_iter().find_map(|row| {
        let mut have = row.config.members.clone();
        have.sort();
        (!row.deleted && row.config.is_group() && have == wanted).then_some(row.config.id)
    }))
}

/// The user wrote in a group lane: start a room turn there (ending one still running).
pub fn start(hub: &Arc<Hub>, group: &BotConfig, lane: Lane) {
    start_with(hub, group, lane, None);
}

/// [`start`], reporting whether the turn ran to the end: a group routine waits for it.
pub fn start_with(hub: &Arc<Hub>, group: &BotConfig, lane: Lane, done: Option<oneshot::Sender<bool>>) {
    let epoch = hub.groups.next(&lane);
    let (hub, group_id) = (hub.clone(), group.id.clone());
    tokio::spawn(async move {
        let outcome = run(&hub, &group_id, &lane, epoch).await;
        if let Err(error) = &outcome {
            tracing::warn!(group = group_id, error = format!("{error:#}"), "room turn failed");
        }
        if let Some(done) = done {
            let _ = done.send(outcome.is_ok());
        }
    });
}

async fn run(hub: &Arc<Hub>, group_id: &str, lane: &Lane, epoch: u64) -> Result<()> {
    let user_said = last_user_message(hub, lane)?.into_iter().collect::<Vec<_>>();
    let Some(user) = user_said.first().cloned() else { return Ok(()) };
    let group = hub.store.bot(group_id)?.filter(|r| !r.deleted).ok_or_else(|| anyhow!("group deleted"))?.config;
    let members_now = members(hub, &group);
    // The room checkpoints every member before the first bot can touch a file. This is
    // one snapshot for the whole room turn, not one per speaker.
    for member in &members_now {
        if let Err(error) = checkpoint::capture(group_id, lane, user.turn, &member.id, &member.cwd).await {
            tracing::warn!(bot = %member.id, error = format!("{error:#}"), "couldn't save a room checkpoint");
        }
    }
    if is_standup_request(&user) {
        return run_standup(hub, &group, lane, epoch, &members_now, user.turn).await;
    }

    let mut replies = 0;
    let mut last_reply: Option<(String, String, String)> = None;
    // A bot @-mentioning a peer is a handoff: the peer gets one turn with what the first
    // bot tried, what changed, and the next step. One handoff per peer per room turn.
    let mut handed_off: HashSet<String> = HashSet::new();
    'rounds: for round in 0..MAX_ROUNDS {
        let group = hub.store.bot(group_id)?.filter(|r| !r.deleted).ok_or_else(|| anyhow!("group deleted"))?.config;
        let members = members(hub, &group);
        let mut speakers = responders(&members, &user_said);
        // A leader speaks first every round; otherwise each round starts one member later.
        if let Some(i) = speakers.iter().position(|m| !group.leader.is_empty() && m.id == group.leader) {
            speakers.swap(0, i);
        } else if !speakers.is_empty() {
            let offset = round % speakers.len();
            speakers.rotate_left(offset);
        }
        let mut spoke = 0;
        for member in &speakers {
            if !hub.groups.current(lane, epoch) {
                return Ok(());
            }
            // A full room turn still reports its last reply.
            if replies >= MAX_REPLIES {
                break 'rounds;
            }
            if handed_off.contains(&member.id) {
                continue;
            }
            let prompt = member_prompt(hub, &group, &members, member, lane)?;
            // A failed, cancelled or overdue member turn counts as a pass; its error shows in the room.
            if let Some(text) = ask_member(hub, member, lane, epoch, prompt, false).await {
                spoke += 1;
                replies += 1;
                last_reply = Some((member.id.clone(), member.name.clone(), text.clone()));
                for target_id in handoff_targets(&text, &members, &member.id) {
                    if replies >= MAX_REPLIES || !hub.groups.current(lane, epoch) {
                        break;
                    }
                    if !handed_off.insert(target_id.clone()) {
                        continue;
                    }
                    let Some(target) = members.iter().find(|m| m.id == target_id) else { continue };
                    let packet = handoff_packet(hub, &group, lane, member, &text, user.seq);
                    let Ok(prompt) = handoff_prompt(hub, &group, &members, target, lane, &packet) else { continue };
                    if let Some(answer) = ask_member(hub, target, lane, epoch, prompt, false).await {
                        replies += 1;
                        spoke += 1;
                        last_reply = Some((target.id.clone(), target.name.clone(), answer));
                    }
                }
            }
        }
        if spoke == 0 {
            break;
        }
    }
    if let Some((from, name, text)) = last_reply
        && let Some(group) = hub.store.bot(group_id)?.map(|r| r.config)
    {
        push::notify(hub, &group, Some(&from), &format!("{name} in {}", group.name), &text, AlertKind::Done);
    }
    Ok(())
}

/// Queues one member turn and waits for its reply. A pass is `None`, as is a failure.
async fn ask_member(
    hub: &Arc<Hub>,
    member: &BotConfig,
    lane: &Lane,
    epoch: u64,
    prompt: String,
    standup: bool,
) -> Option<String> {
    let (reply, answer) = oneshot::channel();
    let turn = GroupTurn { lane: lane.clone(), prompt, epoch, standup, reply };
    if hub.send_cmd(&member.id, Cmd::Group(turn)).is_err() {
        return None;
    }
    match tokio::time::timeout(crate::chat::team::ASK_TIMEOUT, answer).await {
        Ok(Ok(Ok(Some(text)))) => Some(text),
        _ => None,
    }
}

/// A room standup is the user writing just `@all` (or the header's standup action).
fn is_standup_request(entry: &Entry) -> bool {
    let text = entry.data["text"].as_str().unwrap_or_default().trim().to_lowercase();
    let text = text.trim_end_matches(['!', '.', '?', ' ']);
    matches!(text, "@all" | "@everyone" | "@standup" | "@all standup" | "@all status")
}

/// Ask every member for a status and blocker, then put one merged message in the room.
async fn run_standup(
    hub: &Arc<Hub>,
    group: &BotConfig,
    lane: &Lane,
    epoch: u64,
    members: &[BotConfig],
    turn: i64,
) -> Result<()> {
    let mut answers: Vec<(String, String)> = Vec::new();
    let mut pending: Vec<(&BotConfig, oneshot::Receiver<Result<Option<String>>>)> = Vec::new();
    for member in members {
        let runtime = hub.runtime(&member.id);
        match runtime.status {
            BotStatus::Working => answers.push((
                member.name.clone(),
                format!("**Working** — {} · _blocker: none reported_", nonempty(&runtime.activity, "a task")),
            )),
            BotStatus::NeedsInput => answers.push((
                member.name.clone(),
                "**Waiting for you** — needs approval to continue · _blocker: your input_".into(),
            )),
            BotStatus::Error => answers.push((
                member.name.clone(),
                "**Needs attention** — its last run failed · _blocker: a failed run_".into(),
            )),
            BotStatus::Idle => {
                let prompt = standup_prompt(group, members, member);
                let (reply, answer) = oneshot::channel();
                let asked = GroupTurn { lane: lane.clone(), prompt, epoch, standup: true, reply };
                if hub.send_cmd(&member.id, Cmd::Group(asked)).is_ok() {
                    pending.push((member, answer));
                } else {
                    answers.push((member.name.clone(), "**No response** — couldn't reach it".into()));
                }
            }
        }
    }
    let deadline = tokio::time::Instant::now() + std::time::Duration::from_secs(90);
    for (member, answer) in pending {
        let left = deadline.saturating_duration_since(tokio::time::Instant::now());
        match tokio::time::timeout(left, answer).await {
            Ok(Ok(Ok(Some(text)))) => {
                let (status, blocker) = parse_standup(&text);
                answers.push((
                    member.name.clone(),
                    format!("**{}** · _blocker: {}_", nonempty(&status, "No update"), nonempty(&blocker, "none")),
                ));
            }
            _ => answers.push((member.name.clone(), "**No response** — it didn't answer in time".into())),
        }
    }
    if !hub.groups.current(lane, epoch) {
        return Ok(());
    }
    // Preserve roster order, then add the one message the room actually sees.
    let mut lines = vec!["**Room standup**".to_owned(), String::new()];
    for member in members {
        if let Some((_, line)) = answers.iter().find(|(name, _)| name == &member.name) {
            lines.push(format!("- **{}** — {}", member.name, line));
        }
    }
    if lines.len() == 2 {
        lines.push("_No members answered._".into());
    }
    let text = lines.join("\n");
    if hub.store.bot(&group.id)?.is_some_and(|r| !r.deleted) {
        hub.add_entry(
            lane,
            EntryKind::Agent,
            turn,
            &serde_json::json!({
                "text": text,
                "author": group.id,
                "final": true,
                "standup": true,
                "at": now_ms(),
            }),
        );
    }
    Ok(())
}

fn standup_prompt(group: &BotConfig, members: &[BotConfig], me: &BotConfig) -> String {
    let peers = members.iter().filter(|m| m.id != me.id).map(|m| m.name.as_str()).collect::<Vec<_>>();
    let with = if peers.is_empty() { "the user".to_owned() } else { format!("the user and {}", peers.join(", ")) };
    format!(
        "You are {}, in the group chat \"{}\" with {with}. This is a room standup. Do not use tools or change files. \
         Reply with exactly two lines:\nSTATUS: one short sentence about what you are working on or what you last finished\n\
         BLOCKER: none, or one short sentence about what you need\n\
         Keep both lines under 20 words.",
        me.name, group.name
    )
}

fn parse_standup(text: &str) -> (String, String) {
    let mut status = String::new();
    let mut blocker = String::new();
    for line in text.lines() {
        let line = line.trim();
        if let Some(value) = line.strip_prefix("STATUS:") {
            status = value.trim().to_owned();
        } else if let Some(value) = line.strip_prefix("BLOCKER:") {
            blocker = value.trim().to_owned();
        }
    }
    if status.is_empty() {
        status = text.lines().map(str::trim).find(|line| !line.is_empty()).unwrap_or_default().to_owned();
    }
    (status, blocker)
}

fn nonempty(text: &str, fallback: &str) -> String {
    let text = text.trim();
    if text.is_empty() { fallback.to_owned() } else { text.to_owned() }
}

/// Members a bot named in its room reply (never itself, never the user's `@all`).
fn handoff_targets(text: &str, members: &[BotConfig], from: &str) -> Vec<String> {
    let mut out = Vec::new();
    for member in members {
        if member.id == from || out.contains(&member.id) {
            continue;
        }
        if handles(&member.name).iter().any(|h| mentions(text, h)) {
            out.push(member.id.clone());
        }
    }
    out
}

/// What the next bot needs to continue without repeating the handoff sender's work.
fn handoff_packet(hub: &Hub, group: &BotConfig, lane: &Lane, from: &BotConfig, reply: &str, after_seq: i64) -> String {
    let mut tried = Vec::new();
    let mut changed = Vec::new();
    if let Ok(entries) = hub.store.lane_entries_after(lane, after_seq, 5000) {
        for entry in entries {
            if entry.data["author"].as_str() != Some(from.id.as_str()) || entry.kind != EntryKind::Tool.as_str() {
                continue;
            }
            let title = entry.data["title"].as_str().unwrap_or("a tool").trim();
            if !title.is_empty() && !tried.iter().any(|t| t == title) {
                tried.push(title.to_owned());
            }
            for diff in entry.data["diffs"].as_array().into_iter().flatten() {
                if let Some(path) = diff["path"].as_str()
                    && !changed.iter().any(|p| p == path)
                {
                    changed.push(path.to_owned());
                }
            }
        }
    }
    let next = reply
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty())
        .next_back()
        .unwrap_or("continue from their message");
    format!(
        "Handoff from {} in \"{}\".\nThey tried: {}\nChanged: {}\nNext: {}\nTheir message: {}",
        from.name,
        group.name,
        if tried.is_empty() { "answered the room".to_owned() } else { tried.join(", ") },
        if changed.is_empty() { "no file changes reported".to_owned() } else { changed.join(", ") },
        clip(next, 300),
        clip(reply, 1200),
    )
}

fn handoff_prompt(
    hub: &Hub,
    group: &BotConfig,
    members: &[BotConfig],
    target: &BotConfig,
    lane: &Lane,
    packet: &str,
) -> Result<String> {
    let base = member_prompt(hub, group, members, target, lane)?;
    Ok(format!(
        "{packet}\n\nYou are picking up this handoff. Do not repeat work that is already done. \
         Continue it or reply with what is blocking you. Your reply is the room's message.\n\n{base}"
    ))
}

fn clip(text: &str, max: usize) -> String {
    if text.chars().count() <= max {
        return text.to_owned();
    }
    text.chars().take(max).collect::<String>() + "…"
}


fn members(hub: &Hub, group: &BotConfig) -> Vec<BotConfig> {
    group
        .members
        .iter()
        .filter_map(|id| hub.store.bot(id).ok().flatten())
        .filter(|r| !r.deleted)
        .map(|r| r.config)
        .collect()
}

/// The user's most recent message in the lane: the one that started this room turn.
fn last_user_message(hub: &Hub, lane: &Lane) -> Result<Option<Entry>> {
    let recent = hub.store.messages_after(lane, 0, HISTORY_LINES)?;
    Ok(recent.into_iter().rev().find(|e| e.kind == EntryKind::User.as_str()))
}

/// Who answers: the members @-mentioned in the user's message, or everyone
/// when nobody (or `@all` / `@everyone`) is.
fn responders<'a>(members: &'a [BotConfig], messages: &[Entry]) -> Vec<&'a BotConfig> {
    let texts: Vec<&str> = messages.iter().filter_map(|e| e.data["text"].as_str()).collect();
    let everyone = texts.iter().any(|t| mentions(t, "all") || mentions(t, "everyone"));
    let named: Vec<&BotConfig> =
        members.iter().filter(|m| texts.iter().any(|t| handles(&m.name).iter().any(|h| mentions(t, h)))).collect();
    if everyone || named.is_empty() { members.iter().collect() } else { named }
}

/// Ways to @-mention a bot: its full name, without spaces, or its first word (lowercased).
fn handles(name: &str) -> Vec<String> {
    let lower = name.trim().to_lowercase();
    let mut out = vec![lower.clone(), lower.split_whitespace().collect::<String>()];
    if let Some(first) = lower.split_whitespace().next() {
        out.push(first.to_owned());
    }
    out.retain(|h| !h.is_empty());
    out.dedup();
    out
}

/// `@handle` in `text`, not followed by more of a Latin word (`@al` isn't `@alice`).
/// CJK names are often followed straight by the message, so only ASCII letters end a match early.
fn mentions(text: &str, handle: &str) -> bool {
    let lower = text.to_lowercase();
    let needle = format!("@{handle}");
    lower
        .match_indices(&needle)
        .any(|(i, _)| lower[i + needle.len()..].chars().next().is_none_or(|c| !(c.is_ascii_alphanumeric() || c == '_')))
}

fn member_prompt(hub: &Hub, group: &BotConfig, members: &[BotConfig], me: &BotConfig, lane: &Lane) -> Result<String> {
    let peers: Vec<&BotConfig> = members.iter().filter(|m| m.id != me.id).collect();
    let peer_names = if peers.is_empty() {
        "just the user".to_owned()
    } else {
        peers.iter().map(|p| p.name.as_str()).collect::<Vec<_>>().join(", ")
    };
    let about = group.description.trim();
    let room = if about.is_empty() { format!("\"{}\"", group.name) } else { format!("\"{}\" — {about}", group.name) };
    let mut out =
        vec![format!("You are {}, one participant in a group chat ({room}) with the user and {peer_names}.", me.name)];
    if !group.instructions.trim().is_empty() {
        out.push(format!(
            "The room's rules, set by the user (they apply to everyone here): {}",
            group.instructions.trim()
        ));
    }
    if !group.leader.is_empty() && group.leader == me.id {
        out.push(
            "You are the room's leader: you answer first each round and hand work to the others when it helps.".into(),
        );
    }
    if !peers.is_empty() {
        out.push("Other participants in the room (these are the only people here):".into());
        for p in &peers {
            let about =
                if p.description.trim().is_empty() { String::new() } else { format!(" ({})", p.description.trim()) };
            out.push(format!("- {}{about}", p.name));
        }
    }
    out.push(
        "Never @-mention anyone who isn't in this room — bots from other groups or chats can't see it, \
         and pinging them only confuses the room."
            .into(),
    );
    out.push(format!(
        "You have your full toolkit here, but the only bots you can reach are this room's participants. \
         Do the work first, then answer: your final reply of this turn is the one message the room sees. \
         Keep it short and conversational. @-mention one of those participants only when you need them to \
         act. If you have nothing new worth adding, reply exactly \"{PASS}\". Never reveal private \
         one-on-one context."
    ));
    out.push(String::new());

    let seen = hub.store.last_spoke(lane, &me.id);
    let mut lines: Vec<String> = vec![];
    let mut header = format!("[Group chat: \"{}\" - with {peer_names}]", group.name);
    if let Some(root) = lane.thread.as_deref().and_then(|r| hub.store.entry(r)) {
        header = format!("[Group chat: \"{}\", in a thread - with {peer_names}]", group.name);
        if seen == 0 {
            lines.push(format!("(thread started on) {}", line(hub, group, &root, me)));
        }
    }
    for e in hub.store.messages_after(lane, seen, HISTORY_LINES)? {
        lines.push(line(hub, group, &e, me));
    }
    out.push(header);
    if lines.is_empty() {
        out.push("No new messages in the room since your last turn.".into());
    } else {
        out.push("New messages in the room (oldest first):".into());
        out.extend(lines);
    }
    out.push(String::new());
    out.push(format!(
        "It's your turn, {}. Reply to the room if you have something worth adding, or reply exactly \"{PASS}\" if you don't.",
        me.name
    ));
    Ok(out.join("\n"))
}

fn line(hub: &Hub, group: &BotConfig, e: &Entry, me: &BotConfig) -> String {
    let text = e.data["text"].as_str().unwrap_or_default();
    if e.kind == EntryKind::User.as_str() {
        let root = crate::chat::uploads::root(group);
        let files: Vec<_> = e.data["attachments"]
            .as_array()
            .into_iter()
            .flatten()
            .filter_map(|a| crate::chat::uploads::path_of(&root, a))
            .collect();
        let files = if files.is_empty() { String::new() } else { crate::chat::uploads::prompt_suffix(&files) };
        return format!("User: {text}{files}");
    }
    let author = e.data["author"].as_str().unwrap_or_default();
    let text = crate::agent::acp::truncate(text, LINE_CHARS);
    if author == me.id { format!("{} (you): {text}", me.name) } else { format!("{}: {text}", hub.bot_name(author)) }
}

/// Bot replies can only ping the room's own members. An `@someone` from another group is
/// left as a plain name, so nothing outside the room is invoked and readers aren't misled.
pub fn sanitize_mentions(text: &str, members: &[BotConfig]) -> String {
    let allowed: Vec<String> = members.iter().flat_map(|m| handles(&m.name)).collect();
    let mut out = String::with_capacity(text.len());
    let mut at = 0;
    while let Some(pos) = text[at..].find('@') {
        let start = at + pos;
        out.push_str(&text[at..start]);
        // An @ inside a word (an email address) isn't a mention.
        if text[..start].chars().next_back().is_some_and(|c| c.is_ascii_alphanumeric() || c == '_') {
            out.push('@');
            at = start + 1;
            continue;
        }
        let rest = &text[start + 1..];
        let end = rest
            .char_indices()
            .take_while(|(_, c)| c.is_ascii_alphanumeric() || *c == '_')
            .map(|(i, c)| i + c.len_utf8())
            .last()
            .unwrap_or(0);
        let word = &rest[..end];
        let lower = word.to_lowercase();
        let known = !word.is_empty() && (lower == "all" || lower == "everyone" || allowed.contains(&lower));
        if known {
            out.push('@');
        }
        out.push_str(word);
        at = start + 1 + end;
    }
    out.push_str(&text[at..]);
    out
}

/// A member's final text is a pass (the room doesn't see it).
pub fn is_pass(text: &str) -> bool {
    let t = text.trim().trim_matches(|c: char| c == '"' || c == '.' || c == '*').to_lowercase();
    t.is_empty() || t == PASS || t == "pass"
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    fn bot(id: &str, name: &str) -> BotConfig {
        serde_json::from_value(json!({"id": id, "name": name, "backend": "claude", "cwd": "/tmp"})).expect("valid bot")
    }

    fn said(text: &str) -> Entry {
        Entry {
            id: "e".into(),
            seq: 1,
            bot_id: "g".into(),
            thread_id: None,
            rev: 1,
            kind: "user".into(),
            turn: 1,
            data: json!({"text": text}),
            created_at: 0,
            updated_at: 0,
        }
    }

    #[test]
    fn mentions_pick_responders_and_no_mention_means_everyone() {
        let members = [bot("a", "Alice Chen"), bot("b", "Bob"), bot("c", "尼采課")];
        let ids = |m: &[BotConfig], text: &str| -> Vec<String> {
            responders(m, &[said(text)]).into_iter().map(|b| b.id.clone()).collect()
        };
        assert_eq!(ids(&members, "hi all"), ["a", "b", "c"]);
        assert_eq!(ids(&members, "@bob can you look"), ["b"]);
        assert_eq!(ids(&members, "@alicechen and @Alice"), ["a"]);
        assert_eq!(ids(&members, "@尼采課幫我整理"), ["c"], "CJK names can run into the message");
        assert_eq!(ids(&members, "@bobby?"), ["a", "b", "c"], "a longer word isn't a mention");
        assert_eq!(ids(&members, "@all go"), ["a", "b", "c"]);
    }

    #[test]
    fn only_room_members_keep_their_mention() {
        let members = [bot("a", "Kashmiri"), bot("b", "Rusty Nust")];
        assert_eq!(
            sanitize_mentions("ask @Rusty Nust to check, and @special too", &members),
            "ask @Rusty Nust to check, and special too"
        );
        assert_eq!(
            sanitize_mentions("@everyone @kashmiri look, @bob too", &members),
            "@everyone @kashmiri look, bob too"
        );
        assert_eq!(sanitize_mentions("email me at a@b.com", &members), "email me at a@b.com", "plain emails stay");
    }

    use crate::api::devices::Caller;
    use crate::hub::BotStatus;
    use std::path::PathBuf;
    use std::time::Duration;

    /// A host with agent bots `a` and `b` (the scripted fixture agent) and a group of both.
    struct Room {
        hub: Arc<Hub>,
        dir: PathBuf,
        group: String,
    }

    impl Room {
        fn new() -> Self {
            let dir = std::env::temp_dir().join(format!("codync-group-{}", uuid::Uuid::new_v4()));
            std::fs::create_dir_all(&dir).unwrap();
            let store = Store::open(&dir.join("test.db")).unwrap();
            let agent = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures/team_agent.py");
            for id in ["a", "b"] {
                let cwd = dir.join(id);
                std::fs::create_dir_all(&cwd).unwrap();
                let cfg: BotConfig = serde_json::from_value(json!({
                    "id": id, "name": id, "backend": "fixture", "cwd": cwd,
                    "command": crate::shell::python(&agent), "notify": false,
                }))
                .unwrap();
                store.save_bot(&cfg).unwrap();
            }
            let hub = Hub::new(
                store,
                "test-host".into(),
                crate::remote::identity::Identity::load_or_create(&dir).unwrap(),
                "test-token".into(),
                19222,
            );
            hub.start().unwrap();
            let group = hub
                .create_bot(
                    serde_json::from_value(
                        json!({"id": "", "kind": "group", "name": "Crew", "description": "Ship the release", "members": ["a", "b", "a"]}),
                    )
                    .unwrap(),
                )
                .unwrap();
            let group = group["id"].as_str().unwrap().to_owned();
            Self { hub, dir, group }
        }

        async fn send(&self, text: &str, thread: Option<&str>) -> Entry {
            let sent = crate::api::dispatch(
                &self.hub,
                &Caller::Local,
                "send",
                json!({"botId": self.group, "text": text, "threadId": thread}),
            )
            .await
            .unwrap();
            serde_json::from_value::<serde_json::Value>(sent["entry"].clone())
                .ok()
                .and_then(|e| self.hub.store.entry(e["id"].as_str()?))
                .unwrap()
        }

        fn replies(&self, lane: &Lane) -> Vec<(String, String)> {
            self.hub
                .store
                .messages_after(lane, 0, 100)
                .unwrap()
                .into_iter()
                .filter(|e| e.kind == "agent")
                .map(|e| (e.data["author"].as_str().unwrap().to_owned(), e.data["text"].as_str().unwrap().to_owned()))
                .collect()
        }

        async fn settle(&self) {
            tokio::time::timeout(Duration::from_secs(10), async {
                loop {
                    tokio::time::sleep(Duration::from_millis(300)).await;
                    let idle = ["a", "b"].iter().all(|b| self.hub.runtime(b).status == BotStatus::Idle);
                    if idle && self.hub.bots_json(0).unwrap().iter().all(|b| b["status"] == "idle") {
                        break;
                    }
                }
            })
            .await
            .expect("room settles");
        }

        async fn shutdown(self) {
            self.hub.shutdown().await;
            let _ = std::fs::remove_dir_all(self.dir);
        }
    }

    #[tokio::test]
    async fn room_turns_follow_mentions_and_end_when_everyone_passes() {
        let room = Room::new();
        let row = room.hub.store.bot(&room.group).unwrap().unwrap();
        assert_eq!(row.config.members, ["a", "b"], "members are deduplicated");
        let again = room
            .hub
            .create_bot(
                serde_json::from_value(json!({"id": "", "kind": "group", "name": "Dup", "members": ["b", "a"]}))
                    .unwrap(),
            )
            .unwrap();
        assert_eq!(again["id"], room.group, "the same bots reopen their group");

        let main = Lane::main(&room.group);
        room.send("hello everyone", None).await;
        room.settle().await;
        // `a` answers the user; `b` then sees a bot spoke last and passes; round two is all passes.
        assert_eq!(room.replies(&main), [("a".to_owned(), "reply: group".to_owned())]);

        room.send("@b your turn", None).await;
        room.settle().await;
        assert_eq!(room.replies(&main).last().unwrap().0, "b", "only the mentioned bot answers");
        assert_eq!(room.replies(&main).len(), 2);
        let listed = room.hub.bot_value_for_test(&room.group);
        assert_eq!(listed["lastMessage"], "b: reply: group");
        let prompts = std::fs::read_to_string(room.dir.join("a/prompts.jsonl")).unwrap();
        assert!(
            prompts.contains("Ship the release) with the user"),
            "members are told what the room is for: {prompts}"
        );
        // The members' own chats stay private: the room's entries live in the group.
        assert!(room.hub.store.messages_after(&Lane::main("a"), 0, 100).unwrap().is_empty());
        room.shutdown().await;
    }

    #[tokio::test]
    async fn threads_stay_out_of_the_main_chat_and_summarize_on_their_root() {
        let room = Room::new();
        let first = room.send("@a start here", None).await;
        room.settle().await;
        room.send("@a in the thread", Some(&first.id)).await;
        room.settle().await;
        let thread = Lane::in_thread(&room.group, &first.id);
        assert_eq!(room.replies(&thread).len(), 1);
        assert_eq!(room.replies(&Lane::main(&room.group)).len(), 1, "the thread's reply isn't in the main chat");
        let summary = room.hub.store.entry(&first.id).unwrap().data["thread"].clone();
        assert_eq!(summary["count"], 2);
        assert_eq!(summary["authors"], json!(["user", "a"]));

        // Threads are read on their own (Slack's): reading the main chat leaves the thread's reply unread.
        let unread = || room.hub.bot_value_for_test(&room.group)["unread"].as_i64().unwrap();
        assert_eq!(summary["unread"], 1);
        assert_eq!(unread(), 2, "the main chat's reply and the thread's");
        let mark = |b: serde_json::Value| crate::api::dispatch(&room.hub, &Caller::Local, "markRead", b);
        mark(json!({"botId": room.group})).await.unwrap();
        assert_eq!(unread(), 1);
        mark(json!({"botId": room.group, "threadId": first.id})).await.unwrap();
        assert_eq!(unread(), 0);
        assert_eq!(room.hub.store.entry(&first.id).unwrap().data["thread"]["unread"], 0);
        assert!(mark(json!({"botId": "a", "threadId": first.id})).await.is_err(), "a thread of another chat");

        // Reactions toggle, and reacting to a read message doesn't make it unread.
        let reply = room.hub.store.messages_after(&Lane::main(&room.group), 0, 100).unwrap();
        let reply = reply.iter().find(|e| e.kind == "agent" && e.data["final"] == true).unwrap();
        let react = |emoji: &str| {
            crate::api::dispatch(&room.hub, &Caller::Local, "react", json!({"entryId": reply.id, "emoji": emoji}))
        };
        react("👍").await.unwrap();
        let reacted = react("✅").await.unwrap();
        assert_eq!(reacted["entry"]["data"]["reactions"], json!(["👍", "✅"]));
        assert_eq!(unread(), 0);
        let undone = react("👍").await.unwrap();
        assert_eq!(undone["entry"]["data"]["reactions"], json!(["✅"]));
        assert!(react("hi").await.is_err(), "only emoji");

        // A thread in a bot's own chat gets a session of its own, told where it branched off.
        let dm = crate::api::dispatch(&room.hub, &Caller::Local, "send", json!({"botId": "b", "text": "main"}))
            .await
            .unwrap();
        room.settle().await;
        let dm_root = dm["entry"]["id"].as_str().unwrap().to_owned();
        crate::api::dispatch(
            &room.hub,
            &Caller::Local,
            "send",
            json!({"botId": "b", "text": "aside", "threadId": dm_root}),
        )
        .await
        .unwrap();
        room.settle().await;
        let prompts = std::fs::read_to_string(room.dir.join("b/prompts.jsonl")).unwrap();
        assert!(prompts.lines().last().unwrap().contains("[Thread]"), "{prompts}");
        let replies = room.hub.store.thread("b", &dm_root, 10).unwrap();
        assert_eq!(replies.iter().filter(|e| e.data["final"] == true).count(), 1);
        assert!(
            crate::api::dispatch(
                &room.hub,
                &Caller::Local,
                "send",
                json!({"botId": "b", "text": "x", "threadId": replies[0].id})
            )
            .await
            .is_err(),
            "threads are flat"
        );
        room.shutdown().await;
    }

    #[tokio::test]
    async fn stop_ends_the_room_turn_before_the_next_speaker() {
        let room = Room::new();
        room.send("BLOCK everyone look", None).await;
        let hub = room.hub.clone();
        tokio::time::timeout(Duration::from_secs(10), async {
            while hub.runtime("a").status != BotStatus::Working {
                tokio::time::sleep(Duration::from_millis(20)).await;
            }
        })
        .await
        .expect("a starts");
        crate::api::dispatch(&room.hub, &Caller::Local, "stop", json!({"botId": room.group})).await.unwrap();
        room.settle().await;
        tokio::time::sleep(Duration::from_millis(500)).await;
        assert!(!room.dir.join("b/prompts.jsonl").exists(), "b never got a turn after Stop");
        assert!(room.replies(&Lane::main(&room.group)).iter().all(|(_, t)| t != "reply: group"));
        room.shutdown().await;
    }

    #[test]
    fn pass_is_recognized_loosely() {
        for t in ["(pass)", " (pass) ", "\"(pass)\"", "pass.", ""] {
            assert!(is_pass(t), "{t:?}");
        }
        assert!(!is_pass("I'll pass this to Bob"));
    }
}
