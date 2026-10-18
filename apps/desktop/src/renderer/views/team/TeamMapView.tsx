import { useMemo, useState } from 'react'
import { isGroup, type Bot } from '@shared/models'
import { BotAvatar, GroupAvatar } from '../../components/Avatar'
import { ModalHeader } from '../../components/Overlay'
import { font } from '../../lib/fonts'
import { useApp } from '../../store/context'
import type { BotStore } from '../../store/bot-store'
import './team-map.css'

interface Edge {
  from: string
  to: string
  count: number
}

/** Who asked whom, from the bot-to-bot exchanges recorded on entries. */
function askEdges(store: BotStore, known: Set<string>): Edge[] {
  const counts = new Map<string, number>()
  for (const bot of store.bots.values()) {
    for (const entry of store.allEntries(bot.id)) {
      const message = entry.data.botMessage
      if (!message || message.sourceBotId === message.targetBotId) continue
      if (!known.has(message.sourceBotId) || !known.has(message.targetBotId)) continue
      const key = `${message.sourceBotId}\u0000${message.targetBotId}`
      counts.set(key, (counts.get(key) ?? 0) + 1)
    }
  }
  return [...counts.entries()].map(([key, count]) => {
    const [from, to] = key.split('\u0000') as [string, string]
    return { from, to, count }
  })
}

/**
 * Maus's Team map: the bots and groups of one computer as a ring, with arrows for
 * who asked whom (hover a node to see its edges; a group also shows its members).
 * Read-only: click a node to open that chat.
 */
export function TeamMapView({ store, close }: { store: BotStore; close: () => void }) {
  const app = useApp()
  const [hover, setHover] = useState<string | null>(null)
  const bots = useMemo(() => [...store.bots.values()], [store, store.bots.size, store.bots])
  const groups = bots.filter(isGroup)
  const agents = bots.filter((b) => !isGroup(b))
  const nodes = [...agents, ...groups]
  const edges = useMemo(() => askEdges(store, new Set(nodes.map((n) => n.id))), [store, nodes.length, store.bots.size])

  const W = 640
  const H = 560
  const R = 200
  const at = (index: number) => {
    const angle = (index / Math.max(nodes.length, 1)) * Math.PI * 2 - Math.PI / 2
    return { x: W / 2 + R * Math.cos(angle), y: H / 2 + R * Math.sin(angle) }
  }
  const pos = new Map(nodes.map((node, i) => [node.id, at(i)]))
  const byId = new Map(bots.map((b) => [b.id, b]))
  const memberships = groups.flatMap((group) =>
    group.members.filter((id) => byId.has(id)).map((id) => ({ from: group.id, to: id })),
  )
  const active = hover
  const edgeOn = (from: string, to: string) => !active || from === active || to === active
  const open = (bot: Bot) => {
    app.select({ computerId: store.computer.id, botId: bot.id })
    close()
  }

  return (
    <div className="team-map">
      <ModalHeader title="Team map" />
      <div className="team-map-body">
        {nodes.length === 0 ? (
          <p className="team-map-empty" style={font('footnote')}>No bots on this computer yet.</p>
        ) : (
          <>
            <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Bots, groups and who asked whom">
              <defs>
                <marker id="team-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
                  <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--tertiary)" />
                </marker>
              </defs>
              {groups.map((group) => {
                const from = pos.get(group.id)!
                return memberships
                  .filter((m) => m.from === group.id)
                  .map((m) => {
                    const to = pos.get(m.to)!
                    return (
                      <line
                        key={`${m.from}-${m.to}`}
                        x1={from.x}
                        y1={from.y}
                        x2={to.x}
                        y2={to.y}
                        stroke="var(--tertiary)"
                        strokeWidth={1}
                        strokeDasharray="3 5"
                        opacity={active === group.id ? 0.9 : active ? 0.06 : 0.18}
                      />
                    )
                  })
              })}
              {edges.map((edge) => {
                const from = pos.get(edge.from)
                const to = pos.get(edge.to)
                if (!from || !to) return null
                const mid = { x: (from.x + to.x) / 2, y: (from.y + to.y) / 2 }
                return (
                  <g key={`${edge.from}-${edge.to}`} opacity={edgeOn(edge.from, edge.to) ? (active ? 1 : 0.55) : 0.1}>
                    <line x1={from.x} y1={from.y} x2={to.x} y2={to.y} stroke="var(--tertiary)" strokeWidth={1.4} markerEnd="url(#team-arrow)" />
                    {edge.count > 1 ? (
                      <text x={mid.x} y={mid.y - 5} textAnchor="middle" fill="var(--secondary)" fontSize={10}>
                        {edge.count}
                      </text>
                    ) : null}
                  </g>
                )
              })}
              {nodes.map((node) => {
                const p = pos.get(node.id)!
                const size = 34
                return (
                  <g
                    key={node.id}
                    transform={`translate(${p.x - size / 2} ${p.y - size / 2})`}
                    opacity={!active || active === node.id ? 1 : edges.some((e) => edgeOn(e.from, e.to) && (e.from === node.id || e.to === node.id)) ? 0.55 : 0.25}
                    onMouseEnter={() => setHover(node.id)}
                    onMouseLeave={() => setHover(null)}
                    onClick={() => open(node)}
                    style={{ cursor: 'pointer' }}
                  >
                    <foreignObject width={size} height={size}>
                      {isGroup(node) ? <GroupAvatar members={store.members(node)} size={size} /> : <BotAvatar bot={node} size={size} animated={!!active && active === node.id} />}
                    </foreignObject>
                  </g>
                )
              })}
              {nodes.map((node, index) => {
                const p = pos.get(node.id)!
                const label = node.name.length > 14 ? `${node.name.slice(0, 14)}…` : node.name
                const above = index === 0 || (index > nodes.length / 2 && index < nodes.length * 0.5 + 2)
                return (
                  <text
                    key={`label-${node.id}`}
                    x={p.x}
                    y={above ? p.y - 26 : p.y + 38}
                    textAnchor="middle"
                    fill={active === node.id ? 'var(--text)' : 'var(--secondary)'}
                    fontSize={11.5}
                    onMouseEnter={() => setHover(node.id)}
                    onMouseLeave={() => setHover(null)}
                    style={{ cursor: 'pointer' }}
                    onClick={() => open(node)}
                  >
                    {label}
                  </text>
                )
              })}
            </svg>
            <p className="team-map-note" style={font('footnote')}>
              {edges.length
                ? 'An arrow is a bot asking another for something; dashed lines are group members (hover a group). Click anything to open its chat.'
                : 'The bots here haven\'t asked each other for anything yet — dashed lines are group members (hover a group). Click anything to open its chat.'}
            </p>
          </>
        )}
      </div>
    </div>
  )
}
