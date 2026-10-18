import { useState } from 'react'
import { Button, IconButton, Spinner } from '../../components/Controls'
import { Dialog } from '../../components/Overlay'
import { font } from '../../lib/fonts'
import { useStore } from '../../store/context'
import { CustomConnectorSheet } from './ConnectorSheets'
import { EmptyState, FormSheet, ItemGrid, ManagedRow, ScreenHeader, ServiceLogo } from './pieces'
import { errorText, needsSignIn } from './market-models'
import { usePlugins } from './plugins'

/**
 * The computer's MCP servers (Maus's Apps → MCP servers): what's installed, adding one with a
 * command, a URL or a pasted config, and removing it. Bots turn them on in their settings.
 */
export function McpServersView({ back }: { back: () => void }) {
  const store = useStore()
  const plugins = usePlugins(store)
  const [adding, setAdding] = useState(false)
  const [signingIn, setSigningIn] = useState<string | null>(null)
  const [removing, setRemoving] = useState<{ id: string; name: string } | null>(null)
  const servers = plugins.installedConnectors.filter((c) => c.kind !== 'composio')

  const signIn = async (id: string) => {
    setSigningIn(id)
    try {
      await plugins.signIn(id)
    } catch (e) {
      store.setError(errorText(e))
    }
    setSigningIn(null)
  }

  const remove = async () => {
    if (!removing) return
    try {
      await plugins.removeConnector(removing.id)
    } catch (e) {
      store.setError(errorText(e))
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--background)' }}>
      <ScreenHeader title="MCP servers" onBack={back} trailing={<IconButton title="Add server" icon="plus" onClick={() => setAdding(true)} />} />
      <div className="market-scroll">
        <div className="market-page-content">
          <span style={{ ...font('subheadline'), color: 'var(--secondary)' }}>
            MCP servers give your bots tools of their own. They run on {store.hostName}, and each bot turns on the ones it may use.
          </span>
          {servers.length === 0 ? (
            <EmptyState icon="server.rack" title="No servers yet" detail="Add one with a command, a URL, or a pasted config." />
          ) : (
            <ItemGrid>
              {servers.map((c) => (
                <ManagedRow
                  key={c.id}
                  title={c.name}
                  subtitle={c.command ?? c.url ?? c.description}
                  icon={<ServiceLogo name={c.name} registryName={c.registryName} size={36} />}
                  accessory={signingIn === c.id ? <Spinner /> : needsSignIn(c) ? <Button onClick={() => void signIn(c.id)}>Sign in</Button> : null}
                  onRemove={() => setRemoving({ id: c.id, name: c.name })}
                />
              ))}
            </ItemGrid>
          )}
        </div>
      </div>
      <FormSheet open={adding} onClose={() => setAdding(false)}>
        <CustomConnectorSheet />
      </FormSheet>
      <Dialog
        open={removing !== null}
        title={`Remove ${removing?.name ?? ''}?`}
        message="Bots lose this server, and the keys saved for it are deleted."
        actions={removing ? [{ title: 'Remove', destructive: true, action: () => void remove() }] : []}
        onClose={() => setRemoving(null)}
      />
    </div>
  )
}
