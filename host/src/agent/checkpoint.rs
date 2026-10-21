//! Pre-turn workspace checkpoints for "rewind this turn".
//!
//! A git workspace is captured as a binary diff against HEAD plus its untracked files,
//! so a turn doesn't copy a whole repository. Non-git folders fall back to a bounded
//! tree copy. Restoring is deliberately explicit: the caller has already asked the user.

use crate::service;
use crate::store::Lane;
use anyhow::{Context, Result, ensure};
use serde_json::{Value, json};
use std::fs;
use std::path::{Path, PathBuf};

const KEEP_TURNS: usize = 30;
const KEEP_COPY_TURNS: usize = 5;
const MAX_PATCH: usize = 64 * 1024 * 1024;
const MAX_UNTRACKED: u64 = 128 * 1024 * 1024;
const MAX_COPY: u64 = 128 * 1024 * 1024;
const MAX_FILES: usize = 30_000;

/// Captures `cwd` for one bot in one turn. A group captures every member before its room turn.
pub async fn capture(chat: &str, lane: &Lane, turn: i64, bot: &str, cwd: &str) -> Result<()> {
    let (chat, lane, bot, cwd) = (chat.to_owned(), lane.clone(), bot.to_owned(), cwd.to_owned());
    tokio::task::spawn_blocking(move || capture_sync(&chat, &lane, turn, &bot, &cwd)).await?
}

/// Restores every member checkpoint for `chat`/`lane`/`turn`; returns how many bots were restored.
pub async fn restore(chat: &str, lane: &Lane, turn: i64) -> Result<usize> {
    let (chat, lane) = (chat.to_owned(), lane.clone());
    tokio::task::spawn_blocking(move || restore_sync(&chat, &lane, turn)).await?
}

/// List the bot ids that have a checkpoint for this turn.
pub fn members(chat: &str, lane: &Lane, turn: i64) -> Result<Vec<String>> {
    let root = turn_dir(chat, lane, turn);
    if !root.is_dir() {
        return Ok(Vec::new());
    }
    let mut out = Vec::new();
    for entry in fs::read_dir(&root).with_context(|| format!("reading {}", root.display()))? {
        let entry = entry?;
        if entry.file_type()?.is_dir() && entry.path().join("checkpoint.json").is_file() {
            out.push(entry.file_name().to_string_lossy().into_owned());
        }
    }
    out.sort();
    Ok(out)
}

fn capture_sync(chat: &str, lane: &Lane, turn: i64, bot: &str, cwd: &str) -> Result<()> {
    let cwd = Path::new(cwd);
    ensure!(cwd.is_dir(), "workspace folder does not exist: {}", cwd.display());
    let data = service::data_dir();
    ensure!(
        !data.starts_with(cwd),
        "can't checkpoint a folder that contains Codync's own data"
    );
    let dest = turn_dir(chat, lane, turn).join(safe(bot));
    if dest.exists() {
        fs::remove_dir_all(&dest)?;
    }
    fs::create_dir_all(&dest)?;
    let result = if let Some(root) = git_root(cwd)? {
        capture_git(&root, cwd, &dest)
    } else {
        capture_copy(cwd, &dest)
    };
    if let Err(error) = result {
        let _ = fs::remove_dir_all(&dest);
        return Err(error);
    }
    fs::write(
        dest.join("checkpoint.json"),
        serde_json::to_vec(&json!({
            "bot": bot,
            "cwd": cwd.to_string_lossy(),
            "createdAt": crate::store::now_ms(),
        }))?,
    )?;
    prune(chat, lane, turn);
    Ok(())
}

fn restore_sync(chat: &str, lane: &Lane, turn: i64) -> Result<usize> {
    let members = members(chat, lane, turn)?;
    ensure!(!members.is_empty(), "no checkpoint was saved for this turn");
    for bot in &members {
        let dir = turn_dir(chat, lane, turn).join(safe(bot));
        restore_one(&dir).with_context(|| format!("restoring {bot}'s workspace"))?;
    }
    Ok(members.len())
}

fn restore_one(dir: &Path) -> Result<()> {
    let meta: Value = serde_json::from_slice(
        &fs::read(dir.join("checkpoint.json")).context("checkpoint metadata is missing")?,
    )?;
    let cwd = PathBuf::from(meta["cwd"].as_str().unwrap_or_default());
    ensure!(cwd.is_dir(), "the checkpoint's folder no longer exists: {}", cwd.display());
    let tree = dir.join("tree");
    if tree.is_dir() {
        return restore_copy(&cwd, &tree);
    }
    let patch = fs::read(dir.join("tracked.patch")).context("checkpoint patch is missing")?;
    let root = git_root(&cwd)?.context("the checkpoint's folder is no longer a git repository")?;
    let patch_path = dir.join("tracked.patch");
    if !patch.is_empty() {
        // Check against HEAD, not the agent's current worktree (which is the thing being undone).
        let index = dir.join("check-index");
        let _ = fs::remove_file(&index);
        let read = std::process::Command::new("git")
            .env("GIT_INDEX_FILE", &index)
            .arg("-C")
            .arg(&root)
            .args(["read-tree", "HEAD"])
            .output()?;
        ensure!(read.status.success(), "couldn't prepare the checkpoint: {}", stderr(&read));
        let check = std::process::Command::new("git")
            .env("GIT_INDEX_FILE", &index)
            .arg("-C")
            .arg(&root)
            .args(["apply", "--cached", "--check", "--binary", "--whitespace=nowarn"])
            .arg(&patch_path)
            .output()?;
        let _ = fs::remove_file(&index);
        ensure!(check.status.success(), "the checkpoint no longer applies: {}", stderr(&check));
    }
    // Reset tracked files under the workspace, remove untracked files, then put back the
    // state as it was when the turn started. `.git` itself is never touched.
    let reset = git(&cwd, &["restore", "--source=HEAD", "--staged", "--worktree", "--", "."])?;
    ensure!(reset.status.success(), "couldn't restore tracked files: {}", stderr(&reset));
    let clean = git(&cwd, &["clean", "-fd", "--", "."])?;
    ensure!(clean.status.success(), "couldn't remove files created by the turn: {}", stderr(&clean));
    if !patch.is_empty() {
        let apply = git(&root, &["apply", "--binary", "--whitespace=nowarn", &patch_path.to_string_lossy()])?;
        ensure!(apply.status.success(), "couldn't reapply the checkpoint: {}", stderr(&apply));
    }
    restore_untracked(&dir.join("untracked"), &root)
}

fn capture_git(root: &Path, cwd: &Path, dest: &Path) -> Result<()> {
    let relative = cwd.strip_prefix(root).unwrap_or(Path::new(""));
    let pathspec = if relative.as_os_str().is_empty() {
        ".".to_owned()
    } else {
        relative.to_string_lossy().replace('\\', "/")
    };
    let diff = git(root, &["diff", "--binary", "HEAD", "--", &pathspec])?;
    ensure!(diff.status.success(), "couldn't read the workspace diff: {}", stderr(&diff));
    ensure!(diff.stdout.len() <= MAX_PATCH, "the workspace diff is too large to checkpoint");
    fs::write(dest.join("tracked.patch"), &diff.stdout)?;

    let untracked = git(root, &["ls-files", "--others", "--exclude-standard", "-z", "--", &pathspec])?;
    ensure!(untracked.status.success(), "couldn't list untracked files: {}", stderr(&untracked));
    let mut budget = Budget::default();
    for raw in untracked.stdout.split(|b| *b == 0).filter(|p| !p.is_empty()) {
        let path = PathBuf::from(String::from_utf8_lossy(raw).into_owned());
        ensure!(safe_relative(&path), "git returned an unsafe path");
        copy_entry(&root.join(&path), &dest.join("untracked").join(&path), &mut budget, MAX_UNTRACKED)?;
    }
    Ok(())
}

fn capture_copy(cwd: &Path, dest: &Path) -> Result<()> {
    let mut budget = Budget::default();
    copy_tree(cwd, &dest.join("tree"), &mut budget, MAX_COPY, true)?;
    // Full copies are much larger than git patches; keep fewer of them.
    fs::write(dest.join(".copy"), b"")?;
    Ok(())
}

fn restore_copy(cwd: &Path, tree: &Path) -> Result<()> {
    for entry in fs::read_dir(cwd).with_context(|| format!("reading {}", cwd.display()))? {
        let entry = entry?;
        if entry.file_name() == ".git" {
            continue;
        }
        remove_entry(&entry.path())?;
    }
    let mut budget = Budget::default();
    copy_tree(tree, cwd, &mut budget, MAX_COPY, false)
}

fn restore_untracked(src: &Path, root: &Path) -> Result<()> {
    if !src.is_dir() {
        return Ok(());
    }
    let mut budget = Budget::default();
    copy_tree(src, root, &mut budget, MAX_UNTRACKED, false)
}

fn turn_dir(chat: &str, lane: &Lane, turn: i64) -> PathBuf {
    service::data_dir()
        .join("checkpoints")
        .join(safe(chat))
        .join(safe(lane.thread.as_deref().unwrap_or("main")))
        .join(turn.to_string())
}

fn prune(chat: &str, lane: &Lane, current: i64) {
    let root = service::data_dir().join("checkpoints").join(safe(chat)).join(safe(lane.thread.as_deref().unwrap_or("main")));
    let Ok(entries) = fs::read_dir(root) else { return };
    let mut turns: Vec<(i64, PathBuf, bool)> = entries
        .flatten()
        .filter_map(|entry| {
            let turn = entry.file_name().to_string_lossy().parse::<i64>().ok()?;
            let path = entry.path();
            let copy = path.join(".copy").is_file()
                || fs::read_dir(&path).ok().is_some_and(|dirs| {
                    dirs.flatten().any(|dir| dir.path().join(".copy").is_file())
                });
            Some((turn, path, copy))
        })
        .collect();
    turns.sort_by_key(|(turn, _, _)| *turn);
    let (mut git, mut copies) = (0usize, 0usize);
    for (turn, path, copy) in turns.into_iter().rev() {
        let kept = if copy {
            copies += 1;
            copies
        } else {
            git += 1;
            git
        };
        let keep = if copy { KEEP_COPY_TURNS } else { KEEP_TURNS };
        if kept > keep && turn < current {
            let _ = fs::remove_dir_all(path);
        }
    }
}

fn safe(value: &str) -> String {
    value
        .chars()
        .map(|c| if c.is_ascii_alphanumeric() || c == '-' || c == '_' { c } else { '_' })
        .collect()
}

fn safe_relative(path: &Path) -> bool {
    !path.is_absolute() && path.components().all(|c| matches!(c, std::path::Component::Normal(_)))
}

fn git_root(cwd: &Path) -> Result<Option<PathBuf>> {
    let out = match git(cwd, &["rev-parse", "--show-toplevel"]) {
        Ok(out) => out,
        Err(_) => return Ok(None),
    };
    if !out.status.success() {
        return Ok(None);
    }
    let root = PathBuf::from(String::from_utf8_lossy(&out.stdout).trim());
    if root.as_os_str().is_empty() || !root.is_dir() {
        return Ok(None);
    }
    let head = git(&root, &["rev-parse", "--verify", "HEAD"])?;
    Ok(head.status.success().then_some(root))
}

fn git(cwd: &Path, args: &[&str]) -> Result<std::process::Output> {
    std::process::Command::new("git")
        .arg("-C")
        .arg(cwd)
        .args(args)
        .output()
        .with_context(|| format!("running git in {}", cwd.display()))
}

fn stderr(out: &std::process::Output) -> String {
    String::from_utf8_lossy(&out.stderr).trim().to_owned()
}

#[derive(Default)]
struct Budget {
    bytes: u64,
    files: usize,
}

fn copy_entry(src: &Path, dst: &Path, budget: &mut Budget, max_bytes: u64) -> Result<()> {
    let meta = fs::symlink_metadata(src).with_context(|| format!("reading {}", src.display()))?;
    if meta.file_type().is_symlink() {
        return copy_link(src, dst);
    }
    ensure!(meta.is_file(), "not a regular file: {}", src.display());
    budget.files += 1;
    budget.bytes = budget.bytes.saturating_add(meta.len());
    ensure!(budget.files <= MAX_FILES, "too many files to checkpoint");
    ensure!(budget.bytes <= max_bytes, "the workspace is too large to checkpoint");
    if let Some(parent) = dst.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::copy(src, dst)?;
    Ok(())
}

fn copy_tree(src: &Path, dst: &Path, budget: &mut Budget, max_bytes: u64, skip_git: bool) -> Result<()> {
    fs::create_dir_all(dst)?;
    for entry in fs::read_dir(src).with_context(|| format!("reading {}", src.display()))? {
        let entry = entry?;
        let name = entry.file_name();
        if skip_git && name == ".git" {
            continue;
        }
        let from = entry.path();
        let to = dst.join(&name);
        let meta = fs::symlink_metadata(&from)?;
        if meta.file_type().is_symlink() {
            copy_link(&from, &to)?;
        } else if meta.is_dir() {
            copy_tree(&from, &to, budget, max_bytes, skip_git)?;
        } else if meta.is_file() {
            copy_entry(&from, &to, budget, max_bytes)?;
        }
    }
    Ok(())
}

fn copy_link(src: &Path, dst: &Path) -> Result<()> {
    if let Some(parent) = dst.parent() {
        fs::create_dir_all(parent)?;
    }
    let target = fs::read_link(src)?;
    #[cfg(unix)]
    std::os::unix::fs::symlink(target, dst)?;
    #[cfg(not(unix))]
    std::os::windows::fs::symlink_file(target, dst)?;
    Ok(())
}

fn remove_entry(path: &Path) -> Result<()> {
    let meta = fs::symlink_metadata(path)?;
    if meta.is_dir() && !meta.file_type().is_symlink() {
        fs::remove_dir_all(path)?;
    } else {
        fs::remove_file(path)?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn relative_paths_stay_inside_the_workspace() {
        assert!(safe_relative(Path::new("src/main.rs")));
        assert!(!safe_relative(Path::new("../outside")));
        assert!(!safe_relative(Path::new("/etc/passwd")));
    }

    #[test]
    fn git_checkpoint_round_trip() {
        let dir = std::env::temp_dir().join(format!("codync-checkpoint-{}", uuid::Uuid::new_v4()));
        let root = dir.join("repo");
        let dest = dir.join("checkpoint");
        fs::create_dir_all(&root).unwrap();
        for args in [
            &["init", "-q"][..],
            &["config", "user.email", "a@b"][..],
            &["config", "user.name", "a"][..],
        ] {
            let out = git(&root, args).unwrap();
            assert!(out.status.success());
        }
        fs::write(root.join("a.txt"), "one\n").unwrap();
        let add = git(&root, &["add", "-A"]).unwrap();
        assert!(add.status.success());
        let commit = git(&root, &["commit", "-qm", "init"]).unwrap();
        assert!(commit.status.success());

        fs::write(root.join("a.txt"), "two\n").unwrap();
        fs::write(root.join("pre.txt"), "untracked before\n").unwrap();
        fs::create_dir_all(&dest).unwrap();
        capture_git(&root, &root, &dest).unwrap();
        fs::write(
            dest.join("checkpoint.json"),
            serde_json::to_vec(&json!({"bot": "b", "cwd": root.to_string_lossy()})).unwrap(),
        )
        .unwrap();

        fs::write(root.join("a.txt"), "three\n").unwrap();
        fs::write(root.join("new.txt"), "created by the turn\n").unwrap();
        restore_one(&dest).unwrap();
        assert_eq!(fs::read_to_string(root.join("a.txt")).unwrap(), "two\n");
        assert_eq!(fs::read_to_string(root.join("pre.txt")).unwrap(), "untracked before\n");
        assert!(!root.join("new.txt").exists());
        let _ = fs::remove_dir_all(dir);
    }
}
