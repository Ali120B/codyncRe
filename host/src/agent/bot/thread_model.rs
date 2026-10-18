//! Per-thread models: a thread can pin its own model; without one it follows the bot's.
//! Live sessions switch before their next turn (like Fast mode), and a session the agent
//! can't switch on starts fresh with the pinned model.

use super::{Actor, NoticeStyle};
use crate::store::{BotConfig, Lane, lane_key};
use serde_json::json;

impl Actor {
    /// The thread's pinned model, or `None` when it follows the bot's.
    pub(super) fn pinned_model(&self, root: Option<&str>) -> Option<String> {
        root.and_then(|root| self.hub.store.thread_model(&self.cfg.id, root))
    }

    /// The bot's config with the thread's pinned model applied, for session creation.
    pub(super) fn cfg_for_thread(&self, root: Option<&str>) -> BotConfig {
        let mut cfg = self.cfg.clone();
        if let Some(model) = self.pinned_model(root) {
            cfg.model = Some(model);
        }
        cfg
    }

    /// A live session switches to its thread's model (or back to the bot's) before a turn.
    pub(super) async fn sync_thread_model(&mut self, sid: &str, root: Option<&str>) {
        let desired = self.pinned_model(root).or_else(|| self.cfg.model.clone()).unwrap_or_default();
        if desired.is_empty() || self.model_on_session.get(sid).is_some_and(|current| *current == desired) {
            return;
        }
        match self.session_request("session/set_model", json!({"sessionId": sid, "modelId": desired})).await {
            Ok(_) => {
                self.model_on_session.insert(sid.to_owned(), desired);
            }
            Err(error) => {
                tracing::warn!(bot = %self.cfg.id, error = format!("{error:#}"), "couldn't switch a thread's model");
                if let Some(root) = root {
                    // No mid-session switching here: the thread restarts with its own model.
                    self.notice(
                        "This agent can't change models mid-session — this thread starts fresh with its own model.",
                        NoticeStyle::Info,
                    );
                    let key = lane_key("session", &self.cfg.id, &Lane::in_thread(&self.cfg.id, root));
                    self.set_session(Some(&key), None);
                }
            }
        }
    }
}
