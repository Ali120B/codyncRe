import { useRef, useState } from 'react'
import { isGroup, isWorkingIn, type Bot } from '@shared/models'
import { BotAvatar } from '../../components/Avatar'
import { Icon } from '../../components/Icon'
import { font } from '../../lib/fonts'
import type { BotStore } from '../../store/bot-store'
import { SkillsTab, VaultTab } from '../bots/CapabilityTabs'
import { MacChatButton } from '../thread/MacChatButton'
import { RoutinesView } from '../routines/RoutinesView'
import { BrowserTab } from './BrowserTab'
import { CloudTab } from './CloudTab'
import { ComputerTab } from './ComputerTab'
import { FilesTab } from './FilesTab'
import { GroupFilesTab } from './GroupFilesTab'
import './computer-panel.css'

type TabId = 'computer' | 'browser' | 'files' | 'routines' | 'cloud' | 'skills' | 'vault'

interface Tab {
  id: TabId
  title: string
  icon: string
}

const BOT_TABS: Tab[] = [
  { id: 'computer', title: 'Computer', icon: 'display' },
  { id: 'browser', title: 'Browser', icon: 'safari' },
  { id: 'files', title: 'Files', icon: 'folder' },
  { id: 'routines', title: 'Routines', icon: 'arrow.clockwise' },
  { id: 'cloud', title: 'Cloud', icon: 'cloud' },
]

/** A group has no desktop of its own: its members' browser, files and settings, the room's routines. */
const GROUP_TABS: Tab[] = [
  { id: 'browser', title: 'Browser', icon: 'safari' },
  { id: 'files', title: 'Files', icon: 'folder' },
  { id: 'skills', title: 'Skills', icon: 'list.bullet' },
  { id: 'vault', title: 'Vault', icon: 'key' },
  { id: 'routines', title: 'Routines', icon: 'arrow.clockwise' },
]

const PER_MEMBER: TabId[] = ['browser', 'skills', 'vault']

/** The panel beside a chat: the bot's computer, browser, files and routines (Maus's ComputerPanel). */
export function ComputerPanel({ bot, store, width, onWidth, settings, close }: {
  bot: Bot
  store: BotStore
  width: number
  onWidth: (width: number) => void
  settings: () => void
  close: () => void
}) {
  const group = isGroup(bot)
  const members = group ? store.members(bot) : []
  const [memberId, setMemberId] = useState('')
  const [tab, setTab] = useState<TabId>(() => (group ? 'browser' : 'computer'))
  const [resizing, setResizing] = useState(false)
  const drag = useRef<{ x: number; width: number } | null>(null)
  const member = members.find((m) => m.id === memberId) ?? members[0] ?? null
  const tabs = group ? GROUP_TABS : BOT_TABS
  const room = bot.memberSettings ?? {}

  return (
    <div className={`computer-panel ${resizing ? 'resizing' : ''}`} style={{ width }}>
      <div
        className="computer-resize"
        role="separator"
        aria-label="Resize panel"
        onPointerDown={(e) => {
          ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
          drag.current = { x: e.clientX, width }
          setResizing(true)
        }}
        onPointerMove={(e) => {
          if (!drag.current) return
          const next = drag.current.width - (e.clientX - drag.current.x)
          onWidth(Math.min(Math.max(next, 340), Math.max(340, window.innerWidth - 480)))
        }}
        onPointerUp={() => {
          drag.current = null
          setResizing(false)
        }}
      />
      <div className="computer-head">
        <span style={{ ...font(15, 'semibold') }}>Computer</span>
        <span style={{ ...font('caption'), color: 'var(--tertiary)', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{bot.name}</span>
        <MacChatButton title={group ? 'Group settings' : `${bot.name} settings`} icon="gearshape" direction={[0, 0]} onClick={settings} />
        <MacChatButton title="Close" icon="chevron.right.2" direction={[1, 0]} onClick={close} />
      </div>
      <div className="computer-tabs" role="tablist">
        {tabs.map((item) => (
          <button
            key={item.id}
            role="tab"
            aria-selected={tab === item.id}
            className={`computer-tab ${tab === item.id ? 'active' : ''}`}
            onClick={() => setTab(item.id)}
          >
            <Icon name={item.icon} size={12} weight="semibold" />
            {item.title}
          </button>
        ))}
      </div>
      {group && member && PER_MEMBER.includes(tab) && members.length > 1 ? (
        <MemberPicker members={members} value={member.id} onChange={setMemberId} />
      ) : null}
      <div className="computer-body">
        {group ? (
          <>
            {tab === 'browser' && member ? (
              room[member.id]?.browser ? (
                <BrowserTab bot={member} store={store} working={isWorkingIn(member, bot.id, null)} />
              ) : (
                <div className="computer-pad">
                  <p className="computer-hint" style={font('footnote')}>
                    The browser is off for {member.name} in this room. Turn it on in the group's settings.
                  </p>
                </div>
              )
            ) : null}
            {tab === 'files' ? <GroupFilesTab group={bot} store={store} /> : null}
            {tab === 'skills' && member ? <SkillsTab bot={member} /> : null}
            {tab === 'vault' && member ? <VaultTab bot={member} /> : null}
            {tab === 'routines' ? <RoutinesView botId={bot.id} initialId={null} /> : null}
          </>
        ) : (
          <>
            {tab === 'computer' ? <ComputerTab bot={bot} store={store} /> : null}
            {tab === 'browser' ? <BrowserTab bot={bot} store={store} /> : null}
            {tab === 'files' ? <FilesTab bot={bot} store={store} /> : null}
            {tab === 'routines' ? <RoutinesView botId={bot.id} initialId={null} /> : null}
            {tab === 'cloud' ? <CloudTab store={store} /> : null}
          </>
        )}
      </div>
    </div>
  )
}

/** One member's browser, skills or apps: the tabs that are per bot inside a group. */
function MemberPicker({ members, value, onChange }: { members: Bot[]; value: string; onChange: (id: string) => void }) {
  return (
    <div className="computer-members" role="tablist" aria-label="Group member">
      {members.map((m) => (
        <button
          key={m.id}
          role="tab"
          aria-selected={m.id === value}
          className={`computer-member ${m.id === value ? 'active' : ''}`}
          onClick={() => onChange(m.id)}
          title={m.name}
        >
          <BotAvatar bot={m} size={16} animated={false} />
          {m.name}
        </button>
      ))}
    </div>
  )
}
