import type { Bot } from '@shared/models'
import { Icon } from '../../components/Icon'
import { font } from '../../lib/fonts'
import { MacChatButton } from './MacChatButton'

/** One chat of a bot or group: a thread root that runs on its own context. */
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
  activeChat: string | null
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
          <span className="chats-empty" style={font('footnote')}>
            New chat starts a fresh context and shows up here. Nothing is kept until you send a message.
          </span>
        ) : (
          chats.map((chat) => (
            <button key={chat.id} className={`chat-row press ${chat.id === activeChat ? 'active' : ''}`} onClick={() => onOpen(chat.id)}>
              <Icon name="bubble.left" size={11} color="var(--secondary)" />
              <span className="chat-row-title" style={font(13)}>{chat.title}</span>
              <span className="chat-row-meta" style={font('caption')}>{chat.replies} {chat.replies === 1 ? 'reply' : 'replies'}</span>
            </button>
          ))
        )}
      </div>
    </div>
  )
}
