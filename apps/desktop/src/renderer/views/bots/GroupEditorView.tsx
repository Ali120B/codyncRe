import { useState } from 'react'
import { isGroup, type Bot, type GroupDraft } from '@shared/models'
import { BotAvatar, GroupAvatar } from '../../components/Avatar'
import { CardSection, ChoicePicker, IconButton, Spinner, ValueRow } from '../../components/Controls'
import { Icon } from '../../components/Icon'
import { Dialog, ModalHeader, useDismiss } from '../../components/Overlay'
import { font } from '../../lib/fonts'
import { useStore } from '../../store/context'
import { errorText } from './drafts'
import { AutoTextArea, SwitchRow } from './parts'
import { useWake } from '../../components/Avatar'
import { WorkspacePicker } from './workspaces'
import './bots.css'

/**
 * Create a group chat, or set one up: name, who's in it, how it works (leader, folder,
 * notifications) and the rules everyone follows. Used full-screen from New chat, and
 * in the details panel when a group's settings are opened.
 */
export function GroupEditorView({ group, members: initialMembers = [], onCancel, onDone }: {
  group: Bot | null
  /** Members picked before the editor opened (creating from New chat). */
  members?: string[]
  /** Closes the editor without saving (creating flow). */
  onCancel?: () => void
  /** Handed the created group instead of dismissing (creating flow). */
  onDone?: (group: Bot) => void
}) {
  const store = useStore()
  const dismiss = useDismiss()
  const groupId = group?.id ?? null
  const isNew = groupId === null
  const [name, setName] = useState(group?.name ?? '')
  const [about, setAbout] = useState(group?.description ?? '')
  const [members, setMembers] = useState<string[]>(group?.members ?? initialMembers)
  const [leader, setLeader] = useState(group?.leader ?? '')
  const [instructions, setInstructions] = useState(group?.instructions ?? '')
  const [shared, setShared] = useState(group?.workspace ?? '')
  // "One shared folder" is its own switch: an empty path must still select it.
  const [sharedFolder, setSharedFolder] = useState(!!group?.workspace)
  const wake = useWake(1400)
  const [pinned, setPinned] = useState(group?.pinned ?? false)
  const [notify, setNotify] = useState(group?.notify ?? true)
  const [query, setQuery] = useState('')
  // A new group starts in the picker; an existing one shows its members first.
  const [adding, setAdding] = useState(group === null && initialMembers.length === 0)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<Bot | null>(null)

  const picked = members.map((id) => store.bots.get(id)).filter((b): b is Bot => !!b)
  // Bots that can still join, filtered by the search.
  const q = query.trim().toLowerCase()
  const candidates = store.roster.filter((b) => !isGroup(b) && !members.includes(b.id) && (!q || b.name.toLowerCase().includes(q)))
  const canSave = members.length > 0 && (!sharedFolder || shared.trim() !== '') && !saving
  // "Alice, Bob" when no name is typed.
  const defaultName = picked.length ? picked.map((b) => b.name).join(', ') : 'Name'
  const current = groupId ? store.bots.get(groupId) : undefined

  const toggle = (id: string) => {
    setMembers((list) => {
      const next = list.includes(id) ? list.filter((m) => m !== id) : [...list, id]
      if (!next.includes(leader)) setLeader('')
      return next
    })
    setQuery('')
  }

  const save = () => {
    if (!canSave) return
    setSaving(true)
    setError(null)
    const typed = name.trim()
    const finalName = typed || defaultName
    const extra: Partial<GroupDraft> = { leader, instructions, workspace: sharedFolder ? shared.trim() : '', pinned, notify }
    void (async () => {
      try {
        if (groupId) {
          await store.updateGroup({ id: groupId, kind: 'group', name: finalName, description: about.trim(), members, ...extra })
          dismiss()
        } else {
          const created = await store.createGroup(finalName, about.trim(), members, extra)
          if (onDone) onDone(created)
          else dismiss()
        }
      } catch (e) {
        setError(errorText(e))
      } finally {
        setSaving(false)
      }
    })()
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--background)' }}>
      <ModalHeader
        title={isNew ? 'New group chat' : 'Group chat'}
        trailing={saving ? <Spinner /> : <IconButton title={isNew ? 'Create' : 'Save'} icon="checkmark" disabled={!canSave} onClick={save} />}
        close={!!onCancel}
        onClose={onCancel}
      />
      <div style={{ flex: 1, minHeight: 0, overflowY: 'auto' }}>
        <div className="group-form">
          <div className="group-identity">
            <GroupAvatar members={picked} size={76} wake={wake} />
            <input className="group-name" value={name} placeholder={defaultName} aria-label="Name" onChange={(e) => setName(e.target.value)} />
            <AutoTextArea className="group-about" value={about} placeholder="What this group works on" minRows={1} maxRows={3} onChange={setAbout} />
          </div>

          <CardSection title="Members" accessory={<span style={{ ...font('caption'), color: 'var(--tertiary)' }}>{picked.length}</span>}>
            {picked.map((bot) => (
              <div key={bot.id} className="member-row fade-in">
                <BotAvatar bot={bot} size={32} animated={false} />
                <span className="member-name">{bot.name}</span>
                {leader === bot.id ? <span className="key-cap">Leader</span> : null}
                <IconButton title={`Remove ${bot.name}`} icon="xmark" onClick={() => toggle(bot.id)} />
              </div>
            ))}
            {adding ? [
              <input
                key="search"
                className="field-box fade-in"
                autoFocus
                value={query}
                placeholder="Search bots"
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.nativeEvent.isComposing && candidates[0]) toggle(candidates[0].id)
                  if (e.key === 'Escape') setAdding(false)
                }}
              />,
              ...candidates.map((bot) => (
                <button key={bot.id} className="member-row press fade-in" aria-label={`Add ${bot.name}`} onClick={() => toggle(bot.id)}>
                  <BotAvatar bot={bot} size={32} animated={false} />
                  <span className="member-name" style={{ color: 'var(--secondary)' }}>{bot.name}</span>
                  <Icon name="plus.circle" size={18} color="var(--secondary)" />
                </button>
              )),
            ] : candidates.length ? (
              <button className="member-row press" onClick={() => setAdding(true)}>
                <span style={{ width: 32, display: 'grid', placeItems: 'center' }}>
                  <Icon name="plus" size={16} color="var(--secondary)" />
                </span>
                <span className="member-name" style={{ color: 'var(--secondary)' }}>Add Member</span>
              </button>
            ) : null}
          </CardSection>
          <span style={{ ...font('caption'), color: 'var(--tertiary)' }}>Everyone answers in turn unless you @mention someone.</span>

          <CardSection title="Room">
            <ValueRow label="Leader" detail="Speaks first each round and hands work to the others.">
              <ChoicePicker
                selection={leader}
                options={[{ id: '', label: 'No leader' }, ...picked.map((b) => ({ id: b.id, label: b.name }))]}
                onChange={setLeader}
              />
            </ValueRow>
            <SwitchRow title="Notifications" detail="Get notified when a member finishes or needs you." on={notify} onChange={setNotify} />
            <SwitchRow title="Pin to top" detail="Keep this group at the top of the sidebar." on={pinned} onChange={setPinned} />
          </CardSection>

          <CardSection title="Workspace">
            <ValueRow
              label="Where they work"
              detail={sharedFolder ? 'Every member works in this project folder.' : 'Every member keeps its own folder (set per bot).'}
            >
              <ChoicePicker
                selection={sharedFolder ? 'shared' : 'own'}
                options={[{ id: 'own', label: 'Own folders' }, { id: 'shared', label: 'One shared folder' }]}
                onChange={(id) => setSharedFolder(id === 'shared')}
              />
            </ValueRow>
            {sharedFolder ? (
              <ValueRow
                label="Shared folder"
                detail={shared || 'Pick one of your saved workspaces or browse for a folder.'}
              >
                <WorkspacePicker value={shared} personal={false} onChange={setShared} />
              </ValueRow>
            ) : null}
          </CardSection>

          <CardSection title="Instructions">
            <AutoTextArea
              className="group-instructions"
              value={instructions}
              placeholder="Rules the whole group follows, e.g. Keep answers short. Always cite the file you changed."
              minRows={3}
              maxRows={6}
              onChange={setInstructions}
            />
          </CardSection>

          {error ? <span className="fade-in" style={{ ...font('footnote'), color: 'var(--danger)' }}>{error}</span> : null}
          {current ? (
            <button className="press" style={{ ...font('body'), color: 'var(--danger)', alignSelf: 'flex-start' }} onClick={() => setConfirmDelete(current)}>
              Delete group chat
            </button>
          ) : null}
        </div>
      </div>
      <DeleteBotDialog bot={confirmDelete} onClose={() => setConfirmDelete(null)} onDeleted={dismiss} />
    </div>
  )
}

/** The one "Delete <bot>?" confirmation (kit `deleteBotConfirmation`). */
function DeleteBotDialog({ bot, onClose, onDeleted }: { bot: Bot | null; onClose: () => void; onDeleted: () => void }) {
  const store = useStore()
  return (
    <Dialog
      open={bot !== null}
      title={`Delete ${bot?.name ?? 'bot'}?`}
      message={bot && isGroup(bot) ? 'Its bots and their own chats stay.' : 'Files it changed on your computer stay as they are.'}
      actions={[
        {
          title: 'Delete bot and its conversation',
          destructive: true,
          action: () => {
            if (bot) store.delete(bot)
            onDeleted()
          },
        },
      ]}
      onClose={onClose}
    />
  )
}
