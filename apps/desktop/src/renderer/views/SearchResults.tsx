import { useEffect, useRef, useState } from 'react'
import { BotAvatar } from '../components/Avatar'
import { font } from '../lib/fonts'
import type { SearchHit } from '../lib/search-hit'
import { useApp } from '../store/context'
import type { BotStore } from '../store/bot-store'
import './search-results.css'

export const MIN_QUERY = 2
const DEBOUNCE_MS = 250
const LIMIT = 40
const time = new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' })

/** A hit with the computer it came from. */
interface Located {
  hit: SearchHit
  store: BotStore
}

/**
 * The sidebar's message hits: from two characters on, every shown computer is asked for
 * chat-visible messages containing the query. Clicking one lands on the message itself —
 * right chat, right thread — rather than just opening the chat.
 */
export function SearchResults({ stores, query, onLanded }: { stores: BotStore[]; query: string; onLanded: () => void }) {
  const app = useApp()
  const q = query.trim()
  const [hits, setHits] = useState<Located[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const storesRef = useRef(stores)
  storesRef.current = stores
  const storeIds = stores.map((s) => s.computer.id).join(',')

  useEffect(() => {
    if (q.length < MIN_QUERY) {
      setHits(null)
      setError(null)
      return
    }
    // Do not leave the previous query's hits clickable while the debounced request is in flight.
    setHits(null)
    setError(null)
    let alive = true
    const timer = setTimeout(() => {
      void Promise.all(storesRef.current.map(async (store) => {
        const messages = await store.search(q, LIMIT)
        return messages.map((hit): Located => ({ hit, store }))
      }))
        .then((all) => {
          if (!alive) return
          setHits(all.flat().sort((a, b) => b.hit.at - a.hit.at).slice(0, LIMIT))
        })
        .catch((e: unknown) => alive && setError(e instanceof Error ? e.message : String(e)))
    }, DEBOUNCE_MS)
    return () => {
      alive = false
      clearTimeout(timer)
    }
  }, [q, storeIds])

  if (q.length < MIN_QUERY) return null

  const land = (located: Located) => {
    app.showMessage({ computerId: located.store.computer.id, botId: located.hit.botId }, located.hit.entryId, located.hit.threadId)
    onLanded()
  }

  return (
    <div className="search-results">
      <div className="search-results-heading" style={font(11, 'medium')}>
        {hits ? `Messages · ${hits.length}${hits.length === LIMIT ? '+' : ''}` : 'Messages'}
      </div>
      {error ? <div className="search-results-note" style={font(12.5)}>Couldn't search: {error}</div> : null}
      {hits && hits.length === 0 && !error ? (
        <div className="search-results-note" style={font(12.5)}>No messages match “{q}”</div>
      ) : null}
      {hits?.map(({ hit, store }) => {
        const bot = store.bots.get(hit.botId)
        // A group member's own name; in a bot's chat `author` is the bot itself.
        const author = hit.author && hit.author !== hit.botId ? store.bots.get(hit.author) : undefined
        return (
          <button key={hit.entryId} className="search-hit press" onClick={() => land({ hit, store })}>
            {bot ? (
              <BotAvatar bot={bot} size={26} animated={false} />
            ) : (
              <span className="search-hit-hash" style={font(11)}>#</span>
            )}
            <span className="search-hit-body">
              <span className="search-hit-meta" style={font(12)}>
                <span className="search-hit-name">{hit.botName}</span>
                {author ? <span className="search-hit-who">· {author.name}</span> : null}
                <span className="search-hit-time">{time.format(hit.at)}</span>
              </span>
              <span className="search-hit-text" style={font(12.5)} dir="auto">
                {hit.snippet.before}
                <mark>{hit.snippet.matched}</mark>
                {hit.snippet.after}
              </span>
            </span>
          </button>
        )
      })}
    </div>
  )
}
