import { computerSections, moveInOrder, orderedComputers } from '../lib/computer-roster'
import { itemsByKind, kindOrder, rosterKind } from '../lib/roster-sections'
import { prefs, usePref } from '../lib/prefs'
import { useModel } from '../lib/observable'
import type { BotStore } from './bot-store'
import type { AppModel, BotRef } from './app-model'

/** Device-local section ordering and folding, matching the iPhone roster. */
export function useComputerRoster(app: AppModel, shown: Set<string>) {
  const order = useModel(app.computerOrder).ids
  const [collapsed, setCollapsed] = usePref(prefs.collapsedComputers)
  const [savedKinds] = usePref(prefs.rosterSectionOrder)
  const kinds = kindOrder(savedKinds)
  const computers = orderedComputers(app.computers, order)
  const sections = computerSections(computers.filter((c) => shown.has(c.id)), app.roster)
    .map((section) => ({ ...section, store: app.store(section.computer.id) }))
    .filter((section): section is typeof section & { store: BotStore } => section.store !== null)
  const grouped = sections.length > 1
  return {
    sections,
    grouped,
    collapsed,
    items: sections.flatMap((section) => section.items),
    // Arrow keys walk the roster as it is drawn: Group chats first, then Bots.
    navigable: sections.flatMap((section) => grouped && collapsed.includes(section.computer.id) ? [] : itemsByKind(section.items, kinds)),
    toggle(id: string) {
      setCollapsed(collapsed.includes(id) ? collapsed.filter((value) => value !== id) : [...collapsed, id])
    },
    move(id: string, target: string) {
      app.computerOrder.move(id, target, computers.map((c) => c.id))
    },
    /** One place up (-1) or down (+1) among its computer's bots of the same kind and pin state. */
    moveBot(ref: BotRef, offset: number) {
      const section = sections.find((s) => s.computer.id === ref.computerId)
      const current = section?.items.find((item) => item.bot.id === ref.botId)
      if (!section || !current) return
      const siblings = section.items.filter((item) => rosterKind(item.bot) === rosterKind(current.bot))
      const index = siblings.findIndex((item) => item.bot.id === ref.botId)
      const target = siblings[index + offset]
      if (!target || target.bot.pinned !== current.bot.pinned) return
      section.store.reorder(moveInOrder(siblings.map((item) => item.bot.id), ref.botId, target.bot.id))
    },
  }
}
