import { useEffect, useState } from 'react'
import type { AuthorizedDevice, Bot } from '@shared/models'
import { Icon } from '../../components/Icon'
import { font } from '../../lib/fonts'
import type { BotStore } from '../../store/bot-store'

/**
 * The Android tab: the bot's phone surface. Until the Android app (§7) ships, this shows
 * the phones actually paired with this computer — real host state, nothing faked.
 */
export function AndroidTab({ bot, store }: { bot: Bot; store: BotStore }) {
  const [devices, setDevices] = useState<AuthorizedDevice[] | null>(null)
  useEffect(() => {
    let alive = true
    const load = async () => {
      try {
        const res = await (await store.ready()).call<{ devices: AuthorizedDevice[] }>('devices')
        if (alive) setDevices(res.devices.filter((d) => d.platform === 'android'))
      } catch {
        if (alive) setDevices((cur) => cur ?? [])
      }
    }
    void load()
    const timer = setInterval(load, 15_000)
    return () => {
      alive = false
      clearInterval(timer)
    }
  }, [store])

  return (
    <div className="computer-pad">
      <p className="computer-hint" style={{ ...font('footnote'), color: 'var(--text)' }}>
        {bot.name}'s Android surface: its phone screen and apps, once the Android app is installed on a phone.
      </p>
      {devices === null ? (
        <p className="computer-hint" style={font('footnote')}>Checking paired phones…</p>
      ) : devices.length ? (
        <div className="screen-rows">
          {devices.map((device) => (
            <div key={device.key} className="screen-row">
              <span style={{ ...font(12.5), color: 'var(--secondary)', width: 84, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{device.name}</span>
              <Icon name={device.connected ? 'checkmark.circle' : 'circle'} size={12} color={device.connected ? 'var(--added)' : 'var(--tertiary)'} />
              <span style={font(12.5)}>{device.connected ? 'Connected' : 'Paired, not connected'}</span>
            </div>
          ))}
        </div>
      ) : (
        <p className="computer-hint" style={font('footnote')}>
          No Android phone paired yet — pairing arrives with the Android app.
        </p>
      )}
    </div>
  )
}
