import type { HostClient } from '../../client/host-client'

/** One browser tab, as the host lists it. */
export interface BrowserTabInfo {
  id: string
  title: string
  url: string
}

/** What `browserState` reports: the toggle, the engine, the tabs, and who is driving. */
export interface BrowserState {
  enabled: boolean
  engine: string | null
  engineError: string | null
  running: boolean
  tabs: BrowserTabInfo[]
  activeTab: string | null
  control?: 'user' | 'bot'
  /** Seconds until the user's takeover releases itself. */
  secondsLeft?: number
  url?: string
  title?: string
}

/** One captured frame of the active tab. */
export interface BrowserFrame {
  data: string
  format: string
  tab: string
  url: string
  title: string
  width: number
  height: number
  control: 'user' | 'bot'
}

/** Every session renders at this viewport; panel input maps to it. */
export const FRAME_WIDTH = 1280
export const FRAME_HEIGHT = 800

export const frameSrc = (frame: BrowserFrame) => `data:image/${frame.format};base64,${frame.data}`

export const engineName = (engine: string | null | undefined) => (engine === 'firefox' ? 'Firefox' : 'Chrome')

/** Typed calls for one bot's browser, over the store's host link. */
export class BrowserClient {
  constructor(
    private readonly client: HostClient,
    private readonly botId: string,
  ) {}

  state() {
    return this.client.call<BrowserState>('browserState', { botId: this.botId })
  }

  /** Slow on the first call: the host launches the browser then. */
  frame() {
    return this.client.call<BrowserFrame>('browserFrame', { botId: this.botId }, 90_000)
  }

  open(url: string, newTab = false) {
    return this.client.call('browserOpen', { botId: this.botId, url, newTab })
  }

  newTab() {
    return this.client.call('browserNewTab', { botId: this.botId })
  }

  closeTab(tabId: string) {
    return this.client.call('browserCloseTab', { botId: this.botId, tabId })
  }

  selectTab(tabId: string) {
    return this.client.call('browserSelectTab', { botId: this.botId, tabId })
  }

  reload() {
    return this.client.call('browserReload', { botId: this.botId })
  }

  back() {
    return this.client.call('browserBack', { botId: this.botId })
  }

  forward() {
    return this.client.call('browserForward', { botId: this.botId })
  }

  click(x: number, y: number) {
    return this.client.call('browserClick', { botId: this.botId, x, y })
  }

  type(text: string) {
    return this.client.call('browserType', { botId: this.botId, text })
  }

  press(key: string) {
    return this.client.call('browserPress', { botId: this.botId, key })
  }

  scroll(x: number, y: number, deltaY: number) {
    return this.client.call('browserScroll', { botId: this.botId, x, y, deltaY })
  }

  control(control: 'user' | 'bot') {
    return this.client.call<{ control: 'user' | 'bot'; secondsLeft: number }>('browserControl', { botId: this.botId, control })
  }

  stop() {
    return this.client.call('browserStop', { botId: this.botId })
  }
}
