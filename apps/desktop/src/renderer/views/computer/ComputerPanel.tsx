import { useRef, useState } from 'react'
import type { Bot } from '@shared/models'
import { Icon } from '../../components/Icon'
import { font } from '../../lib/fonts'
import type { BotStore } from '../../store/bot-store'
import { MacChatButton } from '../thread/MacChatButton'
import { RoutinesView } from '../routines/RoutinesView'
import { AndroidTab } from './AndroidTab'
import { BrowserTab } from './BrowserTab'
import { CloudTab } from './CloudTab'
import { ComputerTab } from './ComputerTab'
import { FilesTab } from './FilesTab'
import './computer-panel.css'

type TabId = 'computer' | 'browser' | 'files' | 'routines' | 'android' | 'cloud'

const TABS: { id: TabId; title: string; icon: string }[] = [
  { id: 'computer', title: 'Computer', icon: 'display' },
  { id: 'browser', title: 'Browser', icon: 'safari' },
  { id: 'files', title: 'Files', icon: 'folder' },
  { id: 'routines', title: 'Routines', icon: 'arrow.clockwise' },
  { id: 'android', title: 'Android', icon: 'iphone' },
  { id: 'cloud', title: 'Cloud', icon: 'cloud' },
]

/** The panel beside a chat: the bot's computer, browser, files and routines (Maus's ComputerPanel). */
export function ComputerPanel({ bot, store, width, onWidth, settings, close }: {
  bot: Bot
  store: BotStore
  width: number
  onWidth: (width: number) => void
  settings: () => void
  close: () => void
}) {
  const [tab, setTab] = useState<TabId>('computer')
  const [resizing, setResizing] = useState(false)
  const drag = useRef<{ x: number; width: number } | null>(null)

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
        <MacChatButton title={`${bot.name} settings`} icon="gearshape" direction={[0, 0]} onClick={settings} />
        <MacChatButton title="Close" icon="chevron.right.2" direction={[1, 0]} onClick={close} />
      </div>
      <div className="computer-tabs" role="tablist">
        {TABS.map((item) => (
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
      <div className="computer-body">
        {tab === 'computer' ? <ComputerTab bot={bot} store={store} /> : null}
        {tab === 'browser' ? <BrowserTab bot={bot} store={store} /> : null}
        {tab === 'files' ? <FilesTab bot={bot} store={store} /> : null}
        {tab === 'routines' && bot.kind !== 'group' ? <RoutinesView botId={bot.id} initialId={null} /> : null}
        {tab === 'android' ? <AndroidTab bot={bot} store={store} /> : null}
        {tab === 'cloud' ? <CloudTab store={store} /> : null}
      </div>
    </div>
  )
}
