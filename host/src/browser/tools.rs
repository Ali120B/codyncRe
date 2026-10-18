//! The `browser` MCP tools: what a bot can do in its own headless browser. Every call runs
//! against the bot's session (`session.rs`), which starts the browser on first use.

use crate::hub::Hub;
use anyhow::{Result, anyhow, bail};
use serde_json::{Value, json};
use std::sync::Arc;

pub const INSTRUCTIONS: &str = "You have your own headless browser with a private profile (its own cookies and logins). \
Work in it like a person: browser_open a page, browser_read or browser_scrape to see it, then browser_click / \
browser_type / browser_press to act, and browser_screenshot whenever looking at the rendered page helps. \
Click by the text or selector you saw in browser_read/scrape when you can — coordinates are the fallback. \
The user can watch your browser and take it over from their computer; if a tool answers that the user has control, \
wait and try again — do not fight them for it.";

/// The tools a bot with **Use a browser** on is offered; none when this computer has no browser.
pub fn tools() -> Value {
    if super::available_engine().is_err() {
        return json!([]);
    }
    let action = |name: &str, description: &str, schema: Value| {
        json!({"name": name, "description": description, "inputSchema": schema,
               "annotations": {"readOnlyHint": false, "idempotentHint": false}})
    };
    json!([
        action(
            "browser_open",
            "Open a URL in your browser (starting it if needed). This is the usual way in; use browser_navigate to move the current tab.",
            json!({"type": "object", "properties": {"url": {"type": "string"},
                "new_tab": {"type": "boolean", "description": "Open in a new tab instead of the current one."}},
                "required": ["url"]})
        ),
        action(
            "browser_navigate",
            "Go to a URL in the current tab (back/forward are browser_history when needed).",
            json!({"type": "object", "properties": {"url": {"type": "string"}}, "required": ["url"]})
        ),
        action("browser_tabs", "List the open tabs (id, title, url).", json!({"type": "object", "properties": {}})),
        action(
            "browser_new_tab",
            "Open a new tab, optionally at a URL.",
            json!({"type": "object", "properties": {"url": {"type": "string"}}})
        ),
        action(
            "browser_switch_tab",
            "Make one of browser_tabs' tabs the active one.",
            json!({"type": "object", "properties": {"id": {"type": "string"}}, "required": ["id"]})
        ),
        action(
            "browser_close_tab",
            "Close one of your tabs.",
            json!({"type": "object", "properties": {"id": {"type": "string"}}, "required": ["id"]})
        ),
        action(
            "browser_screenshot",
            "A picture of the active tab (1280×800), as you would see it.",
            json!({"type": "object", "properties": {}})
        ),
        action(
            "browser_read",
            "The active tab's visible text, in reading order.",
            json!({"type": "object", "properties": {"max_chars": {"type": "integer", "description": "Default 20000."}}})
        ),
        action(
            "browser_scrape",
            "Text and HTML of the elements matching a CSS selector (up to 50).",
            json!({"type": "object", "properties": {"selector": {"type": "string"},
                "limit": {"type": "integer", "description": "Default 50, max 50."}}, "required": ["selector"]})
        ),
        action(
            "browser_click",
            "Click an element: by its visible text, by CSS selector, or at viewport coordinates. Uses a real mouse click.",
            json!({"type": "object", "properties": {"text": {"type": "string"},
                "selector": {"type": "string"}, "x": {"type": "number"}, "y": {"type": "number"}}})
        ),
        action(
            "browser_type",
            "Type text into the focused element (click it first). Set submit to press Enter after.",
            json!({"type": "object", "properties": {"text": {"type": "string"},
                "submit": {"type": "boolean"}}, "required": ["text"]})
        ),
        action(
            "browser_press",
            "Press one key: Enter, Tab, Escape, Backspace, Delete, the arrows, PageUp/PageDown, Home, End, or space.",
            json!({"type": "object", "properties": {"key": {"type": "string"}}, "required": ["key"]})
        ),
        action(
            "browser_scroll",
            "Scroll the active tab. Positive delta_y scrolls down.",
            json!({"type": "object", "properties": {"delta_y": {"type": "number"},
                "x": {"type": "number"}, "y": {"type": "number"}}, "required": ["delta_y"]})
        ),
    ])
}

/// Runs one `browser` tool for `bot`: `{content, isError?}` for the agent.
pub async fn browser(hub: &Arc<Hub>, bot: &str, name: &str, args: Value) -> Result<Value> {
    let session = hub.browser.session(hub, bot).await?;
    let text = |value: Value| json!({"content": [{"type": "text", "text": value.to_string()}]});
    Ok(match name {
        "browser_open" => {
            let url = str_arg(&args, "url")?;
            let tab = session.open(url, args["new_tab"] == true).await?;
            text(json!({"tab": tab.id, "url": tab.url, "title": tab.title}))
        }
        "browser_navigate" => {
            let tab = session.navigate(str_arg(&args, "url")?).await?;
            text(json!({"tab": tab.id, "url": tab.url, "title": tab.title}))
        }
        "browser_tabs" => text(json!(session.tabs().await?)),
        "browser_new_tab" => {
            let tab = session.new_tab(args["url"].as_str()).await?;
            text(json!({"tab": tab.id, "url": tab.url}))
        }
        "browser_switch_tab" => {
            session.select_tab(str_arg(&args, "id")?).await?;
            text(json!({"active": str_arg(&args, "id")?}))
        }
        "browser_close_tab" => {
            session.close_tab(str_arg(&args, "id")?).await?;
            text(json!({"closed": true}))
        }
        "browser_screenshot" => {
            let shot = session.agent_screenshot().await?;
            json!({"content": [{"type": "image", "data": base64::Engine::encode(&base64::engine::general_purpose::STANDARD, &shot.data), "mimeType": format!("image/{}", shot.format)}]})
        }
        "browser_read" => {
            let max = args["max_chars"].as_u64().unwrap_or(20_000).clamp(200, 200_000) as usize;
            let page = session.agent_read().await?;
            text(json!({"text": truncate(&page, max)}))
        }
        "browser_scrape" => {
            let selector = str_arg(&args, "selector")?;
            let limit = args["limit"].as_u64().unwrap_or(50).clamp(1, 50);
            let js = scrape_js(selector, limit);
            text(session.agent_evaluate(&js).await?)
        }
        "browser_click" => {
            let (x, y) = click_point(&session, &args).await?;
            session.agent_click(x, y).await?;
            text(json!({"clicked": {"x": x, "y": y}}))
        }
        "browser_type" => {
            let mut typed = str_arg(&args, "text")?.to_owned();
            if args["submit"] == true {
                typed.push('\n');
            }
            session.agent_type(&typed).await?;
            text(json!({"typed": true, "submitted": args["submit"] == true}))
        }
        "browser_press" => {
            let key = str_arg(&args, "key")?;
            session.agent_press(key).await?;
            text(json!({"pressed": key}))
        }
        "browser_scroll" => {
            let delta = args["delta_y"].as_f64().ok_or_else(|| anyhow!("delta_y is required"))?;
            let x = args["x"].as_f64().unwrap_or(640.0);
            let y = args["y"].as_f64().unwrap_or(400.0);
            session.agent_scroll(x, y, delta).await?;
            text(json!({"scrolled": delta}))
        }
        other => bail!("unknown browser tool: {other}"),
    })
}

fn str_arg<'a>(args: &'a Value, key: &str) -> Result<&'a str> {
    args[key].as_str().filter(|s| !s.trim().is_empty()).ok_or_else(|| anyhow!("{key} is required"))
}

/// Where `browser_click` should land: text first, then selector, then plain coordinates.
async fn click_point(session: &super::BotBrowser, args: &Value) -> Result<(f64, f64)> {
    if let Some(text) = args["text"].as_str().filter(|t| !t.trim().is_empty()) {
        let js = find_text_js(text);
        let point = session.agent_evaluate(&js).await?;
        return point_or_missing(&point, text);
    }
    if let Some(selector) = args["selector"].as_str().filter(|s| !s.trim().is_empty()) {
        let js = find_selector_js(selector);
        let point = session.agent_evaluate(&js).await?;
        return point_or_missing(&point, selector);
    }
    match (args["x"].as_f64(), args["y"].as_f64()) {
        (Some(x), Some(y)) => Ok((x, y)),
        _ => bail!("give text, selector, or both x and y"),
    }
}

fn point_or_missing(point: &Value, what: &str) -> Result<(f64, f64)> {
    match (point["x"].as_f64(), point["y"].as_f64()) {
        (Some(x), Some(y)) => Ok((x, y)),
        _ => bail!("nothing on the page matched {what:?} — browser_read or browser_screenshot first"),
    }
}

/// JS: the center of the element whose visible text best matches `text`, scrolled into view.
fn find_text_js(text: &str) -> String {
    let wanted = serde_json::to_string(text.trim()).unwrap_or_else(|_| "\"\"".into());
    format!(
        "(() => {{
            const wanted = {wanted}.toLowerCase();
            const els = [...document.querySelectorAll('a,button,input,textarea,select,summary,label,option,[role=button],[role=link],[role=tab],[role=menuitem],[onclick]')];
            const norm = (e) => ((e.innerText ?? e.value ?? e.getAttribute('aria-label') ?? '') + '').trim().toLowerCase();
            const el = els.find((e) => norm(e) === wanted)
                ?? els.filter((e) => norm(e).includes(wanted)).sort((a, b) => norm(a).length - norm(b).length)[0];
            if (!el) return null;
            el.scrollIntoView({{block: 'center'}});
            const r = el.getBoundingClientRect();
            return {{x: r.left + r.width / 2, y: r.top + r.height / 2}};
        }})()"
    )
}

fn find_selector_js(selector: &str) -> String {
    let selector = serde_json::to_string(selector).unwrap_or_else(|_| "\"\"".into());
    format!(
        "(() => {{
            const el = document.querySelector({selector});
            if (!el) return null;
            el.scrollIntoView({{block: 'center'}});
            const r = el.getBoundingClientRect();
            return {{x: r.left + r.width / 2, y: r.top + r.height / 2}};
        }})()"
    )
}

fn scrape_js(selector: &str, limit: u64) -> String {
    let selector = serde_json::to_string(selector).unwrap_or_else(|_| "\"\"".into());
    format!(
        "(() => [...document.querySelectorAll({selector})].slice(0, {limit}).map((e) => {{
            const out = {{text: ((e.innerText ?? '') + '').trim().slice(0, 2000), html: (e.outerHTML ?? '').slice(0, 2000)}};
            if (e.href) out.href = e.href;
            if (e.value !== undefined) out.value = String(e.value).slice(0, 500);
            return out;
        }}))()"
    )
}

fn truncate(text: &str, max: usize) -> String {
    if text.len() <= max {
        return text.to_owned();
    }
    let end = (0..=max).rev().find(|&i| text.is_char_boundary(i)).unwrap_or(0);
    format!("{}\n… (truncated)", &text[..end])
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_tool_list_covers_the_whole_flow() {
        let names: Vec<String> =
            tools().as_array().unwrap().iter().map(|t| t["name"].as_str().unwrap().to_owned()).collect();
        for tool in [
            "browser_open",
            "browser_navigate",
            "browser_screenshot",
            "browser_click",
            "browser_type",
            "browser_read",
            "browser_scrape",
        ] {
            assert!(names.contains(&tool.to_owned()), "{tool} missing");
        }
    }

    #[test]
    fn snippets_never_split_a_character() {
        let text = "寫".repeat(50);
        let short = truncate(&text, 7);
        assert!(short.starts_with("寫寫") && short.ends_with("(truncated)"));
        assert_eq!(truncate("ok", 7), "ok");
    }

    #[test]
    fn click_js_escapes_its_inputs() {
        let js = find_text_js("he said \"hi\"");
        assert!(js.contains(r#"he said \"hi\""#));
        assert!(find_selector_js("#a > b").contains("#a > b"));
    }
}
