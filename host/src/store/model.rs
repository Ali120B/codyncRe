//! Row and wire types: bot configs, lanes, entries and their kinds.

use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::BTreeMap;

/// How a bot handles the agent's permission requests (wire values `ask` / `auto` / `full`).
#[derive(Serialize, Deserialize, Clone, Copy, Debug, Default, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum Permission {
    /// Every request becomes an approval card on the phone.
    Ask,
    /// Requests are approved once, automatically. New bots start here.
    #[default]
    Auto,
    /// Full computer access: every request is approved without cards (elevated risk).
    Full,
}

/// Transcript entry kinds (the `kind` column / wire field).
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum EntryKind {
    User,
    Agent,
    Thought,
    Tool,
    Plan,
    Permission,
    Notice,
}

impl EntryKind {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::User => "user",
            Self::Agent => "agent",
            Self::Thought => "thought",
            Self::Tool => "tool",
            Self::Plan => "plan",
            Self::Permission => "permission",
            Self::Notice => "notice",
        }
    }
}

/// What one member may use inside a group. Both off until the group switches them on.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MemberSettings {
    #[serde(default)]
    pub browser: bool,
    #[serde(default)]
    pub computer: bool,
}

/// A roster row is an agent bot or a group chat of bots (wire values `agent` / `group`).
#[derive(Serialize, Deserialize, Clone, Copy, Debug, Default, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum BotKind {
    #[default]
    Agent,
    /// Several bots and the user in one conversation; it has no agent of its own (see `group`).
    Group,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
#[expect(clippy::struct_excessive_bools, reason = "independent user settings stored as plain JSON flags")]
pub struct BotConfig {
    pub id: String,
    #[serde(default)]
    pub kind: BotKind,
    /// A group's bots, in the order they were added.
    #[serde(default)]
    pub members: Vec<String>,
    /// A group's leader: answers first and hands work to the others. Empty = no leader yet.
    #[serde(default)]
    pub leader: String,
    /// Rules the whole group follows, beside each bot's own standing instructions.
    #[serde(default)]
    pub instructions: String,
    /// A group's shared folder. Empty = every member keeps its own workspace.
    #[serde(default)]
    pub workspace: String,
    /// What each member may use inside this room (browser, computer control). The
    /// bot's own chats keep the bot's own settings; these only gate room turns.
    #[serde(default)]
    pub member_settings: BTreeMap<String, MemberSettings>,
    pub name: String,
    #[serde(default)]
    pub description: String,
    #[serde(default = "default_color")]
    pub avatar_color: String,
    /// Grok-Bot-style character shape: blob | pebble | squircle | tablet | wedge | hex | cloud | teardrop.
    #[serde(default = "default_shape")]
    pub avatar_shape: String,
    /// Empty for a group.
    #[serde(default)]
    pub backend: String,
    #[serde(default)]
    pub command: Option<String>,
    /// Empty for a group.
    #[serde(default)]
    pub cwd: String,
    #[serde(default)]
    pub permission: Permission,
    /// Command prefixes this bot may run without asking (the Command allowlist), for `ask` bots.
    #[serde(default)]
    pub allowlist: Vec<String>,
    #[serde(default)]
    pub model: Option<String>,
    /// The model's own settings (effort, fast…): ACP config option id → value.
    #[serde(default)]
    pub model_options: BTreeMap<String, String>,
    /// Ask the agent for its Fast mode (Claude Code, Codex): faster replies for more usage.
    #[serde(default)]
    pub fast_mode: bool,
    #[serde(default)]
    pub pinned: bool,
    /// Place in the roster, lowest first (after pinned bots). All 0 until the user drags one:
    /// ties sort by recent activity (see `Hub::reorder_bots`).
    #[serde(default)]
    pub position: i64,
    #[serde(default)]
    pub hidden: bool,
    #[serde(default)]
    pub notify: Option<bool>,
    /// Connector ids (MCP servers) handed to the agent; see `market`.
    #[serde(default)]
    pub connectors: Vec<String>,
    /// Skill ids the bot is told about; see `market`.
    #[serde(default)]
    pub skills: Vec<String>,
    /// The bot gets the built-in `computer` MCP server (see `screen`).
    #[serde(default)]
    pub computer: bool,
    /// The bot gets the built-in `browser` MCP server: its own headless Chrome/Firefox with
    /// its own profile (see `browser`).
    #[serde(default)]
    pub browser: bool,
    /// Which engine the bot's browser uses: empty picks automatically (Chrome, else Firefox);
    /// `chrome` / `firefox` pin it.
    #[serde(default)]
    pub browser_engine: String,
    /// Created without a name: the host names it from its first conversations (see `naming`)
    /// until the user renames it.
    #[serde(default)]
    pub auto_name: bool,
    #[serde(default)]
    pub created_at: i64,
}

#[cfg(test)]
mod member_tests {
    use super::MemberSettings;

    #[test]
    fn group_member_tools_start_off() {
        let settings: MemberSettings = serde_json::from_value(serde_json::json!({})).expect("defaults");
        assert!(!settings.browser && !settings.computer, "a new room member gets no browser or computer");
    }
}

impl BotConfig {
    pub fn is_group(&self) -> bool {
        self.kind == BotKind::Group
    }
}

fn default_color() -> String {
    "blue".into()
}
fn default_shape() -> String {
    "blob".into()
}

/// kv key for per-bot state in one lane (`what`: `session`, `seen`).
pub fn lane_key(what: &str, bot_id: &str, lane: &Lane) -> String {
    format!("lane.{what}.{bot_id}@{}", lane.key())
}

/// What the user has read (`markRead`): the main chat, one thread, or all of a chat.
#[derive(Clone, Copy, Debug)]
pub enum ReadScope<'a> {
    All,
    Chat,
    Thread(&'a str),
}

/// Where an entry lives: a bot's or group's chat (`bot_id`), and optionally a thread in it,
/// named by its root entry. Every lane a bot talks in has its own ACP session.
#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub struct Lane {
    pub chat: String,
    pub thread: Option<String>,
}

impl Lane {
    pub fn main(chat: &str) -> Self {
        Self { chat: chat.to_owned(), thread: None }
    }

    pub fn in_thread(chat: &str, root: &str) -> Self {
        Self { chat: chat.to_owned(), thread: Some(root.to_owned()) }
    }

    /// Stable text form for kv keys: `chat` or `chat/root`.
    pub fn key(&self) -> String {
        match &self.thread {
            Some(t) => format!("{}/{t}", self.chat),
            None => self.chat.clone(),
        }
    }
}

#[derive(Serialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct Entry {
    pub id: String,
    pub seq: i64,
    pub bot_id: String,
    /// The thread's root entry; `None` in the main chat.
    pub thread_id: Option<String>,
    pub rev: i64,
    pub kind: String,
    pub turn: i64,
    pub data: Value,
    pub created_at: i64,
    pub updated_at: i64,
}

pub struct LastMessage {
    pub text: String,
    pub at: i64,
    /// The bot that wrote it; `None` when the user did.
    pub author: Option<String>,
}

/// One sidebar search hit (wire `search`): a chat-visible message and where it lives.
#[derive(Serialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct SearchHit {
    pub entry_id: String,
    /// The chat (bot or group) the message is in.
    pub bot_id: String,
    pub bot_name: String,
    /// The thread the message is in; `None` in the main chat.
    pub thread_id: Option<String>,
    /// `user` or `agent`.
    pub kind: String,
    /// The bot that replied (a member of the group); `None` for the user's own messages.
    pub author: Option<String>,
    pub snippet: Snippet,
    pub at: i64,
}

/// A message's text around its first match; `before`/`after` carry a leading/trailing `…`
/// when the text was trimmed.
#[derive(Serialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct Snippet {
    pub before: String,
    pub matched: String,
    pub after: String,
}

pub struct BotRow {
    pub config: BotConfig,
    pub rev: i64,
    pub deleted: bool,
    pub session_id: Option<String>,
    pub read_rev: i64,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn permission_wire_values_round_trip() {
        let p: Permission = serde_json::from_str("\"auto\"").unwrap();
        assert_eq!(p, Permission::Auto);
        assert_eq!(serde_json::to_string(&Permission::Ask).unwrap(), "\"ask\"");
        assert_eq!(serde_json::from_str::<Permission>("\"full\"").unwrap(), Permission::Full);
        assert!(serde_json::from_str::<Permission>("\"yolo\"").is_err(), "unknown modes are rejected at the boundary");
    }
}
