import type { Entry, PermissionOption } from '@shared/models'
import { Spinner } from '../../components/Controls'
import { font } from '../../lib/fonts'
import { useStore } from '../../store/context'
import type { BotStore } from '../../store/bot-store'
import { optionLabel, permissionHeadline } from './PermissionCard'
import './pending-approval.css'

/** The permission requests still waiting in one conversation, oldest first. */
export function pendingApprovals(store: BotStore, botId: string, thread: string | null): Entry[] {
  return store.chat(botId).filter((e) => e.kind === 'permission' && e.data.status === 'pending' && (e.threadId ?? null) === thread)
}

/** Whether the conversation waits on a decision (its composer is taken over by the bar). */
export function useWaitingApproval(botId: string, thread: string | null): boolean {
  const store = useStore()
  return pendingApprovals(store, botId, thread).length > 0
}

/** Rejects first, the plain "Allow once" last so the primary sits under your thumb. */
const ORDER = ['reject_always', 'reject_once', 'allow_always', 'allow_once']
const decisionOrder = (options: PermissionOption[]) =>
  [...options].sort((a, b) => (ORDER.indexOf(a.kind) + 1 || 99) - (ORDER.indexOf(b.kind) + 1 || 99))

/**
 * The decision strip above the composer (Maus's PendingApproval): what the agent waits on and
 * the answers. The transcript card above only says it is waiting; deciding happens here.
 */
export function PendingApprovalBar({ botId, thread }: { botId: string; thread: string | null }) {
  const store = useStore()
  const pending = pendingApprovals(store, botId, thread)
  const entry = pending[0]
  if (!entry) return null
  const d = entry.data
  const answering = store.answering.get(entry.id) ?? null
  const options = decisionOrder(d.options ?? [])
  const primary = [...options].reverse().find((o) => o.kind.startsWith('allow'))?.optionId
  const detail = (d.command ?? d.detail ?? d.title ?? '').trim()
  return (
    <div className="approval-bar" role="region" aria-label="Approval needed">
      <div className="approval-bar-head">
        <span className="approval-bar-tag">Approval</span>
        {pending.length > 1 ? <span className="approval-bar-count">{`1 of ${pending.length}`}</span> : null}
        <span className="approval-bar-title">{permissionHeadline(d.toolKind)}</span>
        {d.toolKind ? <span className="approval-bar-slug" style={font(11, undefined, 'monospaced')}>{d.toolKind}</span> : null}
      </div>
      {detail ? <pre className="approval-bar-detail selectable">{detail.length > 4000 ? `${detail.slice(0, 4000)}…` : detail}</pre> : null}
      <div className="approval-bar-actions">
        <button className="press approval-action quiet" disabled={answering !== null} onClick={() => store.stop(botId)}>
          Cancel turn
        </button>
        {options.map((o) => (
          <button
            key={o.optionId}
            className={`press approval-action ${o.optionId === primary ? 'primary' : o.kind.startsWith('reject') ? 'danger' : 'plain'}`}
            disabled={answering !== null}
            onClick={() => store.respond(entry, o.optionId)}
          >
            {answering === o.optionId ? <Spinner size={12} /> : optionLabel(o)}
          </button>
        ))}
      </div>
    </div>
  )
}
