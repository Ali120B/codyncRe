import { useMemo, useState } from 'react'
import { needsInput, type Entry } from '@shared/models'
import { IconButton } from '../components/Controls'
import { Icon } from '../components/Icon'
import { ModalHeader, Sheet } from '../components/Overlay'
import { font } from '../lib/fonts'
import { useApp } from '../store/context'
import { permissionHeadline } from './thread/PermissionCard'
import { relativeTime } from './thread/chat-items'
import './needs-you.css'

interface InboxItem {
  key: string
  title: string
  detail: string
  icon: string
  at: number
  danger?: boolean
  open: () => void
}

/**
 * The one place that answers "what is waiting on me?": permission cards, failed sends,
 * connection sign-ins, device approvals and bots whose last run failed. Sits immediately
 * above the account/plugins footer.
 */
export function NeedsYouInbox({ compact = false }: { compact?: boolean }) {
  const app = useApp()
  const [open, setOpen] = useState(false)

  const items = useMemo(() => {
    const out: InboxItem[] = []
    for (const row of app.roster) {
      const { bot, store, ref } = row
      const entries = store.allEntries(bot.id).slice(-500)
      const pendingCard = (entry: Entry, kind: 'permission' | 'connection') => {
        const author = entry.data.author && entry.data.author !== bot.id ? store.authorName(entry.data.author as string) : ''
        const who = author ? `${bot.name} · ${author}` : bot.name
        if (kind === 'permission') {
          out.push({
            key: `perm:${entry.id}`,
            title: permissionHeadline(entry.data.toolKind as string | undefined),
            detail: `${who}${entry.data.title ? ` · ${entry.data.title}` : ''}`,
            icon: entry.data.toolKind === 'execute' ? 'terminal' : 'lock.shield',
            at: entry.createdAt,
            danger: true,
            open: () => app.showMessage(ref, entry.id, entry.threadId ?? null),
          })
        } else {
          const request = entry.data.connectionRequest as { title?: string } | undefined
          out.push({
            key: `request:${entry.id}`,
            title: request?.title ? `Sign in to ${request.title}` : 'A bot needs a sign-in',
            detail: who,
            icon: 'key',
            at: entry.createdAt,
            open: () => app.showMessage(ref, entry.id, entry.threadId ?? null),
          })
        }
      }
      let waiting = false
      let failedEntry: Entry | null = null
      for (const entry of entries) {
        if (entry.kind === 'permission' && entry.data.status === 'pending') {
          pendingCard(entry, 'permission')
          waiting = true
        }
        if (entry.data.connectionRequest?.status === 'pending') {
          pendingCard(entry, 'connection')
          waiting = true
        }
        if (entry.kind === 'user' && entry.data.status === 'failed' && (!failedEntry || entry.createdAt > failedEntry.createdAt)) {
          failedEntry = entry
        }
      }
      if (failedEntry) {
        const failed = failedEntry
        out.push({
          key: `failed:${failed.id}`,
          title: "A message didn't send",
          detail: `${bot.name}${failed.data.text ? ` · ${String(failed.data.text).slice(0, 80)}` : ''}`,
          icon: 'exclamationmark.bubble',
          at: failed.createdAt,
          danger: true,
          open: () => app.showMessage(ref, failed.id, failed.threadId ?? null),
        })
      }
      if (bot.status === 'error') {
        out.push({
          key: `error:${bot.id}`,
          title: `${bot.name} needs attention`,
          detail: 'Its last run failed. Open the chat to see what happened.',
          icon: 'exclamationmark.triangle',
          at: bot.lastAt ?? 0,
          danger: true,
          open: () => app.select(ref),
        })
      } else if (needsInput(bot) && !waiting) {
        out.push({
          key: `input:${bot.id}`,
          title: `${bot.name} is waiting for you`,
          detail: bot.activity || 'It needs your answer to continue.',
          icon: 'questionmark.bubble',
          at: bot.lastAt ?? 0,
          open: () => app.select(ref),
        })
      }
    }
    for (const approval of app.approvals) {
      out.push({
        key: `approval:${approval.id}`,
        title: `${approval.request.deviceName || 'A device'} wants access`,
        detail: approval.request.code ? `Approval code ${approval.request.code}` : 'Waiting for its approval code',
        icon: 'lock.open',
        at: approval.request.createdAt ?? 0,
        open: () => app.reviewApprovals(),
      })
    }
    return out.sort((a, b) => b.at - a.at)
  }, [app, app.roster, app.approvals])

  const count = items.length
  const label = count ? `Needs you, ${count} waiting` : 'Needs you, all caught up'
  const pick = (item: InboxItem) => {
    setOpen(false)
    item.open()
  }

  const sheet = (
    <Sheet open={open} onClose={() => setOpen(false)} width={420} height={520} dismissOnDim>
      <div className="needs-you-sheet">
        <ModalHeader title="Needs you" onClose={() => setOpen(false)} trailing={count ? <span className="needs-you-count">{count}</span> : null} />
        <div className="needs-you-list">
          {count === 0 ? (
            <div className="needs-you-empty">
              <Icon name="checkmark.circle" size={24} color="var(--secondary)" />
              <div style={font('compactBody', 'semibold')}>You're all caught up</div>
              <div style={{ ...font('footnote'), color: 'var(--secondary)' }}>Approvals, questions and failed runs show up here.</div>
            </div>
          ) : (
            items.map((item) => (
              <button key={item.key} className="needs-you-row press" onClick={() => pick(item)} title={item.detail}>
                <span className={`needs-you-icon ${item.danger ? 'danger' : ''}`}>
                  <Icon name={item.icon} size={14} />
                </span>
                <span className="needs-you-text">
                  <span className="needs-you-title">{item.title}</span>
                  <span className="needs-you-detail">{item.detail}</span>
                </span>
                <span className="needs-you-time">{item.at ? relativeTime.day(item.at) : ''}</span>
                <Icon name="chevron.right" size={9} weight="semibold" color="var(--tertiary)" />
              </button>
            ))
          )}
        </div>
      </div>
    </Sheet>
  )

  if (compact) {
    return (
      <>
        <span className="needs-you-rail">
          <IconButton title={label} icon="bell" size={36} onClick={() => setOpen(true)} />
          {count ? <span className="needs-you-count rail" aria-hidden>{count > 9 ? '9+' : count}</span> : null}
        </span>
        {sheet}
      </>
    )
  }

  return (
    <>
      <button className={`needs-you-button ${count ? 'has-items' : ''}`} onClick={() => setOpen(true)} title={label} aria-label={label}>
        <Icon name="bell" size={13} weight="semibold" />
        <span className="needs-you-label">Needs you</span>
        <span className="needs-you-spacer" />
        {count ? <span className="needs-you-count">{count > 99 ? '99+' : count}</span> : <span className="needs-you-clear">All clear</span>}
        <Icon name="chevron.up" size={8} weight="semibold" color="var(--tertiary)" />
      </button>
      {sheet}
    </>
  )
}
