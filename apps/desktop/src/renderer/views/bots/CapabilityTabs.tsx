import { useState } from 'react'
import { draftOf, type Bot, type BotDraft } from '@shared/models'
import { Switch } from '../../components/Controls'
import { useStore } from '../../store/context'
import { usePlugins } from './drafts'

/** Saves one change onto the bot through the host's updateBot. */
export function useBotChange(bot: Bot) {
  const store = useStore()
  return (change: Partial<BotDraft>) => void store.save({ ...draftOf(bot), ...change })
}

/** What this bot can do: every installed skill, switched on or off for the bot alone. */
export function SkillsTab({ bot, openPlugins }: { bot: Bot; openPlugins?: () => void }) {
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
export function VaultTab({ bot, openPlugins }: { bot: Bot; openPlugins?: () => void }) {
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
