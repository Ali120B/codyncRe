import { useState } from 'react'
import type { Backend } from '@shared/models'
import { CardForm, CardSection } from '../../components/Controls'
import { Icon } from '../../components/Icon'
import { ThinkingOrb } from '../../components/ThinkingOrb'
import { font } from '../../lib/fonts'
import { useModel } from '../../lib/observable'
import { useStore } from '../../store/context'
import { AgentSheet, AgentTile, NodeNotice } from '../marketplace/AgentSheet'
import { errorText } from '../marketplace/market-models'
import { FormSheet } from '../marketplace/pieces'
import { SetupTerminalView } from '../marketplace/SetupTerminal'

/**
 * Settings → AI (Maus's AI page): the agents this computer can run bots on — what's ready,
 * what still needs setup, and "Check again". A card opens the agent's own setup (install,
 * Node.js, sign in); bots pick their agent when they're made.
 */
export function AiSettings() {
  const store = useStore()
  const model = useModel(store)
  const backends = (model.hello?.backends ?? []).filter((b) => b.available || b.installed === true || b.curated === true)
  // The host marks every catalog agent "available" (it can install it on demand); only
  // `installed` means the agent is actually on this computer.
  const ready = backends.filter((b) => b.installed === true && b.available)
  const needsSetup = backends.filter((b) => !(b.installed === true && b.available))
  const [agent, setAgent] = useState<Backend | null>(null)
  const [nodeSetup, setNodeSetup] = useState(false)
  const [checking, setChecking] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const check = async () => {
    setChecking(true)
    try {
      await store.refreshBackends()
      setError(null)
    } catch (e) {
      setError(errorText(e))
    }
    setChecking(false)
  }

  return (
    <>
      <CardForm>
        <CardSection
          title="Model providers"
          footer={`Agents run on ${store.hostName} and answer as your bots. Sign in here once; each bot picks its model when you make it.`}
        >
          <div className="settings-row">
            <div className="settings-stack" style={{ flex: 1 }}>
              <span>Model providers and accounts</span>
              <span style={{ ...font('caption'), color: 'var(--secondary)' }}>
                {ready.length} ready · {needsSetup.length} need setup
              </span>
            </div>
            <button className="engine-check" disabled={checking} onClick={() => void check()}>
              {checking ? <ThinkingOrb size={13} color="var(--secondary)" /> : <Icon name="arrow.clockwise" size={13} />}
              {checking ? 'Checking…' : 'Check again'}
            </button>
          </div>
        </CardSection>
        {backends.some((b) => b.node === 'missing') ? <NodeNotice onAdd={() => setNodeSetup(true)} /> : null}
        <div className="engine-groups">
          <EngineGroup title="Ready" backends={ready} onOpen={setAgent} />
          <EngineGroup title="Needs setup" backends={needsSetup} onOpen={setAgent} />
        </div>
        {error ? <span style={{ ...font('footnote'), color: 'var(--danger)' }}>{error}</span> : null}
      </CardForm>
      <FormSheet open={agent !== null} onClose={() => setAgent(null)}>
        {agent ? <div style={{ height: '100%' }}><AgentSheet initial={agent} /></div> : null}
      </FormSheet>
      <FormSheet open={nodeSetup} onClose={() => setNodeSetup(false)}>
        <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
          <SetupTerminalView backend={null} step="node" back={() => setNodeSetup(false)} />
        </div>
      </FormSheet>
    </>
  )
}

/** One group of agent cards with its quiet heading and count. */
function EngineGroup({ title, backends, onOpen }: { title: string; backends: Backend[]; onOpen: (b: Backend) => void }) {
  if (backends.length === 0) return null
  return (
    <div className="engine-group">
      <div className="engine-group-head">
        <span className="engine-group-title">{title}</span>
        <span className="engine-group-count">{backends.length}</span>
      </div>
      <div className="engine-grid">
        {backends.map((b) => (
          <EngineCard key={b.id} backend={b} onOpen={() => onOpen(b)} />
        ))}
      </div>
    </div>
  )
}

/** One agent: its logo and name, the provider under it, and how ready it is. */
function EngineCard({ backend, onOpen }: { backend: Backend; onOpen: () => void }) {
  const ready = backend.installed === true && backend.available
  return (
    <button className="engine-card" onClick={onOpen} title={backend.description ?? backend.name}>
      <div className="engine-card-top">
        <AgentTile registry={backend.registry ?? backend.id} icon={20} tile={38} radius={10} />
        <div className="engine-card-text">
          <span className="engine-card-name">{backend.name}</span>
          <span className="engine-card-sub">{backend.description ?? backend.command}</span>
        </div>
        <Icon name="chevron.right" size={10} weight="semibold" color="var(--tertiary)" />
      </div>
      <div className="engine-card-bottom">
        {ready ? (
          <span className="engine-pill ready">
            <Icon name="checkmark" size={9} weight="bold" />
            Ready
          </span>
        ) : (
          <span className="engine-pill">
            <span className="engine-dot" aria-hidden />
            Needs setup
          </span>
        )}
        {ready ? null : <span className="engine-setup">Set up</span>}
      </div>
    </button>
  )
}
