import { useState, type ReactNode } from 'react'
import { folderName, isGroup, isWorking, type Bot } from '@shared/models'
import { AgentIcon } from '../../components/AgentIcon'
import { BotAvatar, GroupAvatar, useWake } from '../../components/Avatar'
import { CardSection, IconButton } from '../../components/Controls'
import { Dialog } from '../../components/Overlay'
import { Icon } from '../../components/Icon'
import { font } from '../../lib/fonts'
import { useModel } from '../../lib/observable'
import { useStore } from '../../store/context'
import { BotSettingsPanel } from '../bots/BotEditorView'
import { GroupEditorView } from '../bots/GroupEditorView'
import { ComputerBadge } from '../ComputerBadge'
import { BotPanel } from './BotPanel'
import { MacChatButton } from './MacChatButton'
import '../bots/bots.css'

const isMac = window.codync.platform === 'darwin'

/** The inspector beside a conversation: the computer, the agent, and the bot's settings. */
export function DetailsPanel({ botId, routineId, editing, setEditing, share, openPlugins, close }: {
  botId: string
  routineId: string | null
  editing: boolean
  setEditing: (on: boolean) => void
  share: () => void
  openPlugins?: () => void
  close: () => void
}) {
  const store = useModel(useStore())
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
        <PanelTitle>Details</PanelTitle>
        <span style={{ flex: 1 }} />
        <RoundButton label="All group settings" symbol="gearshape" action={() => setEditing(true)} />
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
            <div className="panel-slide from-trailing" style={{ height: '100%' }}>
              <GroupEditorView group={bot} onCancel={() => setEditing(false)} onSaved={() => setEditing(false)} />
            </div>
          ) : (
            <GroupDetails group={bot} onSettings={() => setEditing(true)} />
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

function PanelTitle({ children }: { children: ReactNode }) {
  return <span style={{ ...font(15, 'semibold'), color: 'var(--text)', paddingLeft: 4 }}>{children}</span>
}

/** A single circular surface, shared with the floating transcript controls. */
function RoundButton({ label, symbol, action }: { label: string; symbol: string; action: () => void }) {
  return <MacChatButton title={label} icon={symbol} direction={symbol === 'chevron.right.2' ? [1, 0] : [0, 0]} onClick={action} />
}

/** The group at a glance, like a bot's Details: who's in it, its rules and all settings. */
function GroupDetails({ group, onSettings }: { group: Bot; onSettings: () => void }) {
  const store = useStore()
  const [adding, setAdding] = useState(false)
  const [query, setQuery] = useState('')
  const [removeTarget, setRemoveTarget] = useState<Bot | null>(null)
  const wake = useWake(1800)
  const members = store.members(group)
  const full = group.members.length >= 50
  const q = query.trim().toLowerCase()
  // Hidden bots can still join a room: the roster alone would hide them from this list.
  const candidates = [...store.bots.values()].filter((b) => !isGroup(b) && !group.members.includes(b.id) && (!q || b.name.toLowerCase().includes(q)))
  const add = (id: string) => {
    if (full) return
    void store.updateGroup({
      kind: 'group',
      id: group.id,
      name: group.name,
      description: group.description,
      members: [...group.members, id],
      leader: group.leader,
      instructions: group.instructions,
      workspace: group.workspace,
      memberSettings: group.memberSettings,
      pinned: group.pinned,
    })
    setQuery('')
  }
  const remove = (bot: Bot) => {
    if (group.members.length <= 2) setRemoveTarget(bot)
    else store.removeMember(group, bot.id)
  }
  return (
    <div className="bot-panel">
      <div className="bot-panel-head group-details-head">
        <GroupAvatar members={members} size={56} wake={wake} />
        <span className="bot-panel-name">{group.name}</span>
        {group.description.trim() ? <span className="group-details-about">{group.description}</span> : null}
      </div>
      <div className="bot-panel-body group-details-body">
        <CardSection title="Members" accessory={<span style={{ ...font('caption'), color: 'var(--tertiary)' }}>{members.length}{full ? ' · 50 max' : ''}</span>}>
          {members.map((bot) => (
            <MemberRow key={bot.id} bot={bot} group={group} wake={wake} onRemove={() => remove(bot)} />
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
          {candidates.length === 0 ? (
            <span style={{ ...font('caption'), color: 'var(--tertiary)', padding: '4px 0' }}>
              {q ? `No bots match “${query}”.` : 'Every bot is already in this group.'}
            </span>
          ) : null}
        </>
      ) : !full ? (
        <button className="member-row press" onClick={() => setAdding(true)}>
          <span style={{ width: 32, display: 'grid', placeItems: 'center' }}>
            <Icon name="plus" size={16} color="var(--secondary)" />
          </span>
          <span className="member-name" style={{ color: 'var(--secondary)' }}>Add bots</span>
        </button>
      ) : null}
        </CardSection>
        <CardSection title="Instructions">
          <span className="group-details-instructions">{group.instructions.trim() || 'No room rules yet.'}</span>
        </CardSection>
        <CardSection>
          <button className="group-settings-row press" onClick={onSettings}>
            <Icon name="gearshape" size={14} color="var(--secondary)" />
            <span style={{ flex: 1, textAlign: 'left' }}>All settings</span>
            <Icon name="chevron.right" size={10} weight="semibold" color="var(--tertiary)" />
          </button>
        </CardSection>
      </div>
      <Dialog
        open={removeTarget !== null}
        title="Delete this group?"
        message={`${removeTarget?.name ?? 'That bot'} can't leave — a group needs at least 2 bots. Delete the group instead?`}
        actions={[
          {
            title: 'Delete group',
            destructive: true,
            action: () => store.delete(group),
          },
        ]}
        onClose={() => setRemoveTarget(null)}
      />
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

/** One member of a room: opening it swaps to that bot's own chat; hovering wakes its face. */
function MemberRow({ bot, group, wake, onRemove }: { bot: Bot; group: Bot; wake: boolean; onRemove: () => void }) {
  const store = useStore()
  const [hover, setHover] = useState(false)
  return (
    <div
      className="member-row removable"
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
    >
      <button className="member-open press" title={`Open ${bot.name}'s own chat`} onClick={() => store.setSelection(bot.id)}>
        <BotAvatar bot={bot} size={32} wake={wake || hover} />
        <span style={{ display: 'flex', flexDirection: 'column', gap: 1, minWidth: 0, flex: 1 }}>
          <span className="member-name">{bot.name}</span>
          {group.leader === bot.id ? <span className="member-name" style={{ ...font('compactSecondary'), color: 'var(--secondary)' }}>Leads the room</span> : null}
          {isWorking(bot) ? <span className="member-name" style={{ ...font('compactSecondary'), color: 'var(--secondary)' }}>{bot.activity || 'Working…'}</span> : null}
        </span>
      </button>
      <IconButton title={`Remove ${bot.name} from the group`} icon="xmark" onClick={onRemove} />
    </div>
  )
}
