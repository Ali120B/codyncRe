import { useRef, useState } from 'react'
import { draftOf, type Bot } from '@shared/models'
import { Icon } from '../../components/Icon'
import { AnchoredMenu, type MenuItem } from '../../components/Overlay'
import { useStore } from '../../store/context'
import type { BotStore } from '../../store/bot-store'
import { CommandAllowlistSheet } from './CommandAllowlistSheet'

/** How a bot treats its agent's permission requests (the host's wire values `ask` / `auto` / `full`). */
const MODES = [
  { mode: 'ask', title: 'Ask for approval', subtitle: 'Requests approval for commands and file changes.', icon: 'hand.raised' },
  { mode: 'auto', title: 'Approve for me', subtitle: 'Requests are approved once, automatically. Your agent’s own permission rules still apply.', icon: 'checkmark.shield' },
  { mode: 'full', title: 'Full access', subtitle: 'Full computer access (elevated risk).', icon: 'exclamationmark.shield' },
] as const

type Mode = (typeof MODES)[number]['mode']

const modeOf = (permission: string) => MODES.find((m) => m.mode === permission) ?? MODES[1]

function setPermission(store: BotStore, bot: Bot, mode: Mode) {
  if (bot.permission === mode) return
  void store.save({ ...draftOf(bot), permission: mode })
}

/**
 * The composer's approval-mode button (Maus's): a hand for Ask, a shield for Approve for me,
 * a warning shield for Full access. Its menu asks how the agent's actions should be approved,
 * and manages the Command allowlist (commands that skip the card in Ask).
 */
export function ApprovalModeControl({ bot }: { bot: Bot }) {
  const store = useStore()
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState(false)
  const button = useRef<HTMLButtonElement>(null)
  const current = modeOf(bot.permission)
  const items = (): MenuItem[] => [
    ...MODES.map((m) => ({
      title: m.title,
      subtitle: m.subtitle,
      icon: m.icon,
      selected: m.mode === current.mode,
      action: () => setPermission(store, bot, m.mode),
    })),
    {
      title: 'Command allowlist',
      subtitle: 'Commands that start with one of these run without asking.',
      icon: 'list.bullet',
      detail: bot.allowlist.length ? String(bot.allowlist.length) : undefined,
      divider: true,
      action: () => setEditing(true),
    },
  ]
  return (
    <>
      <button
        ref={button}
        className="press composer-approval"
        aria-label={`Approval: ${current.title}`}
        title={current.title}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
      >
        <Icon name={current.icon} size={13} weight="medium" color="var(--text)" />
      </button>
      <AnchoredMenu
        open={open}
        onClose={() => setOpen(false)}
        anchor={button}
        items={items}
        header={`How should ${store.backendName(bot.backend)} actions be approved?`}
        width={340}
      />
      <CommandAllowlistSheet bot={bot} open={editing} onClose={() => setEditing(false)} />
    </>
  )
}
