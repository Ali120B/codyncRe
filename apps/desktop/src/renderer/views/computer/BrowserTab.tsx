import { useEffect, useRef, useState } from 'react'
import { isWorkingIn, type Bot } from '@shared/models'
import { Spinner } from '../../components/Controls'
import { Icon } from '../../components/Icon'
import { font } from '../../lib/fonts'
import type { BotStore } from '../../store/bot-store'
import { SwitchRow } from '../bots/parts'
import { BrowserClient, FRAME_HEIGHT, FRAME_WIDTH, engineName, frameSrc, type BrowserFrame, type BrowserState } from './browser-model'

const FRAME_MS = 400
const RETRY_MS = 2000
const STATE_MS = 1200
const NAMED_KEYS = new Set(['Enter', 'Tab', 'Escape', 'Backspace', 'Delete', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'PageUp', 'PageDown', 'Home', 'End', ' '])

const message = (e: unknown) => (e instanceof Error ? e.message : String(e))

/**
 * The bot's own headless browser: what it sees, its tabs, and a real mouse/keyboard when the
 * user takes over (the host hands control back to the bot after 50 s without input).
 * Nothing starts it on its own: an explicit Start, or the bot working in this chat.
 */
export function BrowserTab({ bot, store, working }: { bot: Bot; store: BotStore; working?: boolean }) {
  const [state, setState] = useState<BrowserState | null>(null)
  const [frame, setFrame] = useState<BrowserFrame | null>(null)
  const [error, setError] = useState<string | null>(null)
  /** The user asked to see this browser (Start, a URL, Take control). */
  const [watch, setWatch] = useState(false)
  const [busy, setBusy] = useState<'start' | 'restart' | 'stop' | null>(null)
  /** Near-fullscreen: the browser fills the window (not the OS full screen). */
  const [expanded, setExpanded] = useState(false)
  const [address, setAddress] = useState('')
  const addressFocused = useRef(false)
  const view = useRef<HTMLDivElement>(null)
  const wheel = useRef({ x: FRAME_WIDTH / 2, y: FRAME_HEIGHT / 2, dy: 0 })
  const wheelTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  // The config decides before the first state arrives; the host is the truth after.
  const enabled = state?.enabled ?? bot.browser
  const running = state?.running ?? false
  const botWorking = working ?? isWorkingIn(bot, bot.id, null)
  const live = enabled && (watch || botWorking)
  const user = state?.control === 'user'
  const engine = state?.engine ?? null

  const browser = async () => new BrowserClient(await store.ready(), bot.id)
  const perform = async (fn: (b: BrowserClient) => Promise<unknown>) => {
    try {
      await fn(await browser())
      setError(null)
    } catch (e) {
      setError(message(e))
    }
  }

  // State: cheap, always while the tab is open. Frames: only when wanted, one request at a
  // time (the first one launches Chrome/Firefox); nothing runs while the window is hidden.
  useEffect(() => {
    let alive = true
    let stateTimer: ReturnType<typeof setTimeout> | undefined
    let frameTimer: ReturnType<typeof setTimeout> | undefined
    const pumpState = async () => {
      try {
        const next = await (await browser()).state()
        if (!alive) return
        setState(next)
        if (!addressFocused.current) setAddress(next.url ?? '')
      } catch (e) {
        if (alive) setError(message(e))
      }
      if (alive) stateTimer = setTimeout(pumpState, STATE_MS)
    }
    const pumpFrame = async () => {
      if (!live || document.hidden) {
        if (alive) frameTimer = setTimeout(pumpFrame, FRAME_MS)
        return
      }
      try {
        const next = await (await browser()).frame()
        if (!alive) return
        setFrame(next)
        if (!addressFocused.current && next.url) setAddress(next.url)
        setError(null)
        setBusy((b) => (b === 'start' || b === 'restart' ? null : b))
      } catch (e) {
        if (alive) setError(message(e))
        if (alive) frameTimer = setTimeout(pumpFrame, RETRY_MS)
        return
      }
      if (alive) frameTimer = setTimeout(pumpFrame, FRAME_MS)
    }
    void pumpState()
    void pumpFrame()
    return () => {
      alive = false
      if (stateTimer) clearTimeout(stateTimer)
      if (frameTimer) clearTimeout(frameTimer)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bot.id, store, live])

  // Escape closes the big view — unless the user is driving the page, where it's a key for the page.
  useEffect(() => {
    if (!expanded) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !user) setExpanded(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [expanded, user])

  const point = (e: { clientX: number; clientY: number }, el: HTMLElement) => {
    const rect = el.getBoundingClientRect()
    return { x: ((e.clientX - rect.left) / rect.width) * FRAME_WIDTH, y: ((e.clientY - rect.top) / rect.height) * FRAME_HEIGHT }
  }

  const start = () => {
    setWatch(true)
    setBusy((b) => b ?? 'start')
  }
  const restart = () => {
    setBusy('restart')
    setWatch(true)
    void perform(async (b) => {
      await b.stop()
      setFrame(null)
      setState((s) => (s ? { ...s, running: false } : s))
    })
  }
  /** Off for this bot: keep the profile, but stop this browser and the bot's browser tools. */
  const stopForBot = () => {
    setBusy('stop')
    setWatch(false)
    setFrame(null)
    void perform(async (b) => {
      await b.stop()
      store.setBrowser(bot, false)
      setState((s) => (s ? { ...s, enabled: false, running: false } : s))
    }).finally(() => setBusy(null))
  }
  const takeControl = () => {
    setWatch(true)
    setState((s) => (s ? { ...s, control: 'user', secondsLeft: 50 } : s))
    view.current?.focus()
    void perform((b) => b.control('user'))
  }
  const release = () => {
    setState((s) => (s ? { ...s, control: 'bot', secondsLeft: 0 } : s))
    void perform((b) => b.control('bot'))
  }
  const navigate = (url: string) => {
    setWatch(true)
    setBusy((b) => b ?? 'start')
    void perform((b) => b.open(url)).finally(() => setBusy((b) => (b === 'start' ? null : b)))
  }

  const onFrameWheel = (e: React.WheelEvent<HTMLImageElement>) => {
    if (!user) return
    const at = point(e, e.currentTarget)
    wheel.current = { ...at, dy: wheel.current.dy + e.deltaY }
    if (wheelTimer.current) return
    wheelTimer.current = setTimeout(() => {
      wheelTimer.current = null
      const { x, y, dy } = wheel.current
      wheel.current = { ...wheel.current, dy: 0 }
      if (dy) void perform((b) => b.scroll(x, y, dy))
    }, 90)
  }

  const onKeyDown = (e: React.KeyboardEvent) => {
    if (!user || e.ctrlKey || e.metaKey || e.altKey) return
    const key = e.key
    if (key.length === 1 || NAMED_KEYS.has(key)) {
      e.preventDefault()
      void perform((b) => (key.length === 1 ? b.type(key) : b.press(key)))
    }
  }

  if (!enabled) {
    return (
      <div className="computer-pad">
        <SwitchRow
          title="Use a browser"
          detail={`Let ${bot.name} open pages in its own private browser on this computer — its own profile, its own logins. You can watch and take over from this panel.`}
          on={bot.browser}
          onChange={(on) => store.setBrowser(bot, on)}
        />
        <p className="computer-hint" style={font('footnote')}>
          While on, {bot.name} gets browser tools (open, read, click, type, screenshot) and everything it does stays in its own browser.
        </p>
      </div>
    )
  }
  if (state?.engineError) {
    return (
      <div className="computer-pad">
        <p className="computer-hint" style={font('body')}>{state.engineError}</p>
        <button className="browser-stop" disabled={busy !== null} onClick={stopForBot}>Turn it off for {bot.name}</button>
      </div>
    )
  }

  const chrome = (
    <>
      <div className="browser-toolbar">
        <button className="browser-nav" title="Back" aria-label="Back" onClick={() => void perform((b) => b.back())}>
          <Icon name="chevron.left" size={13} weight="semibold" />
        </button>
        <button className="browser-nav" title="Forward" aria-label="Forward" onClick={() => void perform((b) => b.forward())}>
          <Icon name="chevron.right" size={13} weight="semibold" />
        </button>
        <button className="browser-nav" title="Reload" aria-label="Reload" onClick={() => void perform((b) => b.reload())}>
          <Icon name="arrow.clockwise" size={13} weight="semibold" />
        </button>
        <input
          className="browser-address"
          value={address}
          spellCheck={false}
          placeholder="example.com"
          aria-label="Address"
          onFocus={() => (addressFocused.current = true)}
          onBlur={() => (addressFocused.current = false)}
          onChange={(e) => setAddress(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.nativeEvent.isComposing) {
              e.preventDefault()
              ;(e.target as HTMLInputElement).blur()
              navigate(address)
            }
          }}
        />
        {user ? (
          <button className="browser-control on" onClick={release} title="Hand the browser back to the bot">
            <Icon name="hand.raised" size={12} weight="semibold" />
            Release
          </button>
        ) : (
          <button className="browser-control" onClick={takeControl} title="Pause the bot and drive the page yourself">
            <Icon name="hand.raised" size={12} weight="semibold" />
            Take control
          </button>
        )}
        <button className="browser-nav" title={expanded ? 'Exit full view' : 'Full view'} aria-label={expanded ? 'Exit full view' : 'Full view'} onClick={() => setExpanded((x) => !x)}>
          <Icon name={expanded ? 'arrow.down.right.and.arrow.up.left' : 'arrow.up.left.and.arrow.down.right'} size={13} weight="semibold" />
        </button>
      </div>
      <div className="browser-tabstrip">
        {(state?.tabs ?? []).map((tab) => (
          <span key={tab.id} className={`browser-tab-pill ${tab.id === state?.activeTab ? 'active' : ''}`}>
            <button className="browser-tab-open" title={tab.url || 'New tab'} onClick={() => void perform((b) => b.selectTab(tab.id))}>
              <span className="browser-tab-title">{tab.title || (tab.url === 'about:blank' || !tab.url ? 'New tab' : tab.url)}</span>
            </button>
            <button className="browser-tab-close" title="Close tab" aria-label={`Close ${tab.title || 'tab'}`} onClick={() => void perform((b) => b.closeTab(tab.id))}>
              <Icon name="xmark" size={9} weight="semibold" />
            </button>
          </span>
        ))}
        <button className="browser-nav browser-new-tab" title="New tab" aria-label="New tab" onClick={() => void perform((b) => { setWatch(true); return b.newTab() })}>
          <Icon name="plus" size={12} weight="semibold" />
        </button>
      </div>
      <div className="browser-view" ref={view} tabIndex={0} onKeyDown={onKeyDown} role="application" aria-label={`${bot.name}'s browser`}>
        {frame ? (
          <img className="browser-frame" src={frameSrc(frame)} alt="" draggable={false} onClick={(e) => user && void perform((b) => { const at = point(e, e.currentTarget); return b.click(at.x, at.y) })} onWheel={onFrameWheel} />
        ) : (
          <div className="browser-blank" style={font('footnote')}>
            {busy === 'start' || busy === 'restart' ? (
              <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Spinner size={13} /> Starting {engineName(engine)}…
              </span>
            ) : (
              <span style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 10 }}>
                {running ? 'No page yet.' : `${engineName(engine)} is not running.`}
                <button className="browser-start" onClick={start} disabled={busy !== null}>
                  <Icon name="play" size={11} weight="semibold" />
                  Start browser
                </button>
              </span>
            )}
          </div>
        )}
        {user ? null : (
          <button className="browser-takeover" onClick={takeControl}>
            Take control
          </button>
        )}
      </div>
      <div className="browser-status" style={font('caption')}>
        <span>{engineName(engine)}</span>
        <span>·</span>
        <span>{running ? 'running' : busy === 'stop' ? 'stopping' : 'stopped'}</span>
        <span>·</span>
        {user ? <span>You have control — {bot.name} can't act · releases in {state?.secondsLeft ?? 0}s</span> : botWorking && !watch ? <span>{bot.name} is working — watching</span> : running ? <span>{bot.name} is driving</span> : <span>Starts only when you ask{botWorking ? ' or the bot works here' : ''}</span>}
        <span style={{ flex: 1 }} />
        {running ? (
          <button className="browser-restart" disabled={busy !== null} onClick={restart}>
            Restart
          </button>
        ) : null}
      </div>
      {error ? <div className="browser-error" style={font('footnote')}>{error}</div> : null}
    </>
  )

  return (
    <div className="browser-tab">
      <div className="browser-owner">
        <span style={{ ...font('caption'), color: 'var(--tertiary)' }}>Browser</span>
        <span style={{ flex: 1 }} />
        <button className="browser-stop" disabled={busy === 'stop'} title={`Close the browser and take the tools away from ${bot.name}. Its profile is kept.`} onClick={stopForBot}>
          <Icon name="power" size={11} weight="semibold" />
          Stop for {bot.name}
        </button>
      </div>
      {expanded ? null : chrome}
      {expanded ? (
        <div className="browser-overlay" onClick={(e) => e.target === e.currentTarget && setExpanded(false)}>
          <div className="browser-overlay-panel" role="dialog" aria-modal="true" aria-label={`${bot.name}'s browser, full view`}>
            {chrome}
          </div>
        </div>
      ) : null}
    </div>
  )
}
