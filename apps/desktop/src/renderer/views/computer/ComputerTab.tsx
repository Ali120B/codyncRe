import type { Bot } from '@shared/models'
import { Icon } from '../../components/Icon'
import { font } from '../../lib/fonts'
import { useApp } from '../../store/context'
import type { BotStore } from '../../store/bot-store'
import { SwitchRow } from '../bots/parts'

/** The Computer tab: this computer's screen state, who may use it, and this bot's switch. */
export function ComputerTab({ bot, store }: { bot: Bot; store: BotStore }) {
  const app = useApp()
  const screen = store.screen
  const local = store.isLoopback

  return (
    <div className="computer-pad">
      {screen ? (
        <>
          {local ? (
            <SwitchRow
              title="Remote screen"
              detail={
                screen.enabled
                  ? screen.platform === 'linux'
                    ? 'Sharing this screen, and letting bots use it while it is on.'
                    : 'Sharing this screen so you can watch and take over from your phone.'
                  : 'Share this screen with your phone, and let bots with computer use operate it.'
              }
              on={screen.enabled}
              onChange={(on) => void app.setRemoteScreen(on)}
            />
          ) : null}
          <div className="screen-rows">
            <ScreenRow label="Sharing" ok={screen.enabled} text={screen.enabled ? 'On' : 'Off'} />
            <ScreenRow label="Connected" ok={screen.connected} text={screen.connected ? 'Yes' : 'Not yet'} />
            <ScreenRow
              label="Capture"
              ok={screen.capture || screen.platform === 'windows' || screen.platform === 'darwin'}
              text={screen.capture ? 'Ready' : screen.platform === 'windows' || screen.platform === 'darwin' ? 'Ready' : 'Waiting for permission'}
            />
            <ScreenRow
              label="Control"
              ok={screen.input || screen.platform === 'windows' || screen.platform === 'darwin'}
              text={screen.input ? 'Ready' : screen.platform === 'windows' || screen.platform === 'darwin' ? 'Ready' : 'Waiting for permission'}
            />
            {screen.userControl ? <ScreenRow label="Takeover" ok text="You have control" /> : null}
            {screen.agentBot ? <ScreenRow label="In use by" ok text={store.bots.get(screen.agentBot)?.name ?? screen.agentBot} /> : null}
          </div>
          {screen.enabled && !screen.connected ? (
            <p className="computer-hint" style={font('footnote')}>
              {screen.permissionApp ? `Allow ${screen.permissionApp}` : 'Allow screen sharing'} in your system privacy settings if this stays on “Not yet”.
            </p>
          ) : null}
          {app.screenError ? <p className="computer-hint" style={{ ...font('footnote'), color: 'var(--danger)' }}>{app.screenError}</p> : null}
        </>
      ) : (
        <p className="computer-hint" style={font('footnote')}>Screen sharing isn't set up on this computer yet.</p>
      )}
      <SwitchRow
        title={`Let ${bot.name} use the computer`}
        detail={`${bot.name} can see the screen and use the mouse and keyboard${screen?.enabled ? '; you can take over from here or your phone' : ''}.`}
        on={bot.computer}
        onChange={(on) => store.setComputer(bot, on)}
      />
      <SwitchRow
        title="Take over"
        detail={screen?.userControl ? 'Bots pause while you have the screen.' : 'Pause bots and drive the screen yourself.'}
        on={screen?.userControl ?? false}
        onChange={(on) => store.screenTakeover(on)}
      />
      <p className="computer-hint" style={font('footnote')}>
        Watching the screen from this panel lands with the Linux screen work; for now the phone shows it.
      </p>
    </div>
  )
}

function ScreenRow({ label, ok, text }: { label: string; ok: boolean; text: string }) {
  return (
    <div className="screen-row">
      <span style={{ ...font(12.5), color: 'var(--secondary)', width: 84 }}>{label}</span>
      <Icon name={ok ? 'checkmark.circle' : 'circle'} size={12} color={ok ? 'var(--added)' : 'var(--tertiary)'} />
      <span style={font(12.5)}>{text}</span>
    </div>
  )
}
