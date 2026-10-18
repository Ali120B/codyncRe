import { useState, type ReactNode } from 'react'
import { draftOf, type Bot, type BotDraft } from '@shared/models'
import { BotAvatar } from '../../components/Avatar'
import { Switch } from '../../components/Controls'
import { useStore } from '../../store/context'
import { usePlugins } from '../bots/drafts'
import { MemoryCard } from '../bots/MemoryView'
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
  return (
    <div className="bot-panel">
      <div className="bot-panel-head">
        <BotAvatar bot={bot} size={56} />
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

/** Saves one change onto the bot through the host's updateBot. */
function useBotChange(bot: Bot) {
  const store = useStore()
  return (change: Partial<BotDraft>) => void store.save({ ...draftOf(bot), ...change })
}

/** What this bot can do: every installed skill, switched on or off for the bot alone. */
function SkillsTab({ bot, openPlugins }: { bot: Bot; openPlugins?: () => void }) {
  const plugins = usePlugins(useStore())
  const change = useBotChange(bot)
  const skills = plugins?.skills ?? []
  const on = bot.skills ?? []
  const set = (ids: string[]) => change({ skills: ids })
  return (
    <div className="bot-panel-section">
      <div className="bot-panel-head-row">
        <span className="bot-panel-label">Skills</span>
        {openPlugins ? <button className="press bot-panel-link" onClick={openPlugins}>Add skill</button> : null}
      </div>
      {!skills.length ? <p className="bot-panel-note">No skills yet.</p> : skills.map((s) => (
        <div className="bot-panel-row" key={s.id}>
          <span className="bot-panel-rowtext">
            <span className="bot-panel-row-name">{s.name}</span>
            {s.description ? <span className="bot-panel-row-detail">{s.description}</span> : null}
          </span>
          <Switch on={on.includes(s.id)} onChange={(use) => set(use ? [...on, s.id] : on.filter((x) => x !== s.id))} />
        </div>
      ))}
      <p className="bot-panel-foot">Things {bot.name} has learned to do. Switch one off and {bot.name} stops using it.</p>
    </div>
  )
}

/** What this bot is allowed to use: the connected apps it gets, nothing else. */
function VaultTab({ bot, openPlugins }: { bot: Bot; openPlugins?: () => void }) {
  const plugins = usePlugins(useStore())
  const change = useBotChange(bot)
  const apps = plugins?.connectors ?? []
  const on = bot.connectors ?? []
  // The master switch keeps the bot's own picks while it's off.
  const [stash, setStash] = useState<string[]>([])
  const set = (ids: string[]) => change({ connectors: ids })
  const toggleAll = (use: boolean) => {
    if (use) set(stash.length ? stash : apps.map((a) => a.id))
    else {
      setStash(on)
      set([])
    }
  }
  return (
    <div className="bot-panel-section">
      <p className="bot-panel-intro">What {bot.name} is allowed to use. It only gets what you switch on here.</p>
      <div className="bot-panel-head-row">
        <span className="bot-panel-label">Apps</span>
        {openPlugins ? <button className="press bot-panel-link" onClick={openPlugins}>Add app</button> : null}
      </div>
      <div className="bot-panel-row">
        <span className="bot-panel-rowtext">
          <span className="bot-panel-row-name">Let {bot.name} use apps</span>
        </span>
        <Switch on={on.length > 0} onChange={toggleAll} disabled={!apps.length} />
      </div>
      {!apps.length ? <p className="bot-panel-note">No apps connected yet.</p> : apps.map((a) => (
        <div className="bot-panel-row" key={a.id}>
          <span className="bot-panel-rowtext">
            <span className="bot-panel-row-name">{a.name}</span>
            {a.description ? <span className="bot-panel-row-detail">{a.description}</span> : null}
          </span>
          <Switch on={on.includes(a.id)} onChange={(use) => set(use ? [...on, a.id] : on.filter((x) => x !== a.id))} />
        </div>
      ))}
    </div>
  )
}
