import type { RosterItem } from '../store/app-model'

/** Bots and groups matching a query by name, description or last message; everything for an empty query. */
export function matchRoster(items: RosterItem[], query: string): RosterItem[] {
  const q = query.trim().toLowerCase()
  if (!q) return items
  return items.filter(({ bot }) => {
    const sources = [bot.name, bot.description, bot.lastMessage ?? '']
    return sources.some((s) => s.toLowerCase().includes(q))
  })
}
