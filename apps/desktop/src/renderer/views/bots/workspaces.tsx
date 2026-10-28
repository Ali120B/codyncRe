import { useEffect, useState } from 'react'
import { Button, CardSection, DropdownMenu, IconButton, Pill } from '../../components/Controls'
import { Icon } from '../../components/Icon'
import { Sheet } from '../../components/Overlay'
import { prefs, usePref } from '../../lib/prefs'
import { StoreContext, useStore } from '../../store/context'
import type { BotStore } from '../../store/bot-store'
import { FolderPicker } from './FolderPicker'
import './bots.css'

/** Remembers a folder as a one-click workspace (newest first, no duplicates). */
export function rememberWorkspace(path: string) {
  const clean = path.replace(/\/+$/, '') || '/'
  prefs.workspaces.set([clean, ...prefs.workspaces.get().filter((p) => p !== clean)].slice(0, 12))
}

export function forgetWorkspace(path: string) {
  prefs.workspaces.set(prefs.workspaces.get().filter((p) => p !== path))
}

/** The folder's own name ("~/projects/site" → "site"). */
export const workspaceName = (path: string) => path.split('/').filter(Boolean).pop() ?? path

/** `~` becomes the host's home folder; other paths pass through. */
export function expandWorkspace(path: string, home: string | null | undefined) {
  const typed = path.trim()
  if (!typed) return ''
  if (home && (typed === '~' || typed.startsWith('~/'))) return `${home.replace(/\/+$/, '')}${typed.slice(1)}`
  return typed.replace(/\/+$/, '') || '/'
}

/** A pill that picks a workspace folder: Personal (bots), a saved one, or browse. */
export function WorkspacePicker({ value, onChange, personal = true, placeholder }: {
  value: string
  onChange: (path: string) => void
  /** Bots have their own managed space; a shared group folder doesn't. */
  personal?: boolean
  placeholder?: string
}) {
  const store = useStore()
  const [list] = usePref(prefs.workspaces)
  const [picking, setPicking] = useState(false)
  const fallback = placeholder ?? (personal ? 'Personal workspace' : 'Choose a folder')
  return (
    <>
      <DropdownMenu
        title={value || fallback}
        items={() => [
          ...(personal ? [{ title: 'Personal workspace', selected: !value, action: () => onChange('') }] : []),
          ...list.map((path) => ({
            title: workspaceName(path),
            detail: path,
            selected: path === value,
            divider: personal ? false : path === list[0] && !value,
            action: () => onChange(path),
          })),
          {
            title: 'Choose folder…',
            icon: 'folder',
            divider: personal || !!list.length,
            action: () => setPicking(true),
          },
        ]}
      >
        <Pill outlined style={{ gap: 6, color: 'var(--text)' }}>
          <Icon name="folder" size={11} color="var(--secondary)" />
          <span style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: 170 }}>{value ? workspaceName(value) : fallback}</span>
          <Icon name="chevron.down" size={10} weight="semibold" color="var(--secondary)" />
        </Pill>
      </DropdownMenu>
      <Sheet open={picking} onClose={() => setPicking(false)} width={460} height={520}>
        <FolderPicker
          start={value || store.hello?.home || null}
          onPick={(path) => {
            rememberWorkspace(path)
            void store.ready().then((c) => c.call('addWorkspace', { path })).catch(() => {})
            onChange(path)
            setPicking(false)
          }}
        />
      </Sheet>
    </>
  )
}

/** Settings → General → Workspaces: the folders bots and groups start in with one click. */
export function WorkspacesCard({ store }: { store: BotStore | null }) {
  const [list] = usePref(prefs.workspaces)
  const [typed, setTyped] = useState('')
  const [picking, setPicking] = useState(false)
  useEffect(() => {
    if (!store) return
    let cancelled = false
    void (async () => {
      try {
        const c = await store.ready()
        const res = await c.call<{ workspaces?: string[] }>('workspaces', {})
        if (cancelled) return
        const hostList = Array.isArray(res.workspaces) ? res.workspaces : []
        const local = prefs.workspaces.get()
        if (hostList.length === 0 && local.length > 0) {
          await c.call('setWorkspaces', { workspaces: local })
        } else if (hostList.length > 0) {
          const merged = [...hostList]
          for (const p of local) if (!merged.includes(p)) merged.push(p)
          const capped = merged.slice(0, 12)
          prefs.workspaces.set(capped)
          if (capped.length !== hostList.length) await c.call('setWorkspaces', { workspaces: capped })
        }
      } catch {}
    })()
    return () => { cancelled = true }
  }, [store])
  const add = () => {
    const path = expandWorkspace(typed, store?.hello?.home)
    if (!path) return
    rememberWorkspace(path)
    void store?.ready().then((c) => c.call('addWorkspace', { path })).catch(() => {})
    setTyped('')
  }
  return (
    <>
      <CardSection title="Workspaces" footer="Folders you can pick in one click when creating a bot or a group, instead of browsing every time.">
        {list.length === 0 ? (
          <span style={{ color: 'var(--secondary)' }}>No folders yet. Add one below, or pick one while creating a bot.</span>
        ) : (
          list.map((path) => (
            <div key={path} className="settings-row" style={{ gap: 8 }}>
              <Icon name="folder" size={14} color="var(--secondary)" />
              <span style={{ flex: 1, minWidth: 0, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }} title={path}>
                {path}
              </span>
              <IconButton title={`Remove ${path}`} icon="xmark" onClick={() => { forgetWorkspace(path); void store?.ready().then((c) => c.call('removeWorkspace', { path })).catch(() => {}) }} />
            </div>
          ))
        )}
        <div className="settings-row" style={{ gap: 8 }}>
          <input
            className="field-box"
            style={{ flex: 1, minWidth: 0 }}
            value={typed}
            spellCheck={false}
            placeholder="Add a folder path, e.g. ~/projects/site"
            onChange={(e) => setTyped(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') add()
            }}
          />
          <Button kind="secondary" onClick={add} disabled={!typed.trim()}>
            Add
          </Button>
          {store ? (
            <Button kind="secondary" onClick={() => setPicking(true)}>
              Browse…
            </Button>
          ) : null}
        </div>
      </CardSection>
      {store ? (
        <StoreContext.Provider value={store}>
          <Sheet open={picking} onClose={() => setPicking(false)} width={460} height={520}>
            <FolderPicker
              start={store.hello?.home ?? null}
              onPick={(path) => {
                rememberWorkspace(path)
                void store?.ready().then((c) => c.call('addWorkspace', { path })).catch(() => {})
                setPicking(false)
              }}
            />
          </Sheet>
        </StoreContext.Provider>
      ) : null}
    </>
  )
}
