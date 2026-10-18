import { useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { draftOf, type Bot } from '@shared/models'
import { ChoicePicker, SearchField, Spinner } from '../../components/Controls'
import { Icon } from '../../components/Icon'
import { ProviderMark } from '../../components/ProviderMark'
import { usePresence } from '../../components/Overlay'
import { font } from '../../lib/fonts'
import { useStore } from '../../store/context'
import { useAgentModels, type AgentModels } from '../bots/agent-models'
import { errorText } from '../bots/drafts'
import './model-chip.css'

/** The chip's label: the chosen model's name, its id until the catalog has loaded, or Default. */
function modelLabel(bot: Bot, catalog: AgentModels | null): string {
  if (!bot.model) return 'Default'
  // Model ids often repeat the agent's own id ("opencode/…"); the chip already names the agent.
  return catalog?.models.find((m) => m.id === bot.model)?.name ?? bot.model.replace(`${bot.backend}/`, '')
}

/** The chat header's model switcher: the current model, then a provider rail and a searchable list. */
export function ModelChip({ bot }: { bot: Bot }) {
  const store = useStore()
  const [open, setOpen] = useState(false)
  const anchor = useRef<HTMLButtonElement>(null)
  const label = `${store.backendName(bot.backend)} · ${modelLabel(bot, null)}`
  return (
    <>
      <button ref={anchor} className={`model-chip ${open ? 'open' : ''}`} title={`Model · ${label}`} aria-haspopup="listbox" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        <ProviderMark id={bot.backend} name={store.backendName(bot.backend)} size={13} />
        <span className="model-chip-label">{label}</span>
        <Icon name="chevron.down" size={8} weight="semibold" />
      </button>
      <ModelPopover open={open} onClose={() => setOpen(false)} anchor={anchor} bot={bot} />
    </>
  )
}

interface ModelRow {
  id: string | null
  name: string
  description?: string | null
}

function ModelPopover({ open, onClose, anchor, bot }: {
  open: boolean
  onClose: () => void
  anchor: React.RefObject<HTMLButtonElement | null>
  bot: Bot
}) {
  const store = useStore()
  const { mounted, shown } = usePresence(open, 120)
  const [paneBackend, setPaneBackend] = useState(bot.backend)
  const [query, setQuery] = useState('')
  const [highlight, setHighlight] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const search = useRef<HTMLInputElement>(null)
  const panel = useRef<HTMLDivElement>(null)
  const [rect, setRect] = useState<DOMRect | null>(null)
  // The agent's list costs a real session on the host, so it loads once the picker opens.
  const models = useAgentModels(paneBackend, open && mounted)

  useEffect(() => {
    if (!open) return
    setPaneBackend(bot.backend)
    setQuery('')
    setError(null)
    if (anchor.current) setRect(anchor.current.getBoundingClientRect())
    const focus = requestAnimationFrame(() => search.current?.focus())
    return () => cancelAnimationFrame(focus)
  }, [open, anchor, bot.backend])

  // Escape closes; while the picker is open, the wheel only moves its own list.
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    const onWheel = (e: WheelEvent) => {
      if (panel.current && !panel.current.contains(e.target as Node)) {
        e.preventDefault()
        e.stopPropagation()
      }
    }
    window.addEventListener('keydown', onKey)
    window.addEventListener('wheel', onWheel, { capture: true, passive: false })
    return () => {
      window.removeEventListener('keydown', onKey)
      window.removeEventListener('wheel', onWheel, true)
    }
  }, [open, onClose])

  // Every agent on this computer, plus the bot's own (never drop the current one).
  const agents = useMemo(() => {
    const list = store.hello?.backends ?? []
    const picked = list.filter((b) => b.available || b.id === bot.backend)
    return picked.length ? picked : list
  }, [store.hello, bot.backend])

  // The agent's own "Default" entry is the same choice as an unset model, so it folds into the Default row.
  const agentDefault = models.catalog?.models.find((m) => m.name.toLowerCase().startsWith('default'))
  const available = (models.catalog?.models ?? []).filter((m) => m.id !== agentDefault?.id)
  const sameAgent = paneBackend === bot.backend
  const value = sameAgent ? (bot.model === agentDefault?.id ? null : (bot.model ?? null)) : undefined
  const allRows: ModelRow[] = [
    { id: null, name: 'Default', description: agentDefault ? agentDefault.name : "The agent's own choice" },
    ...available.map((m) => ({ id: m.id, name: m.name, description: m.description })),
  ]
  const q = query.trim().toLowerCase()
  const rows = q ? allRows.filter((r) => [r.name, r.description ?? '', r.id ?? ''].some((s) => s.toLowerCase().includes(q))) : allRows

  useEffect(() => setHighlight(0), [query, open, paneBackend])
  const modelOptions = value ? (available.find((m) => m.id === value)?.options ?? []) : []

  const apply = (model: string | null) => {
    void store
      .save({ ...draftOf(bot), backend: paneBackend, model })
      .then(() => onClose())
      .catch((e: unknown) => setError(errorText(e)))
  }
  const applyOption = (id: string, v: string) => {
    const { [id]: _, ...rest } = bot.modelOptions
    const next = v ? { ...rest, [id]: v } : rest
    void store
      .save({ ...draftOf(bot), model: bot.model ?? null, modelOptions: next })
      .catch((e: unknown) => setError(errorText(e)))
  }

  const onKey = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      const step = e.key === 'ArrowDown' ? 1 : -1
      setHighlight((i) => (rows.length ? (i + step + rows.length) % rows.length : 0))
    } else if (e.key === 'Enter') {
      e.preventDefault()
      const row = rows[highlight]
      if (row) apply(row.id)
    }
  }

  if (!mounted || !rect) return null
  const H = window.innerHeight
  const roomBelow = Math.max(0, H - rect.bottom - 14)
  const roomAbove = Math.max(0, rect.top - 14)
  const below = roomBelow >= roomAbove
  const width = Math.min(470, window.innerWidth - 16)
  const style: React.CSSProperties = {
    position: 'absolute',
    [below ? 'top' : 'bottom']: below ? rect.bottom + 6 : H - rect.top + 6,
    right: Math.max(8, window.innerWidth - rect.right + 44),
    width,
    maxHeight: Math.min(460, below ? roomBelow : roomAbove),
    transformOrigin: `${below ? 'top' : 'bottom'} right`,
  }

  return createPortal(
    <div className={`model-layer ${shown ? 'shown' : ''}`} onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="model-panel" ref={panel} style={style}>
        <div className="model-rail" role="tablist" aria-label="Agent">
          {agents.map((b) => (
            <button
              key={b.id}
              role="tab"
              aria-selected={b.id === paneBackend}
              className={`model-rail-row ${b.id === paneBackend ? 'selected' : ''} ${b.available ? '' : 'dim'}`}
              title={b.name}
              onClick={() => setPaneBackend(b.id)}
            >
              <ProviderMark id={b.id} name={b.name} size={16} />
              <span className="model-rail-name">{b.name}</span>
            </button>
          ))}
        </div>
        <div className="model-pane" role="listbox" aria-label="Model">
          <div className="model-search">
            <SearchField value={query} onChange={setQuery} prompt="Search models" ariaLabel="Search models" inputRef={search} onKeyDown={onKey} />
          </div>
          <div className="model-list">
            {rows.map((row, i) => (
              <button
                key={row.id ?? 'default'}
                role="option"
                aria-selected={row.id === value}
                className={`model-row ${i === highlight ? 'highlight' : ''}`}
                onMouseMove={() => setHighlight(i)}
                onClick={() => apply(row.id)}
              >
                <span className="model-row-text">
                  <span className="model-row-name">{row.name}</span>
                  {row.description ? <span className="model-row-description">{row.description}</span> : null}
                </span>
                {row.id === value ? <Icon name="checkmark" size={10} weight="semibold" /> : null}
              </button>
            ))}
            {!rows.length && !models.loading ? <div className="model-note">No matching models</div> : null}
          </div>
          {models.loading ? (
            <div className="model-note" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Spinner size={12} /> Loading models…
            </div>
          ) : null}
          {!models.loading && models.failure ? (
            <button className="model-note model-retry" onClick={models.reload}>
              {models.failure} · Retry
            </button>
          ) : null}
          {!models.loading && !models.failure && models.catalog && !models.catalog.models.length ? (
            <div className="model-note">This agent doesn't advertise a model list. Change it in bot settings.</div>
          ) : null}
          {modelOptions.length ? (
            <div className="model-options">
              {modelOptions.map((o) => {
                const fallback = o.values.find((v) => v.id === o.default)?.name
                const choices = [{ id: '', label: fallback ? `Default · ${fallback}` : 'Default' }, ...o.values.map((v) => ({ id: v.id, label: v.name }))]
                return (
                  <div key={o.id} className="model-option-row">
                    <span style={{ ...font('caption'), color: 'var(--secondary)' }}>{o.name}</span>
                    <ChoicePicker selection={bot.modelOptions[o.id] ?? ''} options={choices} onChange={(v) => applyOption(o.id, v)} />
                  </div>
                )
              })}
            </div>
          ) : null}
          {error ? <div className="model-note model-error">{error}</div> : null}
        </div>
      </div>
    </div>,
    document.body,
  )
}
