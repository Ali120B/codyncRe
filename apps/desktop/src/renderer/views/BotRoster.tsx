import { useRef, useState, type DragEvent, type ReactNode } from 'react'
import { isGroup, type Bot } from '@shared/models'
import { moveInOrder } from '../lib/computer-roster'
import { prefs, usePref } from '../lib/prefs'
import { kindOrder, placeKind, rosterKind, ROSTER_KIND_LABELS, type RosterKind } from '../lib/roster-sections'
import { useSlide } from '../lib/slide'
import { sameRef, refKey, type RosterItem } from '../store/app-model'
import type { BotStore } from '../store/bot-store'
import type { useComputerRoster } from '../store/computer-roster'
import { StoreContext, useApp } from '../store/context'
import { BotRow } from './BotRow'
import { ComputerRosterSection } from './ComputerRosterSection'
import { RosterSection } from './RosterSection'
import { UpdateNeededCard } from './UpdateNeededCard'

/** A bot being dragged among the bots of its own kind, its order so far, and where it started. */
interface BotDrag {
  item: RosterItem
  ids: string[]
  original: string[]
}

export function BotRoster({ roster, compact, onContext, items, searching }: {
  roster: ReturnType<typeof useComputerRoster>
  compact: boolean
  onContext: (bot: Bot, store: BotStore, x: number, y: number) => void
  /** Only these bots show (sidebar search); unset shows every bot. */
  items?: RosterItem[]
  /** While a search is on, folded sections open so their matches show. */
  searching?: boolean
}) {
  const app = useApp()
  const selected = app.selection
  const [savedKinds, setSavedKinds] = usePref(prefs.rosterSectionOrder)
  const [collapsedKinds, setCollapsedKinds] = usePref(prefs.rosterCollapsedSections)
  const [hovered, setHovered] = useState<string | null>(null)
  const [dragged, setDragged] = useState<string | null>(null)
  const [botDrag, setBotDrag] = useState<BotDrag | null>(null)
  const [sectionDrag, setSectionDrag] = useState<RosterKind | null>(null)
  const [dropMark, setDropMark] = useState<{ kind: RosterKind; place: 'before' | 'after' } | null>(null)
  const kinds = kindOrder(savedKinds)

  /** A section's rows, in the order a drag in progress has them. */
  const ordered = (computerId: string, items: RosterItem[]) => {
    if (botDrag?.item.ref.computerId !== computerId) return items
    const byId = new Map(items.map((item) => [item.bot.id, item]))
    return botDrag.ids.flatMap((id) => byId.get(id) ?? [])
  }

  // Any row of the same computer and kind takes the drop; only rows with the same pin state make room.
  const dragOver = (event: DragEvent<HTMLElement>, item: RosterItem) => {
    if (!botDrag || botDrag.item.ref.computerId !== item.ref.computerId || rosterKind(item.bot) !== rosterKind(botDrag.item.bot)) return
    event.preventDefault()
    event.dataTransfer.dropEffect = 'move'
    // A row still sliding out of the way would be hit again where it was.
    if (item.bot.id === botDrag.item.bot.id || item.bot.pinned !== botDrag.item.bot.pinned || event.currentTarget.getAnimations().length) return
    setBotDrag({ ...botDrag, ids: moveInOrder(botDrag.ids, botDrag.item.bot.id, item.bot.id) })
  }

  const drop = (event: DragEvent<HTMLElement>) => {
    if (!botDrag) return
    event.preventDefault()
    if (botDrag.ids.some((id, index) => id !== botDrag.original[index])) botDrag.item.store.reorder(botDrag.ids)
    setBotDrag(null)
  }

  const row = (item: RosterItem, siblings: RosterItem[]) => {
    const key = refKey(item.ref)
    const isSelected = sameRef(item.ref, selected)
    const many = roster.sections.length > 1
    const ids = siblings.map((sibling) => sibling.bot.id)
    return (
      <RosterRow
        key={key}
        dragging={botDrag?.item.bot.id === item.bot.id && botDrag.item.ref.computerId === item.ref.computerId}
        background={isSelected ? 'var(--roster-selected)' : hovered === key ? 'var(--bubble-agent)' : 'transparent'}
        compact={compact}
        label={many ? `${item.bot.name}, on ${item.store.hostName}` : item.bot.name}
        title={many ? `${item.bot.name} · ${item.store.hostName}` : item.bot.name}
        selected={isSelected}
        onHover={(on) => setHovered((h) => (on ? key : h === key ? null : h))}
        onClick={() => app.select(item.ref)}
        onContext={(x, y) => onContext(item.bot, item.store, x, y)}
        onDragStart={() => setBotDrag({ item, ids, original: ids })}
        onDragOver={(event) => dragOver(event, item)}
        onDrop={drop}
        onDragEnd={() => setBotDrag(null)}
      >
        <BotRow bot={item.bot} store={item.store} compact={compact} usingComputer={item.store.screen?.agentBot === item.bot.id} members={isGroup(item.bot) ? item.store.members(item.bot) : noMembers} />
      </RosterRow>
    )
  }

  /** Fold or unfold one kind, for every computer at once (Maus folds its sections the same way). */
  const toggleKind = (kind: RosterKind) => {
    setCollapsedKinds(collapsedKinds.includes(kind) ? collapsedKinds.filter((value) => value !== kind) : [...collapsedKinds, kind])
  }

  /** Alt+↑/↓ on a grip: one section up or down. */
  const moveKind = (kind: RosterKind, direction: -1 | 1) => {
    const target = kinds[kinds.indexOf(kind) + direction]
    if (target) setSavedKinds(placeKind(kinds, kind, target, direction < 0 ? 'before' : 'after'))
  }

  /** A heading being dragged takes its new order live, and a line marks where it would land. */
  const sectionOver = (kind: RosterKind) => (event: DragEvent<HTMLElement>) => {
    if (!sectionDrag) return
    event.preventDefault()
    event.dataTransfer.dropEffect = 'move'
    if (kind === sectionDrag) return
    const rect = event.currentTarget.getBoundingClientRect()
    const place = event.clientY > rect.top + rect.height / 2 ? 'after' : 'before'
    setDropMark({ kind, place })
    const next = placeKind(kinds, sectionDrag, kind, place)
    if (next.join() !== kinds.join()) setSavedKinds(next)
  }

  const sectionDrop = (kind: RosterKind) => (event: DragEvent<HTMLElement>) => {
    if (!sectionDrag) return
    event.preventDefault()
    if (kind !== sectionDrag && dropMark?.kind === kind) setSavedKinds(placeKind(kinds, sectionDrag, kind, dropMark.place))
    setSectionDrag(null)
    setDropMark(null)
  }

  const sectionDragEnd = () => {
    setSectionDrag(null)
    setDropMark(null)
  }

  const shownKeys = items ? new Set(items.map((i) => refKey(i.ref))) : null
  return roster.sections.map(({ computer, store, items: sectionItems }, index) => {
    const sectionRows = shownKeys ? sectionItems.filter((i) => shownKeys.has(refKey(i.ref))) : sectionItems
    const notice = store.mismatch && !compact ? <StoreContext.Provider value={store}><UpdateNeededCard /></StoreContext.Provider> : null
    const rows = compact
      ? ordered(computer.id, sectionRows).map((item) => row(item, sectionRows))
      : kinds.map((kind) => {
          const siblings = sectionRows.filter((item) => rosterKind(item.bot) === kind)
          const kindRows = ordered(computer.id, siblings).map((item) => row(item, siblings))
          if (!kindRows.length) return null
          return (
            <RosterSection
              key={kind}
              label={ROSTER_KIND_LABELS[kind]}
              collapsed={!searching && collapsedKinds.includes(kind)}
              dragging={sectionDrag === kind}
              dropLine={dropMark?.kind === kind ? dropMark.place : null}
              toggle={() => toggleKind(kind)}
              onDragStart={() => setSectionDrag(kind)}
              onDragEnd={sectionDragEnd}
              onDragOver={sectionOver(kind)}
              onDrop={sectionDrop(kind)}
              onMove={(direction) => moveKind(kind, direction)}
            >
              {kindRows}
            </RosterSection>
          )
        })
    if (!roster.grouped) return <div key={computer.id}>{notice}{rows}</div>
    return (
      <ComputerRosterSection key={computer.id} store={store} compact={compact}
        collapsed={roster.collapsed.includes(computer.id)} toggle={() => roster.toggle(computer.id)}
        previous={roster.sections[index - 1]?.computer.id} next={roster.sections[index + 1]?.computer.id}
        dragged={dragged} setDragged={setDragged} move={roster.move} notice={notice}>
        {rows}
        {!sectionRows.length && !store.mismatch && !compact ? <div className="computer-roster-empty">{store.shownConnection.kind === 'online' ? 'No bots yet' : store.connectionLabel}</div> : null}
      </ComputerRosterSection>
    )
  })
}

/** One roster button: drags to reorder, and slides when the others move. */
function RosterRow({ dragging, background, compact, label, title, selected, onHover, onClick, onContext, onDragStart, onDragOver, onDrop, onDragEnd, children }: {
  dragging: boolean
  background: string
  compact: boolean
  label: string
  title: string
  selected: boolean
  onHover: (on: boolean) => void
  onClick: () => void
  onContext: (x: number, y: number) => void
  onDragStart: () => void
  onDragOver: (event: DragEvent<HTMLElement>) => void
  onDrop: (event: DragEvent<HTMLElement>) => void
  onDragEnd: () => void
  children: ReactNode
}) {
  const ref = useRef<HTMLButtonElement>(null)
  useSlide(ref)
  return (
    <button
      ref={ref}
      className={`roster-row ${dragging ? 'dragging' : ''}`}
      style={{ padding: compact ? 0 : '0 8px', background }}
      aria-label={label}
      aria-selected={selected}
      title={title}
      draggable
      onMouseEnter={() => onHover(true)}
      onMouseLeave={() => onHover(false)}
      onClick={onClick}
      onContextMenu={(e) => {
        e.preventDefault()
        onContext(e.clientX, e.clientY)
      }}
      onDragStart={(e) => {
        // Its own type, so the composer and other apps don't take it as text.
        e.dataTransfer.effectAllowed = 'move'
        e.dataTransfer.setData('application/x-codync-bot', label)
        onDragStart()
      }}
      onDragEnter={onDragOver}
      onDragOver={onDragOver}
      onDrop={onDrop}
      onDragEnd={onDragEnd}
    >
      {children}
    </button>
  )
}

const noMembers: Bot[] = []
