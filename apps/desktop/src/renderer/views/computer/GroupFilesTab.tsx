import { useState } from 'react'
import type { Bot } from '@shared/models'
import { BotAvatar } from '../../components/Avatar'
import { Icon } from '../../components/Icon'
import { font } from '../../lib/fonts'
import { useModel } from '../../lib/observable'
import type { BotStore } from '../../store/bot-store'
import { FilesTab } from './FilesTab'

/** One file the room changed: where, who, and how much. */
interface Edit {
  path: string
  botId: string
  at: number
  added: number
  removed: number
  isNew: boolean
}

const when = (at: number) => new Date(at).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })

/** The group's Files tab: every file the room changed, signed by the member that changed it. */
export function GroupFilesTab({ group, store }: { group: Bot; store: BotStore }) {
  useModel(store)
  const [copied, setCopied] = useState<string | null>(null)
  const [browsing, setBrowsing] = useState(false)

  // The transcript is the source: a member's file edits arrive as tool calls with diffs.
  const byPath = new Map<string, Edit>()
  for (const entry of store.allEntries(group.id)) {
    if (entry.kind !== 'tool' || !entry.data.diffs?.length) continue
    const botId = entry.data.author ?? entry.botId
    for (const diff of entry.data.diffs) {
      const edit: Edit = {
        path: diff.path,
        botId,
        at: entry.createdAt,
        added: diff.added,
        removed: diff.removed,
        isNew: diff.isNew,
      }
      const seen = byPath.get(diff.path)
      if (!seen || seen.at <= edit.at) byPath.set(diff.path, edit)
    }
  }
  const edits = [...byPath.values()].sort((a, b) => b.at - a.at)
  const owner = (id: string) => store.bots.get(id)

  const copyPath = (path: string) => {
    window.codync.app.copy(path)
    setCopied(path)
    setTimeout(() => setCopied((c) => (c === path ? null : c)), 1500)
  }

  if (browsing) {
    return (
      <div className="group-files-browse">
        <button className="computer-link" onClick={() => setBrowsing(false)}>
          <Icon name="chevron.left" size={11} weight="semibold" />
          Edited files
        </button>
        <FilesTab bot={group} store={store} />
      </div>
    )
  }

  return (
    <div className="computer-pad group-files">
      {group.workspace ? (
        <button className="files-row group-files-shared" onClick={() => setBrowsing(true)}>
          <Icon name="folder" size={15} color="var(--secondary)" />
          <span className="files-name" style={font(13)}>Browse the shared folder</span>
          <Icon name="chevron.right" size={11} weight="semibold" color="var(--tertiary)" />
        </button>
      ) : (
        <p className="computer-hint" style={font('footnote')}>
          This group works in its members' own folders. Set a shared folder in group settings to browse one here.
        </p>
      )}
      <span className="group-files-label" style={font('caption')}>Edited by this group</span>
      {!edits.length ? (
        <p className="computer-hint" style={font('footnote')}>No files changed here yet.</p>
      ) : edits.map((edit) => {
        const bot = owner(edit.botId)
        return (
          <button
            key={edit.path}
            className="files-row group-files-row"
            onClick={() => copyPath(edit.path)}
            title="Copy the path"
          >
            <Icon name="doc.text" size={15} color="var(--tertiary)" />
            <span className="group-files-text">
              <span className="files-name" style={font(13)}>{edit.path.split('/').pop()}</span>
              <span className="group-files-path" style={font('caption')}>{edit.path}</span>
            </span>
            {bot ? (
              <span className="group-files-author" style={font('caption')}>
                <BotAvatar bot={bot} size={16} animated={false} />
                {bot.name}
              </span>
            ) : null}
            <span className="group-files-delta" style={font('caption')}>
              {edit.added ? `+${edit.added}` : ''}{edit.removed ? ` −${edit.removed}` : ''}{edit.isNew ? ' new' : ''}
            </span>
            <span className="files-when" style={font('caption')}>{copied === edit.path ? 'copied' : when(edit.at)}</span>
          </button>
        )
      })}
    </div>
  )
}
