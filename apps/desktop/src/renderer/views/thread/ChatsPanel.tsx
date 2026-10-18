import type { Bot } from '@shared/models'
import { Icon } from '../../components/Icon'
import { font } from '../../lib/fonts'
import { MacChatButton } from './MacChatButton'
import { relativeTime } from './chat-items'

/** One chat of a bot or group: a conversation with its own messages and context. */
export interface ChatSummary {
  id: string
  title: string
  at: number
  replies: number
}

/**
 * The chat history of one bot or group, in the side slot (like the Computer panel):
 * open a chat to continue it on its own context, or start a new one above it.
 */
export function ChatsPanel({ bot, chats, activeChat, onOpen, onNew, close }: {
  bot: Bot
  chats: ChatSummary[]
  activeChat: string
  onOpen: (id: string) => void
  onNew: () => void
  close: () => void
}) {
  return (
    <div className="chats-panel">
      <div className="chats-head">
        <span style={{ ...font(15, 'semibold'), color: 'var(--text)' }}>Chats</span>
        <span style={{ ...font('caption'), color: 'var(--tertiary)', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{bot.name}</span>
        <button className="chats-new press" onClick={onNew}>
          <Icon name="plus" size={11} weight="semibold" />
          New chat
        </button>
        <MacChatButton title="Close" icon="chevron.right.2" direction={[1, 0]} onClick={close} />
      </div>
      <div className="chats-body">
        {chats.length === 0 ? (
          <div className="chats-empty-box">
            <Icon name="bubble.left" size={18} color="var(--tertiary)" />
            <span style={{ ...font('footnote'), color: 'var(--secondary)', textAlign: 'center' }}>
              No chats yet. {bot.name} names one right after your first message — nothing is kept until then.
            </span>
          </div>
        ) : (
          chats.map((chat) => (
            <button
              key={chat.id}
              className={`chat-item press ${chat.id === activeChat ? 'active' : ''}`}
              onClick={() => onOpen(chat.id)}
              title={chat.title}
            >
              <span className="chat-item-icon">
                <Icon name="bubble.left" size={12} color={chat.id === activeChat ? 'var(--accent)' : 'var(--secondary)'} />
              </span>
              <span className="chat-item-text">
                <span className="chat-item-title" style={font(13, 'medium')}>{chat.title}</span>
                <span className="chat-item-meta" style={font('caption')}>
                  {chat.replies} {chat.replies === 1 ? 'reply' : 'replies'} · {relativeTime.time(chat.at)}
                </span>
              </span>
              <Icon name="chevron.right" size={10} weight="semibold" color="var(--tertiary)" className="chat-item-go" />
            </button>
          ))
        )}
      </div>
    </div>
  )
}
