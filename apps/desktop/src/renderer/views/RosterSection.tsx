import { useRef, type DragEvent, type KeyboardEvent, type ReactNode } from 'react'
import { Icon } from '../components/Icon'
import { useSlide } from '../lib/slide'
import './roster-section.css'

/** One roster section (Group chats, Bots): a folding heading whose grip reorders the sections. */
export function RosterSection({ label, collapsed, dragging, dropLine, toggle, onDragStart, onDragEnd, onDragOver, onDrop, onMove, children }: {
  label: string
  collapsed: boolean
  dragging: boolean
  /** Where the dragged heading would land: a line at this section's edge. */
  dropLine: 'before' | 'after' | null
  toggle: () => void
  onDragStart: () => void
  onDragEnd: () => void
  onDragOver: (event: DragEvent<HTMLElement>) => void
  onDrop: (event: DragEvent<HTMLElement>) => void
  /** Alt+↑/↓ on the grip, like the computer headings. */
  onMove?: (direction: -1 | 1) => void
  children: ReactNode
}) {
  const ref = useRef<HTMLElement>(null)
  useSlide(ref)
  const onGripKey = (event: KeyboardEvent<HTMLElement>) => {
    if (!event.altKey || !onMove || !['ArrowUp', 'ArrowDown'].includes(event.key)) return
    event.preventDefault()
    event.stopPropagation()
    onMove(event.key === 'ArrowUp' ? -1 : 1)
  }
  return (
    <section ref={ref} className={`roster-section ${dragging ? 'dragging' : ''}`} aria-label={label} onDragOver={onDragOver} onDrop={onDrop}>
      {dropLine === 'before' ? <span className="roster-section-line" aria-hidden /> : null}
      <div className="roster-section-heading">
        <button className="roster-section-toggle" aria-expanded={!collapsed} title={`${collapsed ? 'Show' : 'Hide'} ${label}`} onClick={toggle}>
          <span className={`roster-section-chevron ${collapsed ? 'collapsed' : ''}`}><Icon name="chevron.down" size={9} /></span>
          <span className="roster-section-label">{label}</span>
        </button>
        <span
          className="roster-section-grip"
          role="button"
          tabIndex={0}
          draggable
          aria-label={`Reorder ${label}`}
          title="Drag to move this section"
          onKeyDown={onGripKey}
          onDragStart={(event) => {
            event.dataTransfer.effectAllowed = 'move'
            event.dataTransfer.setData('text/plain', label)
            onDragStart()
          }}
          onDragEnd={onDragEnd}
        >
          <Icon name="ellipsis.vertical" size={12} />
        </span>
      </div>
      <div className={`roster-section-content ${collapsed ? 'collapsed' : ''}`} inert={collapsed} aria-hidden={collapsed}>
        <div>{children}</div>
      </div>
      {dropLine === 'after' ? <span className="roster-section-line" aria-hidden /> : null}
    </section>
  )
}
