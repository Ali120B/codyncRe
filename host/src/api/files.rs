use super::str_arg;
use crate::chat::files::{self, SharedFile};
use crate::hub::Hub;
use anyhow::{Context, Result, anyhow, ensure};
use base64::Engine as _;
use serde_json::{Value, json};
use std::path::{Path, PathBuf};
use std::sync::Arc;

/// The folder the Files tab browses: a bot works in its working folder; a group in its shared
/// folder (one it doesn't have works in its members').
fn workspace_root(cfg: &crate::store::BotConfig) -> Result<PathBuf> {
    if cfg.is_group() {
        ensure!(
            !cfg.workspace.is_empty(),
            "this group works in its members' own folders; open a member's chat to browse files"
        );
        return Ok(PathBuf::from(&cfg.workspace));
    }
    if cfg.cwd.is_empty() {
        return Ok(crate::agent::workspace::path(&cfg.id));
    }
    Ok(PathBuf::from(&cfg.cwd))
}

/// A path inside the workspace: relative, no `..`, and nothing that escapes it (symlinks included).
fn resolve(root: &Path, rel: &str) -> Result<PathBuf> {
    ensure!(!rel.split(['/', '\\']).any(|part| part == ".."), "invalid path");
    let root = root.canonicalize().with_context(|| format!("no such folder: {}", root.display()))?;
    let full = root.join(rel).canonicalize().with_context(|| format!("no such folder: {rel}"))?;
    ensure!(full.starts_with(&root), "that path is outside the bot's folder");
    Ok(full)
}

/// One directory of the bot's workspace, for the Computer panel's Files tab.
pub async fn list(hub: &Arc<Hub>, body: &Value) -> Result<Value> {
    let row = bot(hub, body)?;
    let root = workspace_root(&row.config)?;
    let rel = body["path"].as_str().unwrap_or_default().trim_matches('/').to_owned();
    let listed = tokio::task::spawn_blocking(move || list_dir(&root, &rel)).await??;
    Ok(json!({
        "path": listed.rel,
        "name": listed.name,
        "parent": listed.parent,
        "dirs": listed.dirs,
        "files": listed.files,
    }))
}

/// A chunk of one workspace file (base64), for previews.
pub async fn read_workspace(hub: &Arc<Hub>, body: &Value) -> Result<Value> {
    let row = bot(hub, body)?;
    let root = workspace_root(&row.config)?;
    let rel = str_arg(body, "path")?.to_owned();
    let offset = body["offset"].as_u64().unwrap_or(0);
    let (data, size) = tokio::task::spawn_blocking(move || read_chunk(&root, &rel, offset)).await??;
    Ok(json!({"data": base64::engine::general_purpose::STANDARD.encode(data), "size": size}))
}

fn bot(hub: &Arc<Hub>, body: &Value) -> Result<crate::store::BotRow> {
    let id = str_arg(body, "botId")?;
    hub.store.bot(id)?.filter(|r| !r.deleted).ok_or_else(|| anyhow!("unknown bot"))
}

struct Listing {
    rel: String,
    name: String,
    parent: Option<String>,
    dirs: Vec<Value>,
    files: Vec<Value>,
}

/// Blocking: one directory listing, folders first, noisy folders out.
fn list_dir(root: &Path, rel: &str) -> Result<Listing> {
    let full = resolve(root, rel)?;
    ensure!(full.is_dir(), "not a folder");
    let name = full.file_name().map_or_else(|| root.display().to_string(), |n| n.to_string_lossy().into_owned());
    let mut dirs = Vec::new();
    let mut files = Vec::new();
    for entry in std::fs::read_dir(&full)?.filter_map(Result::ok) {
        let entry_name = entry.file_name().to_string_lossy().into_owned();
        if matches!(entry_name.as_str(), ".git" | "node_modules" | "__pycache__") {
            continue;
        }
        let Ok(metadata) = entry.metadata() else { continue };
        let child = |rel: &str| {
            if rel.is_empty() { entry_name.clone() } else { format!("{rel}/{entry_name}") }
        };
        if metadata.is_dir() {
            dirs.push(json!({"name": entry_name, "path": child(rel)}));
        } else if metadata.is_file() {
            files.push(json!({
                "name": entry_name,
                "path": child(rel),
                "size": metadata.len(),
                "modified": metadata.modified().ok()
                    .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
                    .and_then(|d| i64::try_from(d.as_millis()).ok()),
            }));
        }
    }
    let key = |v: &Value| v["name"].as_str().unwrap_or_default().to_lowercase();
    dirs.sort_by_key(key);
    files.sort_by_key(key);
    let parent = if rel.is_empty() {
        None
    } else {
        Some(rel.rsplit_once('/').map_or(String::new(), |(head, _)| head.to_owned()))
    };
    Ok(Listing { rel: rel.to_owned(), name, parent, dirs, files })
}

/// Blocking: up to one chunk of a regular file inside the workspace.
fn read_chunk(root: &Path, rel: &str, offset: u64) -> Result<(Vec<u8>, u64)> {
    use std::io::{Read as _, Seek as _, SeekFrom};
    let path = resolve(root, rel)?;
    ensure!(path.is_file(), "not a file");
    let size = std::fs::metadata(&path)?.len();
    ensure!(offset <= size, "invalid file offset");
    let mut file = std::fs::File::open(&path)?;
    file.seek(SeekFrom::Start(offset))?;
    let length = usize::try_from((size - offset).min(files::CHUNK_SIZE as u64))?;
    let mut data = vec![0; length];
    file.read_exact(&mut data)?;
    Ok((data, size))
}

pub async fn read(hub: &Arc<Hub>, body: &Value) -> Result<Value> {
    let entry = hub.store.entry(str_arg(body, "entryId")?).ok_or_else(|| anyhow!("unknown file message"))?;
    ensure!(entry.kind == crate::store::EntryKind::Agent.as_str() && entry.data["final"] == true, "not a file message");
    let bot = entry.data["author"].as_str().ok_or_else(|| anyhow!("missing file author"))?;
    ensure!(entry.bot_id == bot, "not a bot chat file");
    ensure!(hub.store.bot(bot)?.is_some_and(|row| !row.deleted), "unknown bot");
    let id = str_arg(body, "fileId")?;
    let meta = entry.data["files"]
        .as_array()
        .and_then(|files| files.iter().find(|meta| meta["id"] == id))
        .ok_or_else(|| anyhow!("file does not belong to this message"))?;
    let meta: SharedFile = serde_json::from_value(meta.clone())?;
    let offset = body["offset"].as_u64().ok_or_else(|| anyhow!("offset must be a nonnegative integer"))?;
    let root = files::root(bot);
    let size = meta.size;
    let data = tokio::task::spawn_blocking(move || files::read(&root, &meta, offset)).await??;
    Ok(json!({"data": base64::engine::general_purpose::STANDARD.encode(data), "size": size}))
}
