import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { draftOf, isGroup, isWorkingIn, type Bot, type BotDraft, type Entry } from '@shared/models'
import { BotAvatar, GroupAvatar } from '../../components/Avatar'
import { Spinner } from '../../components/Controls'
import { Icon } from '../../components/Icon'
import { Dialog, Sheet } from '../../components/Overlay'
import { font } from '../../lib/fonts'
import { useFocusMessage } from '../../lib/focus-message'
import { prefs, usePref } from '../../lib/prefs'
import { useApp, useStore } from '../../store/context'
import { UpdateNeededCard } from '../UpdateNeededCard'
import { buildChat, rowIn, useArrivals, type ChatItem } from './chat-items'
import { BotConversationView, BotMessageRow } from './BotConversation'
import { ChatFindBar } from './ChatFindBar'
import { ChatHeaderMenu } from './ChatHeaderMenu'
import { ChatRow, TimeSeparator, WorkingIndicator } from './ChatRows'
import { Composer } from './Composer'
import { ChatSidePanel } from './ChatSidePanel'
import { ComputerPanel } from '../computer/ComputerPanel'
import { ChatsPanel, type ChatSummary } from './ChatsPanel'
import { DetailsPanel } from './DetailsPanel'
import { RepliesView } from './RepliesView'
import { TraceView } from './TraceView'
import { useReading } from './reading'
import { BotTemplateView } from '../bots/BotTemplateView'
import { CallView } from '../call/CallView'
import { MacChatButton } from './MacChatButton'
import { ModelChip } from './ModelChip'
import { ThreadModelsLine } from './ThreadModelsLine'
import './thread.css'
import './composer.css'
import './chat-controls.css'
import './model-chip.css'

const WIDE = 680

/**
 * One endless conversation with a bot or a group chat. Only deliberate messages show here; tool
 * calls and thinking live in the "Full conversation" sheet. Any message can start a thread,
 * which opens beside the chat.
 */
export function ThreadView({ botId, openPlugins }: { botId: string; openPlugins?: () => void }) {
  const store = useStore()
  const app = useApp()
  const bot = store.bots.get(botId) ?? null
  const [showTrace, setShowTrace] = useState(false)
  const [findOpen, setFindOpen] = useState(false)
  const [threadModelsSignal, setThreadModelsSignal] = useState(0)
  const [confirmNewSession, setConfirmNewSession] = useState<{ thread: string | null } | null>(null)
  const [activeChat, setActiveChat] = useState<string | null>(null)
  const [showChats, setShowChats] = useState(false)
  const [openThread, setOpenThread] = useState<string | null>(null)
  const [botChat, setBotChat] = useState<string | null>(null)
  const [templateDraft, setTemplateDraft] = useState<BotDraft | null>(null)
  const [showSettings, setShowSettings] = useState(false)
  const [editingDetails, setEditingDetails] = useState(false)
  const [compactDetails, setCompactDetails] = useState(false)
  const [calling, setCalling] = useState(false)
  const [callSpeaking, setCallSpeaking] = useState(false)
  const interruptCall = useRef<(() => void) | null>(null)
  const [routineId, setRoutineId] = useState<string | null>(null)
  const [routineRequest, setRoutineRequest] = useState(0)
  const [showComputer, setShowComputer] = useState(false)
  const [panelWidth, setPanelWidth] = usePref(prefs.computerPanelWidth)
  const root = useRef<HTMLDivElement>(null)
  const dock = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(800)
  useReading(store, botId, null)

  // A search hit: open its thread (or the main chat), page its message in, then flash its row.
  const focus = app.focus?.ref.botId === botId && app.focus.ref.computerId === store.computer.id ? app.focus : null
  useEffect(() => {
    if (!focus) return
    setActiveChat(null)
    setOpenThread(focus.threadId)
    if (focus.threadId) void store.loadThread(botId, focus.threadId)
    else void store.ensureEntry(botId, focus.entryId)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focus?.nonce])
  useFocusMessage(focus, (nonce) => app.consumeMessageFocus(nonce))

  // This bot's or group's chats: roots started by New chat (not threads), newest first.
  const entries = store.allEntries(botId)
  const chats: ChatSummary[] = (() => {
    const roots = entries.filter((e) => (e.threadId ?? null) === null && e.data.chat === true).map((e) => e.id)
    return roots
      .map((root) => {
        const first = entries.find((e) => e.id === root)
        const replies = entries.filter((e) => e.threadId === root)
        const raw = (first?.data.text ?? replies.find((e) => e.kind === 'user')?.data.text ?? '').replace(/\s+/g, ' ').trim()
        return {
          id: root,
          title: raw.length > 64 ? `${raw.slice(0, 64)}…` : raw || 'Chat',
          at: Math.max(first?.createdAt ?? 0, ...replies.map((e) => e.createdAt), 0),
          replies: replies.filter((e) => e.kind === 'agent').length,
        }
      })
      .sort((a, b) => b.at - a.at)
  })()

  // A pending "New chat" is anchored by the first message sent after it was asked for.
  const pendingChatAt = store.pendingNewChat.get(botId)
  const newestUserRoot = entries
    .filter((e) => e.kind === 'user' && (e.threadId ?? null) === null && !e.id.startsWith('local-'))
    .sort((a, b) => b.createdAt - a.createdAt)[0]
  useEffect(() => {
    if (pendingChatAt === undefined || !newestUserRoot) return
    if (newestUserRoot.createdAt < pendingChatAt - 5_000) return
    setActiveChat(newestUserRoot.id)
    store.cancelNewChat(botId)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pendingChatAt, newestUserRoot?.id])

  const openChat = (id: string) => {
    setActiveChat(id)
    store.markAllRead(botId)
  }
  const startNewChat = () => {
    setActiveChat(null)
    store.startNewChat(botId)
  }

  // The roster's Edit Profile: land straight in the settings, not the small details.
  const settingsReq = app.settingsRequest?.ref.botId === botId && app.settingsRequest.ref.computerId === store.computer.id ? app.settingsRequest : null
  useEffect(() => {
    if (!settingsReq) return
    app.consumeSettingsRequest(settingsReq.nonce)
    setOpenThread(null)
    setShowComputer(false)
    setFindOpen(false)
    setEditingDetails(true)
    setShowSettings(true)
    setCompactDetails(true)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [settingsReq?.nonce])

  useLayoutEffect(() => {
    const el = root.current
    if (!el) return
    const observer = new ResizeObserver(([e]) => setWidth(e!.contentRect.width))
    observer.observe(el)
    // The composer floats over the transcript; its height becomes the transcript's bottom padding.
    const docked = new ResizeObserver(([e]) => el.style.setProperty('--composer-height', `${Math.ceil(e!.borderBoxSize[0]!.blockSize)}px`))
    if (dock.current) docked.observe(dock.current)
    return () => {
      observer.disconnect()
      docked.disconnect()
    }
  }, [])

  const wide = width >= WIDE
  // A compact Details presentation dismisses once the window is wide enough for the inspector.
  useEffect(() => {
    if (wide) setCompactDetails(false)
  }, [wide])
  const toggleDetails = useCallback(() => {
    // One panel at a time: the name pill closes the Computer panel, the monitor closes Details.
    setShowComputer(false)
    setShowChats(false)
    if (!wide) {
      setOpenThread(null)
      setCompactDetails((c) => !c)
    } else if (openThread) {
      // The thread occupies the inspector slot: << shows details instead of toggling behind it.
      setOpenThread(null)
      setShowSettings(true)
    } else setShowSettings((s) => !s)
  }, [wide, openThread])

  const toggleComputer = useCallback(() => {
    setShowComputer((s) => !s)
    setShowChats(false)
    setShowSettings(false)
    setCompactDetails(false)
    if (!wide) setOpenThread(null)
  }, [wide])

  const toggleChats = useCallback(() => {
    setShowChats((s) => !s)
    setShowComputer(false)
    setShowSettings(false)
    setCompactDetails(false)
    if (!wide) setOpenThread(null)
  }, [wide])

  useEffect(
    () =>
      window.codync.app.onCommand((c) => {
        if (c.kind === 'toggleDetails') toggleDetails()
      }),
    [toggleDetails],
  )

  const openTrace = useCallback(() => setShowTrace(true), [])
  const openThreadOn = useCallback((root: { id: string }) => setOpenThread(root.id), [])
  const closeThread = useCallback(() => setOpenThread(null), [])
  /** The ⋯ menu's Settings (same landing as the Computer panel's gear). */
  const openSettingsPanel = useCallback(() => {
    setShowComputer(false)
    setShowChats(false)
    setOpenThread(null)
    setEditingDetails(true)
    setShowSettings(true)
    setCompactDetails(true)
  }, [])

  const presentRoutine = (id: string | null) => {
    setRoutineId(id)
    setRoutineRequest((r) => r + 1)
    setEditingDetails(false)
    setOpenThread(null)
    setShowSettings(true)
    if (!wide) setCompactDetails(true)
  }

  const details = (
    <DetailsPanel
      key={routineRequest}
      botId={botId}
      routineId={routineId}
      editing={editingDetails}
      setEditing={setEditingDetails}
      openPlugins={openPlugins}
      share={() => {
        setCompactDetails(false)
        if (bot) setTemplateDraft(draftOf(bot))
      }}
      close={() => {
        setShowSettings(false)
        setCompactDetails(false)
      }}
    />
  )

  return (
    <div className="thread-view" ref={root}>
      <div className="conversation">
        <div className="top-chrome">
          <TopFade />
          <div className="top-chrome-row">
            <span style={{ flex: 1 }} />
            {bot && !isGroup(bot) && !calling ? <ModelChip bot={bot} /> : null}
            {bot && !isGroup(bot) && !calling ? (
              <MacChatButton title="Computer" icon="display" direction={[0, 0]} className={showComputer ? 'active' : undefined} onClick={toggleComputer} />
            ) : null}
            {bot && !calling ? (
              <ChatHeaderMenu
                bot={bot}
                showComputer={showComputer}
                withComputer={!isGroup(bot)}
                onFind={() => setFindOpen(true)}
                onChats={toggleChats}
                onFull={openTrace}
                onComputer={toggleComputer}
                onThreadModels={() => setThreadModelsSignal((n) => n + 1)}
                onNewSession={() => setConfirmNewSession({ thread: null })}
                onSettings={openSettingsPanel}
              />
            ) : null}
          </div>
          {/* The call bar takes the pill's place; its frosted glass would show the name blurred. */}
          <button className={`title-pill ${calling ? 'hidden-by-call' : ''}`} inert={calling} onClick={toggleDetails} aria-label="View conversation details" title="View conversation details">
            <span style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
              {bot ? isGroup(bot) ? <GroupAvatar members={store.members(bot)} size={22} /> : <BotAvatar bot={bot} size={22} /> : null}
              <span style={{ ...font(13, 'medium'), color: 'var(--text)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{bot?.name ?? ''}</span>
            </span>
            {store.shownConnection.kind !== 'online' || store.mismatch ? <ConnectionSubtitle /> : null}
          </button>
          {bot && !isGroup(bot) && !calling ? <ThreadModelsLine bot={bot} store={store} openSignal={threadModelsSignal} /> : null}
        </div>
        {findOpen && !calling ? <ChatFindBar botId={botId} store={store} close={() => setFindOpen(false)} /> : null}
        {activeChat ? (
          <div className="chat-mode-bar">
            <button className="chat-mode-back press" onClick={() => setActiveChat(null)}>
              <Icon name="chevron.left" size={10} weight="semibold" />
              All chats
            </button>
            <span className="chat-mode-title">{chats.find((c) => c.id === activeChat)?.title ?? 'Chat'}</span>
            <span style={{ flex: 1 }} />
            <button className="chat-mode-back press" onClick={() => setConfirmNewSession({ thread: activeChat })}>
              New session
            </button>
          </div>
        ) : store.pendingNewChat.has(botId) ? (
          <div className="chat-mode-bar pending">
            <Icon name="plus.circle" size={11} color="var(--secondary)" />
            <span className="chat-mode-title">New chat — this message starts a fresh context</span>
            <button className="chat-mode-back press" onClick={() => store.cancelNewChat(botId)}>Cancel</button>
          </div>
        ) : null}
        {store.pendingNewChat.has(botId) && !activeChat ? (
          <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24 }}>
            <span style={{ ...font('footnote'), color: 'var(--tertiary)', textAlign: 'center', maxWidth: 320 }}>
              This chat is empty. Send a message to start it — it runs on a fresh context.
            </span>
          </div>
        ) : (
        <Transcript botId={botId} chatRoot={activeChat} openTrace={openTrace} openThread={openThreadOn} openRoutine={presentRoutine} openBotChat={setBotChat} focusEntryId={focus && !focus.threadId ? focus.entryId : null} />
        )}
        <div className="composer-dock" ref={dock}>
          <div className="composer-fade" />
          {store.mismatch ? (
            <div style={{ padding: '0 16px 8px' }}>
              <UpdateNeededCard />
            </div>
          ) : (
            <Composer
              botId={botId}
              thread={activeChat}
              onCall={!calling && bot ? () => setCalling(true) : null}
              onInterrupt={callSpeaking ? () => interruptCall.current?.() : null}
            />
          )}
        </div>
        {calling ? (
          <div className="call-overlay">
            <CallView botId={botId} onSpeaking={setCallSpeaking} interrupt={interruptCall} onEnd={() => setCalling(false)} />
          </div>
        ) : null}
      </div>
      <ChatSidePanel
        open={wide && (!!openThread || showComputer || showSettings || showChats)}
        width={openThread ? Math.min(420, width * 0.45) : showComputer ? panelWidth : 400}
      >
        {openThread ? (
          <RepliesView key={openThread} botId={botId} rootId={openThread} close={closeThread} />
        ) : showChats && bot ? (
          <ChatsPanel
            bot={bot}
            chats={chats}
            activeChat={activeChat}
            onOpen={(id) => openChat(id)}
            onNew={() => startNewChat()}
            close={() => setShowChats(false)}
          />
        ) : showComputer && bot && !isGroup(bot) ? (
          <ComputerPanel
            bot={bot}
            store={store}
            width={panelWidth}
            onWidth={setPanelWidth}
            settings={openSettingsPanel}
            close={() => setShowComputer(false)}
          />
        ) : (
          details
        )}
      </ChatSidePanel>

      <Sheet open={compactDetails && !wide} onClose={() => setCompactDetails(false)} width={400} height={600}>
        {details}
      </Sheet>
      <Sheet open={showChats && !wide && !!bot} onClose={() => setShowChats(false)} width={400} height={600}>
        {bot ? (
          <ChatsPanel
            bot={bot}
            chats={chats}
            activeChat={activeChat}
            onOpen={(id) => {
              openChat(id)
              setShowChats(false)
            }}
            onNew={() => {
              startNewChat()
              setShowChats(false)
            }}
            close={() => setShowChats(false)}
          />
        ) : null}
      </Sheet>
      <Sheet open={showComputer && !wide && !!bot && !isGroup(bot)} onClose={() => setShowComputer(false)} width={panelWidth} height={Math.min(720, window.innerHeight - 60)}>
        {bot && !isGroup(bot) ? (
          <ComputerPanel
            bot={bot}
            store={store}
            width={panelWidth}
            onWidth={setPanelWidth}
            settings={openSettingsPanel}
            close={() => setShowComputer(false)}
          />
        ) : null}
      </Sheet>
      <Sheet open={openThread !== null && !wide} onClose={closeThread} width={440} height={600}>
        {openThread ? <RepliesView botId={botId} rootId={openThread} close={closeThread} /> : null}
      </Sheet>
      <Sheet open={botChat !== null} onClose={() => setBotChat(null)} width={620} height={560}>
        {botChat ? <BotConversationView botId={botId} peerId={botChat} /> : null}
      </Sheet>
      <Dialog
        open={confirmNewSession !== null}
        title={
          confirmNewSession?.thread
            ? 'Start a new session in this chat?'
            : bot && isGroup(bot)
              ? 'Start a new session in this room?'
              : `Start a new session with ${bot?.name ?? 'this bot'}?`
        }
        message={
          confirmNewSession?.thread
            ? 'This chat starts with a fresh context on your next message. Its history stays.'
            : bot && isGroup(bot)
              ? 'Every member starts fresh in this group. Their own chats keep their context; this room keeps its history.'
              : 'The agent starts with a fresh context. Its chats keep their own.'
        }
        actions={[{
          title: 'New session',
          destructive: true,
          action: () => {
            if (bot) void store.newSession(bot.id, confirmNewSession?.thread ?? null)
            setConfirmNewSession(null)
          },
        }]}
        onClose={() => setConfirmNewSession(null)}
      />
      <Sheet open={showTrace} onClose={() => setShowTrace(false)} width={620} height={560}>
        <TraceView botId={botId} />
      </Sheet>
      <Sheet open={templateDraft !== null} onClose={() => setTemplateDraft(null)}>
        {templateDraft ? <BotTemplateView draft={templateDraft} /> : null}
      </Sheet>
    </div>
  )
}

/** A chat's entries: its root message plus everything threaded under it. */
function chatEntries(store: ReturnType<typeof useStore>, botId: string, root: string): Entry[] {
  const all = store.allEntries(botId)
  const first = all.find((e) => e.id === root)
  const replies = all.filter((e) => e.threadId === root).sort((a, b) => a.seq - b.seq)
  return first ? [first, ...replies] : replies
}

/** The top edge of the chat: messages fade out gradually as they scroll under the title pill. */
function TopFade() {
  return <div className="top-fade" />
}

function ConnectionSubtitle() {
  const store = useStore()
  const connecting = store.shownConnection.kind === 'connecting'
  const symbol = store.connection.kind !== 'online' ? 'wifi.slash' : store.hostRoute === 'relay' ? 'cloud' : store.hostRoute === 'direct' ? 'wifi' : store.hostRoute === 'loopback' ? 'desktopcomputer' : 'network'
  const description = (() => {
    switch (store.shownConnection.kind) {
      case 'connecting':
        return 'Connecting'
      case 'computerOffline':
      case 'offline':
        return 'Offline'
      case 'unauthorized':
        return 'No access'
      case 'unpaired':
        return 'Not paired'
    }
    if (store.mismatch) return 'Needs update'
    return store.hostRoute === 'relay' ? 'Connected through Cloudflare' : store.hostRoute === 'direct' ? 'Connected over Wi-Fi or Tailscale' : store.hostRoute === 'loopback' ? 'Connected locally' : 'Connected'
  })()
  return (
    <span style={{ display: 'flex', alignItems: 'center', gap: 6, ...font(10, 'medium'), color: 'var(--secondary)' }} title={`${store.hostName} · ${description}`} aria-label={`${store.hostName}, ${description}`}>
      <span style={{ width: 12, display: 'flex', justifyContent: 'center' }}>{connecting ? <Spinner size={8} /> : <Icon name={symbol} size={8} weight="medium" />}</span>
      <span style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{store.connectionLabel}</span>
    </span>
  )
}

/** The messages: every loaded message, brought to the newest as it changes while the reader is at the bottom. */
const PAGE = 40

function Transcript({ botId, chatRoot = null, openTrace, openThread, openRoutine, openBotChat, focusEntryId }: { botId: string; chatRoot?: string | null; openTrace: () => void; openThread: (e: { id: string }) => void; openRoutine: (id: string | null) => void; openBotChat: (peerId: string) => void; focusEntryId: string | null }) {
  const store = useStore()
  const bot = store.bots.get(botId) ?? null
  const thread = chatRoot ? chatEntries(store, botId, chatRoot) : store.chat(botId).filter((e) => e.data.chat !== true)
  const live = !!bot && isWorkingIn(bot, botId, null) && !store.isOffline
  const all = buildChat(thread, true)
  // The newest 40 items first; older pages come in near the top.
  const [firstShown, setFirstShown] = useState<string | null>(null)
  const [loadingEarlier, setLoadingEarlier] = useState(false)
  const found = firstShown ? all.findIndex((i) => i.id === firstShown) : -1
  const start = found >= 0 ? found : Math.max(0, all.length - PAGE)
  const items = all.slice(start)
  const more = start > 0 || (!chatRoot && store.canLoadOlder(botId))
  // Following the newest message, or reading history: scrolling up releases the bottom,
  // arriving back at the end (or Jump to latest) restores it. Content growth alone never changes it.
  const [following, setFollowing] = useState(true)
  const followingRef = useRef(true)
  followingRef.current = following
  const scroller = useRef<HTMLDivElement>(null)
  const lastScrollTop = useRef(0)
  const keepPlace = useRef<number | null>(null)
  const last = items[items.length - 1]
  const arrivals = useArrivals([...items.map((i) => i.id), ...(live ? ['working'] : [])], following)

  const toBottom = (smooth = false) => {
    const el = scroller.current
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
  }

  useEffect(() => {
    if (!firstShown && items[0]) setFirstShown(items[0].id)
  }, [firstShown, items])

  // A search hit older than the shown page pulls the window back to it (and stops following,
  // so the row stays put while it flashes).
  useEffect(() => {
    if (!focusEntryId) return
    const at = all.findIndex((i) => i.id === focusEntryId)
    if (at < 0 || at >= start) return
    setFirstShown(all[Math.max(0, at - 5)]!.id)
    setFollowing(false)
  }, [focusEntryId, all, start])

  const showEarlier = async () => {
    if (loadingEarlier) return
    setLoadingEarlier(true)
    const el = scroller.current
    keepPlace.current = el ? el.scrollHeight - el.scrollTop : null
    let list = all
    let at = start
    if (at === 0 && !chatRoot) {
      const anchor = list[0]?.id
      await store.loadOlder(botId)
      list = buildChat(store.chat(botId), true)
      at = anchor ? Math.max(0, list.findIndex((i) => i.id === anchor)) : 0
    }
    if (at > 0) setFirstShown(list[Math.max(0, at - PAGE)]!.id)
    setLoadingEarlier(false)
  }

  // Earlier messages going in above keep the reader's place; a follower stays at the bottom.
  useLayoutEffect(() => {
    const el = scroller.current
    if (!el) return
    if (keepPlace.current !== null && !followingRef.current) {
      el.scrollTop = el.scrollHeight - keepPlace.current
      keepPlace.current = null
    } else if (followingRef.current) toBottom()
  })

  // A message the user sends brings the chat back to the newest.
  const lastIsUser = last?.kind === 'entry' && last.entry.kind === 'user'
  useEffect(() => {
    if (lastIsUser) setFollowing(true)
  }, [last?.id, lastIsUser])

  // Rows that grow after layout (images, the text size) keep a follower at the bottom.
  useEffect(() => {
    const el = scroller.current
    if (!el) return
    const observer = new ResizeObserver(() => {
      if (followingRef.current) toBottom()
    })
    observer.observe(el.firstElementChild!)
    observer.observe(el)
    return () => observer.disconnect()
  }, [])

  return (
    <div className="transcript-frame">
      <div
        className="transcript"
        ref={scroller}
        onScroll={(e) => {
          const el = e.currentTarget
          const atEnd = el.scrollHeight - el.scrollTop - el.clientHeight < 8
          if (el.scrollTop < lastScrollTop.current - 1 && !atEnd) setFollowing(false)
          else if (atEnd) setFollowing(true)
          lastScrollTop.current = el.scrollTop
          if (more && el.scrollTop < 200 && !loadingEarlier) void showEarlier()
        }}
      >
        <div className="transcript-content">
          {more ? (
            <button className="load-earlier" disabled={loadingEarlier} onClick={() => void showEarlier()}>
              {loadingEarlier ? <Spinner size={14} /> : 'Load earlier messages'}
            </button>
          ) : null}
          {items.length === 0 && bot ? <div style={{ paddingTop: 40 }}>{isGroup(bot) ? <GroupIntroCard group={bot} /> : <IntroCard bot={bot} />}</div> : null}
          {items.map((item) => (
            <div key={item.id} data-mid={item.id} className={arrivals.has(item.id) ? rowIn(item) : undefined}>
              <Row item={item} chat={bot} openTrace={openTrace} openThread={chatRoot ? undefined : openThread} openRoutine={openRoutine} openBotChat={openBotChat} />
            </div>
          ))}
          {bot && live ? (
            <div className={arrivals.has('working') ? 'row-in' : undefined} style={{ paddingTop: 6 }}>
              <WorkingIndicator bot={bot} thinking={store.currentThinking(botId, null)} />
            </div>
          ) : null}
          <div style={{ height: 8 }} />
        </div>
      </div>
      {!following && items.length ? (
        <div className="jump-to-latest" style={{ bottom: 'calc(var(--composer-height, 70px) + 10px)' }}>
          <MacChatButton
            title="Jump to latest"
            icon="arrow.down"
            direction={[0, 1]}
            onClick={() => {
              setFollowing(true)
              toBottom(true)
            }}
          />
        </div>
      ) : null}
    </div>
  )
}

function Row({ item, chat, openTrace, openThread, openRoutine, openBotChat }: { item: ChatItem; chat: Bot | null; openTrace: () => void; openThread?: (e: { id: string }) => void; openRoutine: (id: string | null) => void; openBotChat: (peerId: string) => void }) {
  if (item.kind === 'separator') {
    return <TimeSeparator date={item.date} />
  }
  if (item.kind === 'exchanges') return <BotMessageRow exchanges={item.exchanges} open={openBotChat} />
  const e = item.entry
  if (e.kind === 'notice' && e.data.routineId) {
    return (
      <button onClick={() => openRoutine(e.data.routineId!)} style={{ display: 'flex', alignItems: 'center', gap: 6, ...font('footnote'), color: 'var(--secondary)', padding: '10px 0', margin: '0 auto' }}>
        <Icon name="clock.arrow.circlepath" size={10} />
        {e.data.text ?? 'Routine'}
      </button>
    )
  }
  return <ChatRow entry={e} groupStart={item.groupStart} chat={chat} openTrace={openTrace} openThread={openThread} />
}

/** The start of an empty group chat: who's in it and how the room works. */
function GroupIntroCard({ group }: { group: Bot }) {
  const store = useStore()
  const members = store.members(group)
  const names = members.map((m) => m.name)
  return (
    <div className="intro-card">
      <GroupAvatar members={members} size={72} />
      <div style={{ ...font('title2', 'semibold'), color: 'var(--text)' }}>{group.name}</div>
      {group.name !== names.join(', ') ? <div style={{ ...font('subheadline'), color: 'var(--secondary)' }}>{names.join(' · ')}</div> : null}
      {group.description ? <div style={{ ...font('subheadline'), color: 'var(--secondary)' }}>{group.description}</div> : null}
      <div style={{ ...font('callout'), color: 'var(--secondary)', paddingTop: 4, lineHeight: 1.5, maxWidth: 360, textWrap: 'balance' }}>Everyone answers in turn. @mention a bot to ask just that one.</div>
    </div>
  )
}

function IntroCard({ bot }: { bot: Bot }) {
  const store = useStore()
  return (
    <div className="intro-card">
      <BotAvatar bot={bot} size={72} />
      <div style={{ ...font('title2', 'semibold'), color: 'var(--text)' }}>{bot.name}</div>
      <div style={{ ...font('callout'), color: 'var(--secondary)', overflowWrap: 'anywhere' }}>
        {bot.managedWorkspace ? `${store.backendName(bot.backend)} · Personal workspace` : `${store.backendName(bot.backend)} in ${bot.cwd}`}
      </div>
      {bot.description ? <div style={{ ...font('subheadline'), color: 'var(--secondary)' }}>{bot.description}</div> : null}
      <div style={{ ...font('callout'), color: 'var(--secondary)', paddingTop: 4, lineHeight: 1.5 }}>Tell it what you need. You'll get a notification when it's done or needs you.</div>
    </div>
  )
}
