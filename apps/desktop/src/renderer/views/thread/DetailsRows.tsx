import { folderName, type Bot } from '@shared/models'
import { Icon } from '../../components/Icon'
import { font } from '../../lib/fonts'
import { useStore } from '../../store/context'
import './bot-panel.css'

const ease = {
  ask: 'Asks you first',
  auto: 'Approved automatically',
  full: 'Full access',
} as const

const title = (value: string) => value.replace(/(^|\s|-)\w/g, (c) => c.toUpperCase())

/**
 * The Details tab's rows (Maus's bot profile): Look, name and job, instructions, model,
 * approvals, workspace — each opening its settings section, then All settings.
 */
export function DetailsRows({ bot, openSettings }: { bot: Bot; openSettings: (section: string) => void }) {
  const store = useStore()
  const backend = store.hello?.backends.find((b) => b.id === bot.backend)
  const model = bot.model ? bot.model.split('/').pop()!.split(':').slice(0, 2).join(':') : `${backend?.name ?? bot.backend}'s default`
  const rows: { id: string; label: string; value: string; section: string }[] = [
    { id: 'look', label: 'Look', value: `${title(bot.avatarShape)} · ${title(bot.avatarColor)}`, section: 'identity' },
    { id: 'name', label: 'Name and job', value: bot.description || bot.name, section: 'overview' },
    { id: 'instructions', label: 'Instructions', value: bot.description || 'None yet', section: 'instructions' },
    { id: 'model', label: 'Default model', value: model, section: 'model' },
    { id: 'permission', label: `Before ${bot.name} acts`, value: ease[bot.permission as keyof typeof ease] ?? bot.permission, section: 'permissions' },
    { id: 'workspace', label: `Where ${bot.name} works`, value: bot.managedWorkspace ? 'Personal workspace' : folderName(bot), section: 'access' },
  ]
  return (
    <div className="details-rows">
      {rows.map((row) => (
        <button key={row.id} className="details-row press" onClick={() => openSettings(row.section)}>
          <span className="details-row-label" style={font(13)}>{row.label}</span>
          <span className="details-row-value" style={font(13)}>{row.value}</span>
          <Icon name="chevron.right" size={11} weight="semibold" color="var(--tertiary)" />
        </button>
      ))}
      <button className="details-row all press" onClick={() => openSettings('')}>
        <Icon name="slider.horizontal.3" size={13} color="var(--text)" />
        <span className="details-row-label" style={{ ...font(13, 'medium'), color: 'var(--text)' }}>All settings</span>
        <span style={{ flex: 1 }} />
        <Icon name="chevron.right" size={11} weight="semibold" color="var(--tertiary)" />
      </button>
    </div>
  )
}
