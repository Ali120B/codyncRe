import { useState, type ReactNode } from 'react'
import type { Bot } from '@shared/models'
import { BotAvatar, useWake } from '../../components/Avatar'
import { MemoryCard } from '../bots/MemoryView'
import { SkillsTab, VaultTab } from '../bots/CapabilityTabs'
import { RoutinesView } from '../routines/RoutinesView'
import { DetailsRows } from './DetailsRows'
import './bot-panel.css'

const TABS = [
  { id: 'details', label: 'Details' },
  { id: 'memory', label: 'Memory' },
  { id: 'library', label: 'Library' },
  { id: 'skills', label: 'Skills' },
  { id: 'routines', label: 'Routines' },
  { id: 'vault', label: 'Vault' },
] as const

type TabId = (typeof TABS)[number]['id']

/** One bot's quick settings beside its chat: avatar, name, and one tab at a time. */
export function BotPanel({ bot, routineId, details, openPlugins, onSettings }: {
  bot: Bot
  routineId: string | null
  /** The Details tab's content: the computer card and the agent card. */
  details: ReactNode
  /** Opens Extensions to add a skill or an app; links hide when the owner can't. */
  openPlugins?: () => void
  /** Opens the settings accordion at one section (the Details tab's rows). */
  onSettings: (section: string) => void
}) {
  const [tab, setTab] = useState<TabId>('details')
  const wake = useWake(1600)
  return (
    <div className="bot-panel">
      <div className="bot-panel-head">
        <BotAvatar bot={bot} size={56} wake={wake} />
        <span className="bot-panel-name">{bot.name}</span>
        <div className="bot-panel-tabs" role="tablist">
          {TABS.map((t) => (
            <button key={t.id} role="tab" aria-pressed={tab === t.id} className="press bot-panel-tab" onClick={() => setTab(t.id)}>{t.label}</button>
          ))}
        </div>
      </div>
      <div className="bot-panel-body">
        {tab === 'details' ? (
          <div className="bot-panel-details">
            <DetailsRows bot={bot} openSettings={onSettings} />
            {details}
          </div>
        )
          : tab === 'memory' ? <MemoryCard botId={bot.id} />
          : tab === 'library' ? <p className="bot-panel-note">Pages, files, and apps {bot.name} makes show up here.</p>
          : tab === 'skills' ? <SkillsTab bot={bot} openPlugins={openPlugins} />
          : tab === 'routines' ? <RoutinesView botId={bot.id} initialId={routineId} />
          : <VaultTab bot={bot} openPlugins={openPlugins} />}
      </div>
    </div>
  )
}
