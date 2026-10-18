// The roster's two kinds — group chats and single bots — and the user's arrangement of them:
// which one sits on top, and which are folded. The kind is the roster's only split; a bot's
// own place stays with its computer.

export const ROSTER_KINDS = ['groups', 'bots'] as const
export type RosterKind = (typeof ROSTER_KINDS)[number]

export const ROSTER_KIND_LABELS: Record<RosterKind, string> = {
  groups: 'Group chats',
  bots: 'Bots',
}

/** The section a roster row belongs to. */
export function rosterKind(bot: { kind?: string }): RosterKind {
  return bot.kind === 'group' ? 'groups' : 'bots'
}

/** The saved order, repaired: unknown entries dropped, missing kinds appended (groups first). */
export function kindOrder(saved: string[]): RosterKind[] {
  const known = saved.filter((id): id is RosterKind => (ROSTER_KINDS as readonly string[]).includes(id))
  return [...known, ...ROSTER_KINDS.filter((kind) => !known.includes(kind))]
}

/** The saved order with `id` dropped next to `target`: which way a grip was dragged. */
export function placeKind(saved: string[], id: string, target: string, place: 'before' | 'after'): string[] {
  if (id === target || !saved.includes(id)) return saved
  const next = saved.filter((value) => value !== id)
  const at = next.indexOf(target)
  if (at < 0) return saved
  next.splice(place === 'before' ? at : at + 1, 0, id)
  return next
}

/** Roster rows in the order the sidebar draws them: one kind's section after another. */
export function itemsByKind<T extends { bot: { kind?: string } }>(items: T[], order: readonly RosterKind[]): T[] {
  return order.flatMap((kind) => items.filter((item) => rosterKind(item.bot) === kind))
}
