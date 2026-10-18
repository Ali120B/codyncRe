import { useEffect, useRef, useState } from 'react'
import type { Bot } from '@shared/models'
import { ChoicePicker } from '../../components/Controls'
import { Icon } from '../../components/Icon'
import { font } from '../../lib/fonts'
import type { BotStore } from '../../store/bot-store'
import { useAgentModels } from '../bots/agent-models'
import { AnchoredPanel } from '../bots/parts'
import './thread-models-line.css'

/** The thread roots of one chat, oldest first. */
function threadRoots(store: BotStore, botId: string): string[] {
  const roots: string[] = []
  for (const entry of store.allEntries(botId)) {
    if (entry.threadId && !roots.includes(entry.threadId)) roots.push(entry.threadId)
  }
  return roots
}

/** A thread's title: its root message, else the first reply's. */
function threadTitle(store: BotStore, botId: string, root: string): string {
  const entries = store.allEntries(botId)
  const at = entries.find((e) => e.id === root)
  const text = at?.data.text ?? entries.find((e) => e.threadId === root && e.kind === 'user')?.data.text ?? ''
  const clean = text.replace(/\s+/g, ' ').trim()
  return clean.length > 42 ? `${clean.slice(0, 42)}…` : clean || 'Thread'
}

/**
 * Maus's thread-models line: a thread can run on its own model; the rest follow the bot's.
 * Shown under the chat's title when the chat has threads.
 */
export function ThreadModelsLine({ bot, store }: { bot: Bot; store: BotStore }) {
  const anchor = useRef<HTMLButtonElement>(null)
  const [open, setOpen] = useState(false)
  const models = useAgentModels(bot.backend, open)
  const online = store.connection.kind === 'online'
  useEffect(() => {
    if (online) void store.loadThreadModels(bot.id)
  }, [store, bot.id, online])
  const roots = threadRoots(store, bot.id)
  const pins = store.threadModels.get(bot.id) ?? {}
  const pinned = Object.keys(pins)
  if (!roots.length) return null
  const catalog = models.catalog?.models ?? []
  const clearAll = () => {
    for (const root of pinned) void store.setThreadModel(bot.id, root, null)
  }
  return (
    <>
      <button ref={anchor} className="thread-models-line press" onClick={() => setOpen((o) => !o)}>
        <Icon name="arrow.triangle.branch" size={10} color="var(--secondary)" />
        {pinned.length
          ? `${pinned.length} thread${pinned.length === 1 ? '' : 's'} on their own model`
          : `${roots.length} thread${roots.length === 1 ? '' : 's'} · set a model`}
      </button>
      <AnchoredPanel open={open} onClose={() => setOpen(false)} anchor={anchor}>
        <div className="thread-models-panel">
          <span style={{ ...font('caption'), color: 'var(--tertiary)' }}>Threads on their own model</span>
          {roots.map((root) => {
            const options = [
              { id: '', label: `Follow ${bot.name}` },
              ...catalog.map((m) => ({ id: m.id, label: m.name })),
              // A pin the agent no longer lists still shows as itself.
              ...(pins[root] && !catalog.some((m) => m.id === pins[root]) ? [{ id: pins[root], label: pins[root] }] : []),
            ]
            return (
              <div key={root} className="thread-models-row">
                <span className="thread-models-title">{threadTitle(store, bot.id, root)}</span>
                <ChoicePicker selection={pins[root] ?? ''} options={options} onChange={(id) => void store.setThreadModel(bot.id, root, id || null)} />
              </div>
            )
          })}
          {pinned.length ? (
            <button className="thread-models-clear press" onClick={clearAll}>
              <span style={{ flex: 1, textAlign: 'left' }}>Switch them all back to {bot.name}'s model</span>
              <Icon name="arrow.counterclockwise" size={11} color="var(--secondary)" />
            </button>
          ) : null}
        </div>
      </AnchoredPanel>
    </>
  )
}
