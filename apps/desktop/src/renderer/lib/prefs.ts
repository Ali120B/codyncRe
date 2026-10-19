import { useSyncExternalStore } from 'react'
import { ROSTER_KINDS } from './roster-sections'
import type { StarAsk } from './star-ask'

/** A per-window preference in localStorage (kit's `@AppStorage`), observable by views. */
export function pref<T>(key: string, fallback: T) {
  const listeners = new Set<() => void>()
  let value: T = (() => {
    try {
      const raw = localStorage.getItem(key)
      return raw === null ? fallback : (JSON.parse(raw) as T)
    } catch {
      return fallback
    }
  })()
  window.addEventListener('storage', (e) => {
    if (e.key !== key) return
    try {
      value = e.newValue === null ? fallback : (JSON.parse(e.newValue) as T)
    } catch {
      value = fallback
    }
    for (const l of listeners) l()
  })
  return {
    get: () => value,
    set(next: T) {
      value = next
      try {
        localStorage.setItem(key, JSON.stringify(next))
      } catch {}
      for (const l of listeners) l()
    },
    subscribe(l: () => void) {
      listeners.add(l)
      return () => void listeners.delete(l)
    },
  }
}

export type Pref<T> = ReturnType<typeof pref<T>>

export function usePref<T>(p: Pref<T>): [T, (v: T) => void] {
  const value = useSyncExternalStore(p.subscribe, p.get)
  return [value, p.set]
}

export const TEXT_SIZES = [11, 12, 13, 14, 15, 16, 18]
export const DEFAULT_TEXT_SIZE = 15

/** The next larger (`up`) or smaller size for ⌘+ / ⌘−, staying at either end. */
export function stepTextSize(size: number, up: boolean) {
  return (up ? TEXT_SIZES.find((s) => s > size) : [...TEXT_SIZES].reverse().find((s) => s < size)) ?? size
}

export const prefs = {
  textSize: pref('conversationFontSize', DEFAULT_TEXT_SIZE),
  sidebarCompact: pref('sidebarCompact', false),
  sidebarWidth: pref('desktopSidebarWidth', 320),
  hiddenComputers: pref('hiddenComputers', ''),
  computerOrder: pref<string[]>('computerOrder', []),
  collapsedComputers: pref<string[]>('collapsedComputerSections', []),
  /** Which roster section sits on top ("groups" then "bots" by default), and which are folded. */
  rosterSectionOrder: pref<string[]>('rosterSectionOrder', [...ROSTER_KINDS]),
  rosterCollapsedSections: pref<string[]>('rosterCollapsedSections', []),
  /** The local account's own name; empty means this computer's user name. */
  profileName: pref('localProfileName', ''),
  usageIconStyle: pref<'character' | 'original'>('usageIconStyle', 'character'),
  onboardingDone: pref('macAccountOnboardingCompleted', false),
  computerAccessSetup: pref<'ready' | 'later' | null>('computerAccessSetup', null),
  /** The Computer panel's width (the aside it shares with details). */
  computerPanelWidth: pref('desktopComputerPanelWidth', 400),
  /** Workspace folders bots and groups can start in with one click (newest first). */
  workspaces: pref<string[]>('workspaceFolders', []),
  starAsk: pref<StarAsk>('githubStarAsk', { kind: 'never' }),
}

/** The Mac preference supplies one scale for conversation text and app chrome. */
export function applyTextSize(size: number) {
  const clamped = Number.isFinite(size) ? Math.min(Math.max(size, 11), 18) : DEFAULT_TEXT_SIZE
  document.documentElement.style.setProperty('--scale', String(clamped / DEFAULT_TEXT_SIZE))
  document.documentElement.style.setProperty('--conversation', `${clamped}px`)
  // Match the reference chat at the selected size; keep larger text an explicit preference.
  document.documentElement.style.setProperty('--chat', `${clamped}px`)
  document.documentElement.style.setProperty('--chat-scale', String(clamped / DEFAULT_TEXT_SIZE))
}

prefs.textSize.subscribe(() => applyTextSize(prefs.textSize.get()))

/** Widths the sidebar used to take on its own (old defaults, and the old resize clamp); it lifts one to the current default once. */
const STALE_SIDEBAR_WIDTHS = [240, 266, 300]
if (STALE_SIDEBAR_WIDTHS.includes(prefs.sidebarWidth.get())) prefs.sidebarWidth.set(320)
