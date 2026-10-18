import { useState, type ReactNode } from 'react'
import { folderName, isGroup, isWorking, type Bot } from '@shared/models'
import { AgentIcon } from '../../components/AgentIcon'
import { BotAvatar } from '../../components/Avatar'
import { CardSection, IconButton } from '../../components/Controls'
import { Icon } from '../../components/Icon'
import { font } from '../../lib/fonts'
import { useStore } from '../../store/context'
import { BotSettingsPanel } from '../bots/BotEditorView'
import { GroupEditorView } from '../bots/GroupEditorView'
import { ComputerBadge } from '../ComputerBadge'
import { BotPanel } from './BotPanel'
import { MacChatButton } from './MacChatButton'
import '../bots/bots.css'

const isMac = window.codync.platform === 'darwin'

/** The inspector beside a conversation: the computer, the agent, and the bot's settings. */
/** One chat of a bot or group: a thread root that runs on its own context. */
export interface ChatSummary {
  id: string
  title: string
  at: number
  replies: number
}

export function DetailsPanel({ botId, routineId, editing, setEditing, share, openPlugins, close, chats, activeChat, onOpenChat, onNewChat }: {
  botId: string
  routineId: string | null
  editing: boolean
  setEditing: (on: boolean) => void
  share: () => void
  openPlugins?: () => void
  close: () => void
  chats: ChatSummary[]
  activeChat: string | null
  onOpenChat: (id: string) => void
  onNewChat: () => void
}) {
  const store = useStore()
  const bot = store.bots.get(botId) ?? null
  const [returnedFromSettings, setReturnedFromSettings] = useState(false)
  /** Which settings section a Details row asked for (nonce re-mounts the form). */
  const [settingsAt, setSettingsAt] = useState<{ section: string; nonce: number } | null>(null)
  const openSettings = (section: string) => {
    setSettingsAt({ section, nonce: Date.now() })
    setEditing(true)
  }
  let header: ReactNode
  if (editing) {
    header = (
      <>
        <RoundButton label="Back to details" symbol="chevron.left" action={() => {
          setReturnedFromSettings(true)
          setEditing(false)
        }} />
        <PanelTitle>Settings</PanelTitle>
        <span style={{ flex: 1 }} />
      </>
    )
  } else if (bot && isGroup(bot)) {
    header = (
      <>
        <PanelTitle>Members</PanelTitle>
        <span style={{ ...font(13), color: 'var(--tertiary)', flex: 1 }}>{bot.members.length}</span>
      </>
    )
  } else {
    header = (
      <>
        <PanelTitle>Details</PanelTitle>
        <span style={{ flex: 1 }} />
        <RoundButton label="Create template" symbol="square.and.arrow.up" action={share} />
        <RoundButton label="Bot settings" symbol="gearshape" action={() => setEditing(true)} />
      </>
    )
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--background)' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, height: 54, padding: '0 12px', flex: 'none' }}>
        {header}
        <RoundButton label="Close details" symbol="chevron.right.2" action={close} />
      </div>
      <div style={{ flex: 1, minHeight: 0, position: 'relative', overflow: 'hidden' }}>
        {bot && isGroup(bot) ? (
          editing ? (
            <div className="panel-slide from-trailing" style={{ height: '100%', overflowY: 'auto' }}>
              <GroupEditorView group={bot} />
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
              <div style={{ padding: '0 20px' }}>
                <ChatsCard chats={chats} activeChat={activeChat} onOpenChat={onOpenChat} onNewChat={onNewChat} />
              </div>
              <GroupMembersList group={bot} />
            </div>
          )
        ) : editing ? (
          <div className="panel-slide from-trailing" style={{ height: '100%', overflowY: 'auto' }}>
            <BotSettingsPanel key={`${botId}:${settingsAt?.nonce ?? 0}`} botId={botId} opened={settingsAt?.section} />
          </div>
        ) : bot ? (
          <div className={returnedFromSettings ? 'panel-slide from-leading' : undefined} style={{ height: '100%' }}>
            <BotPanel
              bot={bot}
              routineId={routineId}
              openPlugins={openPlugins}
              onSettings={openSettings}
              details={<>
                <ChatsCard chats={chats} activeChat={activeChat} onOpenChat={onOpenChat} onNewChat={onNewChat} />
                <ComputerSummary botId={botId} />
                <AgentSummary bot={bot} />
              </>}
            />
          </div>
        ) : null}
      </div>
    </div>
  )
}

/** Every chat of this bot or group; open one to continue it on its own context. */
function ChatsCard({ chats, activeChat, onOpenChat, onNewChat }: {
  chats: ChatSummary[]
  activeChat: string | null
  onOpenChat: (id: string) => void
  onNewChat: () => void
}) {
  return (
    <div className="chats-card">
      <div className="chats-head">
        <span className="chats-label" style={font(13, 'semibold')}>Chats</span>
        <span style={{ flex: 1 }} />
        <button className="chats-new press" onClick={onNewChat}>
          <Icon name="plus" size={11} weight="semibold" />
          New chat
        </button>
      </div>
      {chats.length === 0 ? (
        <span className="chats-empty" style={font('footnote')}>New chat starts a fresh context and shows up here.</span>
      ) : (
        chats.map((chat) => (
          <button key={chat.id} className={`chat-row press ${chat.id === activeChat ? 'active' : ''}`} onClick={() => onOpenChat(chat.id)}>
            <Icon name="bubble.left" size={11} color="var(--secondary)" />
            <span className="chat-row-title" style={font(13)}>{chat.title}</span>
            <span className="chat-row-meta" style={font('caption')}>{chat.replies} {chat.replies === 1 ? 'reply' : 'replies'}</span>
          </button>
        ))
      )}
    </div>
  )
}

function PanelTitle({ children }: { children: ReactNode }) {
  return <span style={{ ...font(15, 'semibold'), color: 'var(--text)', paddingLeft: 4 }}>{children}</span>
}

/** A single circular surface, shared with the floating transcript controls. */
function RoundButton({ label, symbol, action }: { label: string; symbol: string; action: () => void }) {
  return <MacChatButton title={label} icon={symbol} direction={symbol === 'chevron.right.2' ? [1, 0] : [0, 0]} onClick={action} />
}

/** A group's bots (Grok Bot's member list); clicking one opens its own chat. */
function GroupMembersList({ group }: { group: Bot }) {
  const store = useStore()
  const [adding, setAdding] = useState(false)
  const [query, setQuery] = useState('')
  const members = store.members(group)
  const q = query.trim().toLowerCase()
  const candidates = store.roster.filter((b) => !isGroup(b) && !group.members.includes(b.id) && (!q || b.name.toLowerCase().includes(q)))
  const add = (id: string) => {
    void store.updateGroup({
      kind: 'group',
      id: group.id,
      name: group.name,
      description: group.description,
      members: [...group.members, id],
      leader: group.leader,
      instructions: group.instructions,
      workspace: group.workspace,
      pinned: group.pinned,
    })
    setQuery('')
  }
  return (
    <div style={{ height: '100%', overflowY: 'auto', padding: '0 20px' }}>
      {members.map((bot) => (
        <div key={bot.id} className="member-row removable">
          <button className="member-open press" title={`Open ${bot.name}'s own chat`} onClick={() => store.setSelection(bot.id)}>
            <BotAvatar bot={bot} size={32} />
            <span style={{ display: 'flex', flexDirection: 'column', gap: 1, minWidth: 0, flex: 1 }}>
              <span className="member-name">{bot.name}</span>
              {group.leader === bot.id ? <span className="member-name" style={{ ...font('compactSecondary'), color: 'var(--secondary)' }}>Leads the room</span> : null}
              {isWorking(bot) ? <span className="member-name" style={{ ...font('compactSecondary'), color: 'var(--secondary)' }}>{bot.activity || 'Working…'}</span> : null}
            </span>
          </button>
          {group.members.length > 1 ? <IconButton title={`Remove ${bot.name} from the group`} icon="xmark" onClick={() => store.removeMember(group, bot.id)} /> : null}
        </div>
      ))}
      {adding ? (
        <>
          <input
            className="field-box fade-in"
            style={{ margin: '6px 0' }}
            autoFocus
            value={query}
            placeholder="Search bots"
            aria-label="Search bots to add"
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.nativeEvent.isComposing && candidates[0]) add(candidates[0].id)
              if (e.key === 'Escape') setAdding(false)
            }}
          />
          {candidates.map((bot) => (
            <button key={bot.id} className="member-row press fade-in" aria-label={`Add ${bot.name}`} onClick={() => add(bot.id)}>
              <BotAvatar bot={bot} size={32} animated={false} />
              <span className="member-name" style={{ color: 'var(--secondary)' }}>{bot.name}</span>
              <Icon name="plus.circle" size={18} color="var(--secondary)" />
            </button>
          ))}
        </>
      ) : candidates.length ? (
        <button className="member-row press" onClick={() => setAdding(true)}>
          <span style={{ width: 32, display: 'grid', placeItems: 'center' }}>
            <Icon name="plus" size={16} color="var(--secondary)" />
          </span>
          <span className="member-name" style={{ color: 'var(--secondary)' }}>Add bots</span>
        </button>
      ) : null}
    </div>
  )
}

function ComputerSummary({ botId }: { botId: string }) {
  const store = useStore()
  const screen = store.screen
  const ready = !store.isOffline && !!screen?.enabled && screen.connected && screen.capture
  const symbol = !screen?.enabled ? 'power' : !screen.connected ? 'arrow.triangle.2.circlepath' : !screen.capture ? 'exclamationmark.circle' : screen.agentBot === botId ? 'cursorarrow.motionlines' : 'checkmark.circle'
  const status = !screen?.enabled
    ? 'Remote screen is off'
    : !screen.connected
      ? 'Connecting to computer…'
      : !screen.capture
        ? 'Screen recording permission needed'
        : screen.agentBot === botId
          ? `This bot is using your ${isMac ? 'Mac' : 'computer'}`
          : 'Ready to connect'
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16, padding: 16, background: 'var(--surface)', borderRadius: 16 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <ComputerBadge computer={store.computer} size={32} />
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0 }}>
          <span style={{ ...font(15, 'medium'), color: 'var(--text)', overflow: 'hidden', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical' }}>{store.hostName}</span>
          <span style={{ ...font(13), color: 'var(--secondary)' }}>Remote screen</span>
        </div>
      </div>
      {!store.isOffline ? (
        <span style={{ display: 'flex', alignItems: 'center', gap: 6, ...font(13), color: 'var(--text)' }}>
          <Icon name={symbol} size={13} />
          {status}
        </span>
      ) : null}
      {ready ? <div style={{ ...font(12), color: 'var(--secondary)', marginTop: -8 }}>View and control this {isMac ? 'Mac' : 'computer'} from your iPhone.</div> : null}
    </div>
  )
}

function InfoRow({ label, children, title }: { label: string; children: ReactNode; title?: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, ...font(13) }}>
      <span style={{ color: 'var(--secondary)' }}>{label}</span>
      <span style={{ flex: 1, minWidth: 8 }} />
      <span title={title} style={{ color: 'var(--text)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', display: 'flex', alignItems: 'center', gap: 6, minWidth: 0 }}>
        {children}
      </span>
    </div>
  )
}

function AgentSummary({ bot }: { bot: Bot }) {
  const store = useStore()
  return (
    <CardSection title="Agent" footer={bot.description || null}>
      <InfoRow label="Runtime">
        <AgentIcon registry={store.hello?.backends.find((b) => b.id === bot.backend)?.registry ?? bot.backend} size={14} />
        <span>{store.backendName(bot.backend)}</span>
      </InfoRow>
      <InfoRow label={bot.managedWorkspace ? 'Workspace' : 'Folder'} title={bot.managedWorkspace ? 'Personal workspace, managed by Codync' : bot.cwd}>
        {bot.managedWorkspace ? 'Personal' : folderName(bot)}
      </InfoRow>
      {bot.model ? <InfoRow label="Model">{bot.model}</InfoRow> : null}
    </CardSection>
  )
}
