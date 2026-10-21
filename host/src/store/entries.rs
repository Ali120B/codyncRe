//! The `entries` table: the transcript of every chat and thread.

use super::model::{Entry, EntryKind, Lane, ReadScope, SearchHit, Snippet};
use super::{Store, logged, next_rev, now_ms};
use crate::LockExt;
use anyhow::{Result, ensure};
use rusqlite::{OptionalExtension, params};
use serde_json::Value;
use std::fmt::Write as _;

/// How much text a search hit shows on each side of its match.
const SNIPPET_RADIUS: usize = 88;

/// The first occurrence of any term in `text` (ASCII case-insensitive) as a window of
/// `before` / matched / `after`. A term hidden by non-ASCII casing shows the head instead.
fn first_match(text: &str, terms: &[String]) -> (String, String, String) {
    let lower = text.to_lowercase();
    // `lower()` may change byte lengths (İ, ß…); then its offsets don't map back to `text`.
    let found = (lower.len() == text.len())
        .then(|| terms.iter().filter_map(|t| lower.find(t).map(|at| (at, t.len()))).min_by_key(|(at, _)| *at))
        .flatten();
    let (at, end) = found.map_or((0, 0), |(at, len)| (at, at + len));
    // Character-safe windows: `at`/`end` sit on term boundaries, the radius may not.
    let from = (0..=at.saturating_sub(SNIPPET_RADIUS)).rev().find(|&i| text.is_char_boundary(i)).unwrap_or(0);
    let to = (end + SNIPPET_RADIUS..=text.len()).find(|&i| text.is_char_boundary(i)).unwrap_or(text.len());
    let mut before = text[from..at].to_owned();
    if from > 0 {
        before.insert(0, '…');
    }
    let mut after = text[end..to].to_owned();
    if to < text.len() {
        after.push('…');
    }
    (before, text[at..end].to_owned(), after)
}

fn row_entry(r: &rusqlite::Row) -> rusqlite::Result<Entry> {
    let data: String = r.get(6)?;
    Ok(Entry {
        seq: r.get(0)?,
        id: r.get(1)?,
        bot_id: r.get(2)?,
        rev: r.get(3)?,
        kind: r.get(4)?,
        turn: r.get(5)?,
        data: serde_json::from_str(&data).unwrap_or(Value::Null),
        created_at: r.get(7)?,
        updated_at: r.get(8)?,
        thread_id: r.get(9)?,
    })
}

const ENTRY_COLS: &str = "seq, id, bot_id, rev, kind, turn, data, created_at, updated_at, thread_id";
/// A bot still winding down after its deletion can't leave lines behind (`?2` is the chat).
const LIVE_CHAT: &str = "WHERE NOT EXISTS (SELECT 1 FROM bots WHERE id = ?2 AND deleted = 1)";

impl Store {
    pub fn insert_entry(&self, lane: &Lane, kind: EntryKind, turn: i64, data: &Value) -> Result<Entry> {
        let c = self.db.locked();
        let rev = next_rev(&c)?;
        let now = now_ms();
        let id = uuid::Uuid::new_v4().to_string();
        let added = c.execute(
            &format!(
                "INSERT INTO entries(id, bot_id, thread_id, rev, kind, turn, data, created_at, updated_at)
                 SELECT ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?8 {LIVE_CHAT}"
            ),
            params![id, lane.chat, lane.thread, rev, kind.as_str(), turn, data.to_string(), now],
        )?;
        ensure!(added == 1, "the chat was deleted");
        Ok(Entry {
            id,
            seq: c.last_insert_rowid(),
            bot_id: lane.chat.clone(),
            thread_id: lane.thread.clone(),
            rev,
            kind: kind.as_str().to_owned(),
            turn,
            data: data.clone(),
            created_at: now,
            updated_at: now,
        })
    }

    /// Deterministic IDs make routine transcript publication retryable after a crash.
    pub fn insert_entry_once(&self, id: &str, lane: &Lane, kind: EntryKind, data: &Value) -> Result<(Entry, bool)> {
        let mut c = self.db.locked();
        let tx = c.transaction()?;
        if let Some(entry) =
            tx.query_row(&format!("SELECT {ENTRY_COLS} FROM entries WHERE id = ?"), [id], row_entry).optional()?
        {
            return Ok((entry, false));
        }
        let rev = next_rev(&tx)?;
        let now = now_ms();
        let added = tx.execute(&format!("INSERT INTO entries(id, bot_id, thread_id, rev, kind, turn, data, created_at, updated_at) SELECT ?1, ?2, ?3, ?4, ?5, 0, ?6, ?7, ?7 {LIVE_CHAT}"),
            params![id, lane.chat, lane.thread, rev, kind.as_str(), data.to_string(), now])?;
        ensure!(added == 1, "the chat was deleted");
        let entry = tx.query_row(&format!("SELECT {ENTRY_COLS} FROM entries WHERE id = ?"), [id], row_entry)?;
        tx.commit()?;
        Ok((entry, true))
    }

    pub fn update_entry(&self, id: &str, data: &Value) -> Result<Option<Entry>> {
        let c = self.db.locked();
        let rev = next_rev(&c)?;
        c.execute(
            "UPDATE entries SET rev = ?2, data = ?3, updated_at = ?4 WHERE id = ?1",
            params![id, rev, data.to_string(), now_ms()],
        )?;
        Ok(c.query_row(&format!("SELECT {ENTRY_COLS} FROM entries WHERE id = ?1"), [id], row_entry).optional()?)
    }

    pub fn entry(&self, id: &str) -> Option<Entry> {
        let c = self.db.locked();
        let row = c.query_row(&format!("SELECT {ENTRY_COLS} FROM entries WHERE id = ?1"), [id], row_entry);
        logged("entry", row.optional()).flatten()
    }

    pub fn find_by_nonce(&self, bot_id: &str, nonce: &str) -> Option<Entry> {
        let c = self.db.locked();
        let row = c.query_row(
            &format!(
                "SELECT {ENTRY_COLS} FROM entries WHERE bot_id = ?1 AND kind = 'user' AND json_extract(data, '$.clientNonce') = ?2"
            ),
            params![bot_id, nonce],
            row_entry,
        );
        logged("entry by nonce", row.optional()).flatten()
    }

    pub fn max_turn(&self, bot_id: &str) -> i64 {
        let c = self.db.locked();
        let n = c.query_row("SELECT COALESCE(MAX(turn), 0) FROM entries WHERE bot_id = ?1", [bot_id], |r| r.get(0));
        logged("max turn", n).unwrap_or(0)
    }

    /// Entries changed after `since`. A fresh client (`since == 0`) only gets the
    /// newest `cap` entries per bot and pages older ones in with `history`.
    pub fn entries_since(&self, since: i64, cap: i64) -> Result<Vec<Entry>> {
        let c = self.db.locked();
        let sql = if since == 0 {
            format!(
                "SELECT {ENTRY_COLS} FROM (SELECT *, ROW_NUMBER() OVER (PARTITION BY bot_id ORDER BY seq DESC) AS rn FROM entries)
                 WHERE rn <= ?2 AND rev > ?1 ORDER BY seq"
            )
        } else {
            format!("SELECT {ENTRY_COLS} FROM entries WHERE rev > ?1 AND ?2 > 0 ORDER BY seq")
        };
        let mut st = c.prepare(&sql)?;
        Ok(st.query_map(params![since, cap], row_entry)?.collect::<rusqlite::Result<_>>()?)
    }

    /// Older main-chat entries (threads load whole with [`Self::thread`]).
    pub fn history(&self, bot_id: &str, before_seq: i64, limit: i64) -> Result<Vec<Entry>> {
        let c = self.db.locked();
        let mut st = c.prepare(&format!(
            "SELECT {ENTRY_COLS} FROM entries WHERE bot_id = ?1 AND thread_id IS NULL AND seq < ?2
             ORDER BY seq DESC LIMIT ?3"
        ))?;
        let mut rows: Vec<Entry> =
            st.query_map(params![bot_id, before_seq, limit], row_entry)?.collect::<rusqlite::Result<_>>()?;
        rows.reverse();
        Ok(rows)
    }

    /// Cards still waiting for the user to sign in to agent `backend`, oldest first.
    // ponytail: scans notices without an index; sign-ins are rare, add one if it shows up in profiles.
    pub fn pending_sign_ins(&self, backend: &str) -> Result<Vec<Entry>> {
        let c = self.db.locked();
        let mut st = c.prepare(&format!(
            "SELECT {ENTRY_COLS} FROM entries WHERE kind = 'notice'
               AND json_extract(data, '$.connectionRequest.kind') = 'agent'
               AND json_extract(data, '$.connectionRequest.status') = 'pending'
               AND json_extract(data, '$.connectionRequest.backend') = ?1
             ORDER BY rev"
        ))?;
        Ok(st.query_map([backend], row_entry)?.collect::<rusqlite::Result<_>>()?)
    }

    /// Chat-visible messages (the user's, final replies) in any of `bot_id`'s lanes that
    /// contain every term (case-insensitive for ASCII), newest first.
    // ponytail: substring scan, no index (CJK needs no tokenizer this way); an FTS5 trigram table if it gets slow.
    pub fn search_messages(&self, bot_id: &str, terms: &[String], limit: i64) -> Result<Vec<Entry>> {
        let mut sql = format!(
            "SELECT {ENTRY_COLS} FROM entries WHERE bot_id = ?1 AND
               (kind = 'user' OR (kind = 'agent' AND json_extract(data, '$.final') = 1))"
        );
        for i in 0..terms.len() {
            let _ = write!(sql, " AND instr(lower(json_extract(data, '$.text')), ?{}) > 0", i + 3);
        }
        sql.push_str(" ORDER BY seq DESC LIMIT ?2");
        let mut args: Vec<rusqlite::types::Value> = vec![bot_id.to_owned().into(), limit.into()];
        args.extend(terms.iter().map(|t| t.to_lowercase().into()));
        let c = self.db.locked();
        let mut st = c.prepare(&sql)?;
        Ok(st.query_map(rusqlite::params_from_iter(args), row_entry)?.collect::<rusqlite::Result<_>>()?)
    }

    /// Message hits for the sidebar's search across every bot and group: chat-visible
    /// messages (`bot_id` not deleted) containing every term, newest first. `terms` are
    /// already lowercased; `limit` bounds the result, one hit per message.
    // ponytail: substring scan like `search_messages`; an FTS5 trigram table if it gets slow.
    pub fn search_hits(&self, terms: &[String], limit: i64) -> Result<Vec<SearchHit>> {
        if terms.is_empty() {
            return Ok(Vec::new());
        }
        let mut sql = String::from(
            "SELECT e.id, e.bot_id, e.thread_id, e.kind, json_extract(e.data, '$.author'),
                    json_extract(e.data, '$.text'), e.created_at, json_extract(b.config, '$.name')
             FROM entries e JOIN bots b ON b.id = e.bot_id
             WHERE b.deleted = 0 AND json_type(e.data, '$.text') = 'text'
               AND (e.kind = 'user' OR (e.kind = 'agent' AND json_extract(e.data, '$.final') = 1))",
        );
        for i in 0..terms.len() {
            let _ = write!(sql, " AND instr(lower(json_extract(e.data, '$.text')), ?{}) > 0", i + 2);
        }
        sql.push_str(" ORDER BY e.seq DESC LIMIT ?1");
        let mut args: Vec<rusqlite::types::Value> = vec![limit.into()];
        args.extend(terms.iter().cloned().map(Into::into));
        let c = self.db.locked();
        let mut st = c.prepare(&sql)?;
        let rows = st.query_map(rusqlite::params_from_iter(args), |r| {
            let text: String = r.get(5)?;
            let (before, matched, after) = first_match(&text, terms);
            Ok(SearchHit {
                entry_id: r.get(0)?,
                bot_id: r.get(1)?,
                thread_id: r.get(2)?,
                kind: r.get(3)?,
                author: r.get(4)?,
                snippet: Snippet { before, matched, after },
                at: r.get(6)?,
                bot_name: r.get(7)?,
            })
        })?;
        Ok(rows.collect::<rusqlite::Result<_>>()?)
    }

    /// A thread's newest `limit` entries, oldest first.
    pub fn thread(&self, bot_id: &str, root: &str, limit: i64) -> Result<Vec<Entry>> {
        let c = self.db.locked();
        let mut st = c.prepare(&format!(
            "SELECT {ENTRY_COLS} FROM entries WHERE bot_id = ?1 AND thread_id = ?2 ORDER BY seq DESC LIMIT ?3"
        ))?;
        let mut rows: Vec<Entry> =
            st.query_map(params![bot_id, root, limit], row_entry)?.collect::<rusqlite::Result<_>>()?;
        rows.reverse();
        Ok(rows)
    }

    /// The newest `limit` bot-to-bot notices in `bot_id`'s main chat that involve `peer_id`, oldest first.
    pub fn bot_conversation(&self, bot_id: &str, peer_id: &str, limit: i64) -> Result<Vec<Entry>> {
        let c = self.db.locked();
        let mut st = c.prepare(&format!(
            "SELECT {ENTRY_COLS} FROM entries WHERE bot_id = ?1 AND thread_id IS NULL AND kind = 'notice'
               AND json_type(data, '$.botMessage') = 'object'
               AND ?2 IN (json_extract(data, '$.botMessage.sourceBotId'), json_extract(data, '$.botMessage.targetBotId'))
             ORDER BY seq DESC LIMIT ?3"
        ))?;
        let mut rows: Vec<Entry> =
            st.query_map(params![bot_id, peer_id, limit], row_entry)?.collect::<rusqlite::Result<_>>()?;
        rows.reverse();
        Ok(rows)
    }

    /// Chat-visible messages (the user's, final replies) in `lane` after `after_seq`, oldest first.
    pub fn messages_after(&self, lane: &Lane, after_seq: i64, limit: i64) -> Result<Vec<Entry>> {        let c = self.db.locked();
        let mut st = c.prepare(&format!(
            "SELECT {ENTRY_COLS} FROM entries WHERE bot_id = ?1 AND thread_id IS ?2 AND seq > ?3 AND
               (kind = 'user' OR (kind = 'agent' AND json_extract(data, '$.final') = 1))
             ORDER BY seq DESC LIMIT ?4"
        ))?;
        let mut rows: Vec<Entry> = st
            .query_map(params![lane.chat, lane.thread, after_seq, limit], row_entry)?
            .collect::<rusqlite::Result<_>>()?;
        rows.reverse();
        Ok(rows)
    }

    /// The `seq` of `bot`'s last reply in `lane` (0 if it hasn't spoken there).
    pub fn last_spoke(&self, lane: &Lane, bot: &str) -> i64 {
        let c = self.db.locked();
        let n = c.query_row(
            "SELECT COALESCE(MAX(seq), 0) FROM entries WHERE bot_id = ?1 AND thread_id IS ?2 AND kind = 'agent'
               AND json_extract(data, '$.final') = 1 AND json_extract(data, '$.author') = ?3",
            params![lane.chat, lane.thread, bot],
            |r| r.get(0),
        );
        logged("last spoke", n).unwrap_or(0)
    }

    /// A thread's reply count, newest reply time, the bots that replied (first reply first)
    /// and how many replies are unread.
    pub fn thread_summary(&self, chat: &str, root: &str) -> Result<Value> {
        let read_rev = self.bot(chat)?.map_or(0, |r| r.read_rev);
        let unread = self.unread(chat, read_rev, ReadScope::Thread(root));
        let c = self.db.locked();
        let mut st = c.prepare(
            "SELECT kind, json_extract(data, '$.author'), created_at FROM entries WHERE bot_id = ?1 AND thread_id = ?2
               AND (kind = 'user' OR (kind = 'agent' AND json_extract(data, '$.final') = 1)) ORDER BY seq",
        )?;
        let rows = st.query_map(params![chat, root], |r| {
            Ok((r.get::<_, String>(0)?, r.get::<_, Option<String>>(1)?, r.get::<_, i64>(2)?))
        })?;
        let (mut count, mut last_at, mut authors) = (0, 0, Vec::<String>::new());
        for row in rows {
            let (kind, author, at) = row?;
            count += 1;
            last_at = at;
            let author = if kind == EntryKind::User.as_str() { "user".to_owned() } else { author.unwrap_or_default() };
            if !author.is_empty() && !authors.contains(&author) {
                authors.push(author);
            }
        }
        Ok(serde_json::json!({"count": count, "lastAt": last_at, "authors": authors, "unread": unread}))
    }

    /// Permission cards left `pending` (and sends left `queued`) by a crash or restart
    /// can never be honored; returns how many were closed out.
    pub fn expire_pending(&self) -> Result<usize> {
        let ids: Vec<String> = {
            let c = self.db.locked();
            let mut st = c.prepare(
                "SELECT id FROM entries WHERE (kind = 'permission' AND json_extract(data, '$.status') = 'pending')
                  OR (kind = 'user' AND json_extract(data, '$.status') = 'queued')
                  OR (kind = 'notice' AND json_extract(data, '$.delegationId') IS NOT NULL
                      AND json_extract(data, '$.status') IN ('queued', 'sent'))",
            )?;
            st.query_map([], |r| r.get(0))?.collect::<rusqlite::Result<_>>()?
        };
        for id in &ids {
            if let Some(mut e) = self.entry(id) {
                let status = if e.kind == EntryKind::Notice.as_str() {
                    let heading = e.data["heading"].as_str().unwrap_or("Bot request");
                    e.data["text"] = format!(
                        "{heading}\nInterrupted by host restart. Partial work may have happened; check before retrying."
                    )
                    .into();
                    if let Some(message) = e.data.get_mut("botMessage").and_then(Value::as_object_mut) {
                        message.insert(
                            "detail".into(),
                            "Interrupted by host restart. Partial work may have happened; check before retrying."
                                .into(),
                        );
                    }
                    e.data["style"] = "error".into();
                    "failed"
                } else if e.kind == EntryKind::User.as_str() {
                    "failed"
                } else {
                    "expired"
                };
                e.data["status"] = status.into();
                self.update_entry(id, &e.data)?;
            }
        }
        Ok(ids.len())
    }

    /// Every entry in `lane` after `after_seq`, including tool calls and thoughts (oldest first).
    pub fn lane_entries_after(&self, lane: &Lane, after_seq: i64, limit: i64) -> Result<Vec<Entry>> {
        let c = self.db.locked();
        let mut st = c.prepare(&format!(
            "SELECT {ENTRY_COLS} FROM entries WHERE bot_id = ?1 AND thread_id IS ?2 AND seq > ?3
             ORDER BY seq LIMIT ?4"
        ))?;
        Ok(st
            .query_map(params![lane.chat, lane.thread, after_seq, limit.clamp(1, 5000)], row_entry)?
            .collect::<rusqlite::Result<_>>()?)
    }

    /// Deletes entries and leaves durable tombstones, so clients that reconnect don't keep
    /// messages the user rewound. One revision covers the batch.
    pub fn delete_entries(&self, ids: &[String]) -> Result<(i64, Vec<Entry>)> {
        let mut c = self.db.locked();
        let tx = c.transaction()?;
        let mut entries = Vec::new();
        for id in ids {
            if let Some(entry) =
                tx.query_row(&format!("SELECT {ENTRY_COLS} FROM entries WHERE id = ?1"), [id], row_entry).optional()?
            {
                entries.push(entry);
            }
        }
        if entries.is_empty() {
            tx.commit()?;
            return Ok((0, entries));
        }
        let rev = next_rev(&tx)?;
        let now = now_ms();
        for entry in &entries {
            tx.execute("DELETE FROM entries WHERE id = ?1", [&entry.id])?;
            tx.execute(
                "INSERT INTO entry_tombstones(id, bot_id, rev, deleted_at) VALUES(?1, ?2, ?3, ?4)
                 ON CONFLICT(id) DO UPDATE SET rev = ?3, deleted_at = ?4",
                params![entry.id, entry.bot_id, rev, now],
            )?;
        }
        tx.commit()?;
        Ok((rev, entries))
    }

    /// Tombstones after `since`, oldest revision first: `(rev, bot_id, entry_id)`.
    pub fn tombstones_since(&self, since: i64) -> Result<Vec<(i64, String, String)>> {
        let c = self.db.locked();
        let mut st = c.prepare("SELECT rev, bot_id, id FROM entry_tombstones WHERE rev > ?1 ORDER BY rev")?;
        Ok(st
            .query_map([since], |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)))?
            .collect::<rusqlite::Result<_>>()?)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::store::tests::temp_store;
    use serde_json::json;

    #[test]
    fn history_search_matches_every_term_in_chat_messages() {
        let s = temp_store();
        let main = Lane::main("b1");
        s.insert_entry(&main, EntryKind::User, 1, &json!({"text": "我們用 Swift 6 寫 Codync"})).unwrap();
        s.insert_entry(&main, EntryKind::Agent, 1, &json!({"text": "swift narration", "final": false})).unwrap();
        s.insert_entry(&main, EntryKind::Agent, 1, &json!({"text": "Swift 6 strict mode is on", "final": true}))
            .unwrap();
        s.insert_entry(&Lane::main("b2"), EntryKind::User, 1, &json!({"text": "Swift 6 elsewhere"})).unwrap();
        let texts = |terms: &[&str]| -> Vec<String> {
            let terms: Vec<String> = terms.iter().map(|t| (*t).to_owned()).collect();
            s.search_messages("b1", &terms, 10)
                .unwrap()
                .into_iter()
                .map(|e| e.data["text"].as_str().unwrap().to_owned())
                .collect()
        };
        assert_eq!(texts(&["SWIFT", "6"]), ["Swift 6 strict mode is on", "我們用 Swift 6 寫 Codync"]);
        assert_eq!(texts(&["codync", "寫"]), ["我們用 Swift 6 寫 Codync"]);
        assert_eq!(texts(&["narration"]).len(), 0);
    }

    #[test]
    fn restart_expires_pending_cards_and_queued_sends() {
        let s = temp_store();
        s.insert_entry(&Lane::main("b1"), EntryKind::Permission, 1, &serde_json::json!({"status": "pending"})).unwrap();
        let q =
            s.insert_entry(&Lane::main("b1"), EntryKind::User, 2, &serde_json::json!({"status": "queued"})).unwrap();
        s.insert_entry(&Lane::main("b1"), EntryKind::User, 1, &serde_json::json!({"status": "sent"})).unwrap();
        assert_eq!(s.expire_pending().unwrap(), 2);
        assert_eq!(s.entry(&q.id).unwrap().data["status"], "failed");
        assert_eq!(s.expire_pending().unwrap(), 0, "idempotent");
    }

    #[test]
    fn search_hits_span_chats_and_threads_newest_first() {
        use crate::store::tests::test_bot;
        let s = temp_store();
        s.save_bot(&test_bot("b1", "Dawg")).unwrap();
        s.save_bot(&test_bot("g1", "Team")).unwrap();
        s.save_bot(&test_bot("dead", "Gone")).unwrap();
        let main = Lane::main("b1");
        let user = s.insert_entry(&main, EntryKind::User, 1, &json!({"text": "ship the Swift 6 release"})).unwrap();
        s.insert_entry(&main, EntryKind::Agent, 1, &json!({"text": "Swift narration", "final": false})).unwrap();
        s.insert_entry(&main, EntryKind::Agent, 2, &json!({"text": "Swift 6 shipped", "final": true})).unwrap();
        s.insert_entry(
            &Lane::in_thread("b1", &user.id),
            EntryKind::User,
            2,
            &json!({"text": "swift follow-up in a thread"}),
        )
        .unwrap();
        s.insert_entry(
            &Lane::main("g1"),
            EntryKind::Agent,
            1,
            &json!({"text": "SWIFT from the group", "final": true, "author": "m1"}),
        )
        .unwrap();
        s.insert_entry(&Lane::main("dead"), EntryKind::User, 1, &json!({"text": "swift in a deleted chat"})).unwrap();
        s.delete_bot("dead").unwrap();

        let hit = |terms: &[&str], limit: i64| {
            let terms: Vec<String> = terms.iter().map(|t| (*t).to_owned()).collect();
            s.search_hits(&terms, limit).unwrap()
        };
        let six = hit(&["swift", "6"], 40);
        let texts: Vec<&str> = six.iter().map(|h| h.snippet.matched.as_str()).collect();
        assert_eq!(texts, ["Swift", "Swift"], "newest first, every term required");
        assert_eq!(six[0].bot_name, "Dawg");
        assert_eq!(six[0].kind, "agent");
        assert_eq!(six[0].author, None, "a main-chat bot reply carries no author");

        let group = hit(&["swift", "group"], 40);
        assert_eq!(group.len(), 1);
        assert_eq!(group[0].bot_id, "g1");
        assert_eq!(group[0].author.as_deref(), Some("m1"));
        assert_eq!(
            format!("{}{}{}", group[0].snippet.before, group[0].snippet.matched, group[0].snippet.after),
            "SWIFT from the group"
        );

        let thread = hit(&["follow-up"], 40);
        assert_eq!(thread.len(), 1);
        assert_eq!(thread[0].thread_id.as_deref(), Some(user.id.as_str()));
        assert_eq!(hit(&[], 40).len(), 0, "an empty query matches nothing");
        assert_eq!(hit(&["swift"], 1).len(), 1, "the limit keeps the newest");
        assert!(hit(&["swift"], 10).iter().all(|h| h.bot_id != "dead"), "deleted chats stay out");
    }

    #[test]
    fn search_snippets_trim_with_ellipses_and_stay_on_char_boundaries() {
        let s = temp_store();
        s.save_bot(&crate::store::tests::test_bot("b1", "Dawg")).unwrap();
        let long = format!("{} needle {}", "x".repeat(300), "y".repeat(300));
        s.insert_entry(&Lane::main("b1"), EntryKind::User, 1, &json!({"text": long})).unwrap();
        let hits = s.search_hits(&["needle".to_owned()], 10).unwrap();
        assert_eq!(hits.len(), 1);
        let snippet = &hits[0].snippet;
        assert_eq!(snippet.matched, "needle");
        assert!(snippet.before.starts_with('…') && snippet.after.ends_with('…'));
        assert!(snippet.before.len() <= SNIPPET_RADIUS + '…'.len_utf8());
        // CJK text at the window edge must not split a character.
        let cjk = format!("{}{}{}", "寫".repeat(100), "needle", "好".repeat(100));
        s.insert_entry(&Lane::main("b1"), EntryKind::User, 2, &json!({"text": cjk})).unwrap();
        let hits = s.search_hits(&["needle".to_owned()], 1).unwrap();
        assert_eq!(hits[0].snippet.matched, "needle");
    }

    #[test]
    fn bot_conversation_lists_one_peers_notices_oldest_first() {
        let s = temp_store();
        let notice = |bot: &str, source: &str, target: &str, text: &str| {
            let data = json!({"text": text, "sourceBotId": source, "targetBotId": target,
                "botMessage": {"sourceBotId": source, "targetBotId": target, "text": text}});
            s.insert_entry(&Lane::main(bot), EntryKind::Notice, 1, &data).unwrap();
        };
        notice("a", "a", "b", "first");
        notice("a", "a", "c", "other peer");
        notice("a", "b", "a", "second");
        notice("b", "a", "b", "other chat");
        s.insert_entry(&Lane::main("a"), EntryKind::Notice, 1, &json!({"text": "plain"})).unwrap();
        let old = s
            .insert_entry(
                &Lane::main("a"),
                EntryKind::Notice,
                1,
                &json!({"text": "Messaged b: old", "heading": "Messaged b: old", "delegationId": "old",
                "sourceBotId": "a", "targetBotId": "b", "status": "completed"}),
            )
            .unwrap();
        let old_data = old.data.clone();
        let texts: Vec<_> = s
            .bot_conversation("a", "b", 10)
            .unwrap()
            .into_iter()
            .map(|e| e.data["text"].as_str().unwrap().to_owned())
            .collect();
        assert_eq!(texts, ["first", "second"]);
        assert_eq!(s.entry(&old.id).unwrap().data, old_data, "existing notice is untouched");
        assert_eq!(s.bot_conversation("a", "b", 1).unwrap().len(), 1, "limit keeps the newest");
    }
}
