import { useState } from 'react'
import type { Bot } from '@shared/models'
import { Icon } from '../../components/Icon'
import { AnchoredMenu, Dialog, useContextMenu } from '../../components/Overlay'
import { font } from '../../lib/fonts'
import { MacChatButton } from './MacChatButton'
import { relativeTime } from './chat-items'
import './bot-panel.css'

/** One chat of a bot or group: a conversation with its own messages and context. */
export interface ChatSummary {
  id: string
  title: string
  at: number
  /** Its last message in one line; empty until something has been said. */
  preview: string
}

/**
 * The chat history of one bot or group, in the side slot (like the Computer panel):
 * open a chat to continue it on its own context, or start a new one above it.
 * Right-click a chat to delete it.
 */
export function ChatsPanel({ bot, chats, activeChat, onOpen, onNew, onDelete, close }: {
  bot: Bot
  chats: ChatSummary[]
  activeChat: string
  onOpen: (id: string) => void
  onNew: () => void
  onDelete: (id: string) => void
  close: () => void
}) {
  const menu = useContextMenu()
  const [menuChat, setMenuChat] = useState<ChatSummary | null>(null)
  const [confirming, setConfirming] = useState<ChatSummary | null>(null)
  const closeMenu = () => {
    menu.close()
    setMenuChat(null)
  }
  return (
    <div className="chats-panel">
      <div className="chats-head">
        <div className="chats-heading">
          <span style={{ ...font(15, 'semibold'), color: 'var(--text)' }}>Chats</span>
          <span className="chats-heading-sub" style={font('caption')}>{bot.name}</span>
        </div>
        <MacChatButton title="Close" icon="chevron.right.2" direction={[1, 0]} onClick={close} />
      </div>
      <div className="chats-new-wrap">
        <button className="chats-new press" onClick={onNew}>
          <span className="chats-new-icon">
            <Icon name="plus" size={12} weight="semibold" />
          </span>
          <span style={font(13, 'medium')}>New chat</span>
        </button>
      </div>
      <div className="chats-body">
        {chats.length === 0 ? (
          <div className="chats-empty-box">
            <span className="chats-empty-icon">
              <Icon name="bubble.left" size={17} color="var(--secondary)" />
            </span>
            <span style={{ ...font(13, 'semibold'), color: 'var(--text)' }}>No chats yet</span>
            <span style={{ ...font('footnote'), color: 'var(--secondary)', maxWidth: 260, lineHeight: 1.5 }}>
              {bot.name} names each chat after your first message. Nothing is kept until then.
            </span>
          </div>
        ) : (
          <div className="chats-list">
            {groupChats(chats).map((section) => (
              <div className="chats-section" key={section.label}>
                <span className="chats-section-label">{section.label}</span>
                {section.items.map((chat) => (
                  <button
                    key={chat.id}
                    className={`chat-item press ${chat.id === activeChat ? 'active' : ''}`}
                    onClick={() => onOpen(chat.id)}
                    onContextMenu={(e) => {
                      e.preventDefault()
                      if (chat.id === 'main') return
                      setMenuChat(chat)
                      menu.onContextMenu(e)
                    }}
                    title={chat.id === 'main' ? chat.title : `${chat.title} — right-click to delete`}
                  >
                    <span className="chat-item-copy">
                      <span className="chat-item-top">
                        <span
                          className="chat-item-title"
                          style={font(13, chat.id === activeChat ? 'semibold' : 'medium')}
                        >
                          {chat.title}
                        </span>
                        <span className="chat-item-time" style={font('caption')}>{relativeTime.short(chat.at)}</span>
                      </span>
                      <span className="chat-item-preview" style={font('compactSecondary')}>
                        {chat.preview || 'No messages yet'}
                      </span>
                    </span>
                  </button>
                ))}
              </div>
            ))}
          </div>
        )}
      </div>
      <AnchoredMenu
        open={menu.open}
        onClose={closeMenu}
        point={menu.point}
        items={() => {
          const chat = menuChat
          return chat ? [
            { title: 'Open chat', icon: 'bubble.left', action: () => onOpen(chat.id) },
            { title: 'Delete chat', icon: 'trash', destructive: true, divider: true, action: () => setConfirming(chat) },
          ] : []
        }}
      />
      <Dialog
        open={confirming !== null}
        title="Delete chat?"
        message={confirming ? `“${confirming.title}” and its messages are removed from this computer.` : null}
        actions={confirming ? [{
          title: 'Delete chat',
          destructive: true,
          action: () => {
            onDelete(confirming.id)
            setConfirming(null)
          },
        }] : []}
        onClose={() => setConfirming(null)}
      />
    </div>
  )
}

/** Chats newest first, split into the quiet date groups the list shows. */
function groupChats(chats: ChatSummary[]) {
  const sections: { label: string; items: ChatSummary[] }[] = []
  for (const chat of chats) {
    const label = relativeTime.section(chat.at)
    const last = sections[sections.length - 1]
    if (last?.label === label) last.items.push(chat)
    else sections.push({ label, items: [chat] })
  }
  return sections
}
