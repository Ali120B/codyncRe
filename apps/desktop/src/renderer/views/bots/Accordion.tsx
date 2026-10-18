import type { ReactNode } from 'react'
import { Icon } from '../../components/Icon'

/** One row of the settings accordion (Maus's All settings): a quiet card that opens one at a time. */
export function Accordion({ id, icon, title, open, onToggle, children }: {
  id: string
  icon: string
  title: string
  open: string
  onToggle: (id: string) => void
  children: ReactNode
}) {
  const expanded = open === id
  return (
    <div className="settings-acc">
      <button className="settings-acc-row press" aria-expanded={expanded} onClick={() => onToggle(id)}>
        <Icon name={icon} size={14} color="var(--secondary)" />
        <span className="settings-acc-title">{title}</span>
        <Icon name={expanded ? 'chevron.up' : 'chevron.down'} size={11} weight="semibold" color="var(--tertiary)" />
      </button>
      {expanded ? <div className="settings-acc-body" data-acc={id}>{children}</div> : null}
    </div>
  )
}
