import { useEffect, useRef, useState } from 'react'
import { Icon } from '../../components/Icon'
import { font } from '../../lib/fonts'
import { useApp } from '../../store/context'
import type { BotStore } from '../../store/bot-store'
import './chat-find.css'

/**
 * Maus's find bar: type a phrase and Enter walks this chat's messages — each hit lands
 * with the same scroll-and-flash the global search uses. Esc closes.
 */
export function ChatFindBar({ botId, store, close }: { botId: string; store: BotStore; close: () => void }) {
  const app = useApp()
  const [query, setQuery] = useState('')
  const [at, setAt] = useState(-1)
  const input = useRef<HTMLInputElement>(null)
  useEffect(() => input.current?.focus(), [])

  const q = query.trim().toLowerCase()
  const matches = q.length < 2
    ? []
    : store
        .allEntries(botId)
        .filter((e) => (e.threadId ?? null) === null && (e.kind === 'user' || e.kind === 'agent') && (e.data.text ?? '').toLowerCase().includes(q))
        .sort((a, b) => a.seq - b.seq)
        .map((e) => e.id)

  const jump = (index: number) => {
    const id = matches[index]
    if (id) app.showMessage({ computerId: store.computer.id, botId }, id, null)
  }
  const next = (step: number) => {
    if (!matches.length) return
    const index = (at + step + matches.length) % matches.length
    setAt(index)
    jump(index)
  }

  return (
    <div className="chat-find">
      <Icon name="magnifyingglass" size={12} color="var(--tertiary)" />
      <input
        ref={input}
        value={query}
        placeholder="Find in chat"
        aria-label="Find in chat"
        spellCheck={false}
        onChange={(e) => {
          setQuery(e.target.value)
          setAt(-1)
        }}
        onKeyDown={(e) => {
          if (e.key === 'Enter') {
            e.preventDefault()
            next(e.shiftKey ? -1 : 1)
          }
          if (e.key === 'Escape' && !e.nativeEvent.isComposing) {
            e.preventDefault()
            close()
          }
        }}
      />
      <span style={{ ...font('caption'), color: 'var(--tertiary)', whiteSpace: 'nowrap' }}>
        {q.length < 2 ? '' : matches.length ? (at >= 0 ? `${at + 1} of ${matches.length}` : `${matches.length} ${matches.length === 1 ? 'match' : 'matches'}`) : 'No matches'}
      </span>
      <button className="chat-find-nav" aria-label="Previous match" title="Previous match" onClick={() => next(-1)}>
        <Icon name="chevron.up" size={11} weight="semibold" />
      </button>
      <button className="chat-find-nav" aria-label="Next match" title="Next match" onClick={() => next(1)}>
        <Icon name="chevron.down" size={11} weight="semibold" />
      </button>
      <button className="chat-find-nav" aria-label="Close find" title="Close" onClick={close}>
        <Icon name="xmark" size={11} weight="semibold" />
      </button>
    </div>
  )
}
