import { useRef, useState } from 'react'
import { isGroup, type Bot } from '@shared/models'
import { AnchoredMenu, type MenuItem } from '../../components/Overlay'
import { useStore } from '../../store/context'
import { MacChatButton } from './MacChatButton'

/**
 * The chat header's ⋯ menu: find in chat, the full conversation, copying the
 * transcript, the Computer panel and the bot's settings.
 */
export function ChatHeaderMenu({ bot, showComputer, withComputer, onFind, onChats, onFull, onComputer, onThreadModels, onNewSession, onSettings }: {
  bot: Bot
  showComputer: boolean
  /** The Computer panel exists for bots only, not groups. */
  withComputer: boolean
  onFind: () => void
  onChats: () => void
  onFull: () => void
  onComputer: () => void
  onThreadModels: () => void
  onNewSession: () => void
  onSettings: () => void
}) {
  const store = useStore()
  const [open, setOpen] = useState(false)
  const anchor = useRef<HTMLSpanElement>(null)

  /** The chat's back-and-forth as plain text, oldest first. */
  const copyConversation = () => {
    const name = bot.name
    const text = store
      .allEntries(bot.id)
      .filter((e) => (e.threadId ?? null) === null && (e.kind === 'user' || e.kind === 'agent') && (e.data.text ?? '').trim())
      .sort((a, b) => a.seq - b.seq)
      .map((e) => `${e.kind === 'user' ? 'You' : name}: ${e.data.text}`)
      .join('\n\n')
    if (text) void navigator.clipboard.writeText(text)
  }

  const items = (): MenuItem[] => [
    { title: 'Find in chat', icon: 'magnifyingglass', action: onFind },
    { title: 'Chats', icon: 'bubble.left', action: onChats },
    { title: 'Full conversation', icon: 'list.bullet.rectangle', action: onFull },
    { title: 'Copy conversation', icon: 'doc.on.doc', action: copyConversation },
    ...(withComputer ? [{ title: 'Computer', icon: 'display', selected: showComputer, divider: true, action: onComputer } as MenuItem] : []),
    ...(withComputer ? [{ title: 'Thread models', icon: 'arrow.triangle.branch', action: onThreadModels } as MenuItem] : []),
    { title: 'New session', icon: 'arrow.counterclockwise', action: onNewSession },
    { title: isGroup(bot) ? 'Group settings' : 'Bot settings', icon: 'gearshape', action: onSettings, divider: !withComputer },
  ]

  return (
    <>
      <span ref={anchor} style={{ display: 'flex' }}>
        <MacChatButton title="More" icon="ellipsis" direction={[0, 0]} className={open ? 'active' : undefined} onClick={() => setOpen((o) => !o)} />
      </span>
      <AnchoredMenu open={open} onClose={() => setOpen(false)} anchor={anchor} items={items} />
    </>
  )
}
