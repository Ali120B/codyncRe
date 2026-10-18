import { Icon } from '../../components/Icon'
import { font } from '../../lib/fonts'
import type { BotStore } from '../../store/bot-store'

/** The Cloud tab: how this computer links to the cloud (host `cloudStatus`). */
export function CloudTab({ store }: { store: BotStore }) {
  const cloud = store.cloud
  if (!cloud) {
    return <div className="computer-pad"><p className="computer-hint" style={font('footnote')}>The host hasn't reported its cloud link yet.</p></div>
  }
  const approval = cloud.approval === 'auto' ? 'Account devices join automatically' : cloud.approval === 'code' ? 'New devices need the 6-digit code' : null
  return (
    <div className="computer-pad">
      <div className="screen-rows">
        <CloudRow ok={cloud.enabled} label="Cloud" text={cloud.enabled ? 'On' : 'Off — only Wi-Fi and Tailscale reach it'} />
        {cloud.enabled ? <CloudRow ok={cloud.connected} label="Link" text={cloud.connected ? 'Connected (encrypted)' : 'Connecting…'} /> : null}
        {cloud.enabled && cloud.registered !== undefined && cloud.registered !== null ? (
          <CloudRow ok={cloud.registered} label="Registered" text={cloud.registered ? 'This computer is on your account' : 'Not registered yet'} />
        ) : null}
        <CloudRow ok={!!cloud.owner} label="Owner" text={cloud.owner?.email ?? cloud.owner?.userId ?? 'This computer runs on its own'} />
        {approval ? <CloudRow ok label="Devices" text={approval} /> : null}
      </div>
      {cloud.url ? <p className="computer-hint" style={{ ...font('footnote'), color: 'var(--tertiary)' }}>{cloud.url}</p> : null}
      {cloud.lastError ? <p className="computer-hint" style={{ ...font('footnote'), color: 'var(--danger)' }}>{cloud.lastError}</p> : null}
      <p className="computer-hint" style={font('footnote')}>
        Watching a bot's screen from another network arrives with the cloud screen work; this shows how the computer links to the cloud today.
      </p>
    </div>
  )
}

function CloudRow({ ok, label, text }: { ok: boolean | null | undefined; label: string; text: string }) {
  return (
    <div className="screen-row">
      <span style={{ ...font(12.5), color: 'var(--secondary)', width: 84 }}>{label}</span>
      <Icon name={ok === false ? 'circle' : 'checkmark.circle'} size={12} color={ok === false ? 'var(--tertiary)' : 'var(--added)'} />
      <span style={{ ...font(12.5), minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{text}</span>
    </div>
  )
}
