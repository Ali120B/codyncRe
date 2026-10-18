// The local account: a name this computer knows you by, no sign-in needed (Maus's profile
// row). The Codync cloud account is optional and sits beside it; either way the sidebar
// shows your name, not an email.

/** What you called yourself, else this computer's user name. */
export function localAccountName(saved: string, fallback = userName()): string {
  return saved.trim() || fallback.trim() || 'Local account'
}

/** The avatar letter for a name: "ali" → "A". */
export function accountInitial(name: string): string {
  return [...name.trim()][0]?.toUpperCase() ?? '?'
}

/** The OS user name the preload bridge reports ('' in tests and the demo). */
function userName(): string {
  return typeof window === 'undefined' ? '' : window.codync?.username ?? ''
}
