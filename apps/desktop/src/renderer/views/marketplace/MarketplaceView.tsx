import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Button, DropdownMenu, IconButton, Spinner } from '../../components/Controls'
import { Icon } from '../../components/Icon'
import { Dialog, useDismiss } from '../../components/Overlay'
import { font } from '../../lib/fonts'
import { useStore } from '../../store/context'
import { ComposioSection } from './ComposioViews'
import { CredentialsView } from './CredentialsView'
import { CustomConnectorSheet, InstallConnectorSheet, NewSkillSheet } from './ConnectorSheets'
import { McpServersView } from './McpServersView'
import { SkillSheet, type SkillRef } from './SkillSheet'
import {
  appendPage, emptyPage, errorText, hasHiddenItems, market, needsSignIn, replacePage, revealMore, visible,
  type MarketConnector, type MarketSkill,
} from './market-models'
import {
  AppLogo, EmptyState, FormSheet, ItemGrid, ManagedRow, MarketLink, MarketRow, MarketSection,
  ScreenHeader, ServiceLogo, SkeletonGrid, SkillGlyph, SlideStack, TileIcon,
} from './pieces'
import { usePlugins } from './plugins'

const connectorId = (c: MarketConnector) => c.name

/** Cards shown per section before "View all" (two rows of the grid). */
const SECTION_ROWS = 4

type Kind = 'all' | 'apps' | 'connectors' | 'skills'
const KINDS: readonly [Kind, string][] = [['all', 'All'], ['apps', 'Apps'], ['connectors', 'Connectors'], ['skills', 'Skills']]

/**
 * Plugins (Maus's Apps screen): apps through Composio, connectors from the MCP Registry and
 * skills, for the bots on one computer. Everything installs on that computer; each bot turns
 * what it may use on in its settings. The header switches computers when more than one is online.
 */
export function MarketplaceView({ computers, computer, onComputer }: { computers: { id: string; name: string }[]; computer: string; onComputer: (id: string) => void }) {
  const store = useStore()
  const plugins = usePlugins(store)
  const dismiss = useDismiss()
  const [search, setSearch] = useState('')
  /** Bumped on each submitted search, so every section searches again. */
  const [searchToken, setSearchToken] = useState(0)
  const [kind, setKind] = useState<Kind>('all')
  const [connectors, setConnectors] = useState(emptyPage<MarketConnector>)
  /** The registry page after the ones shown; null at the end. */
  const [connectorCursor, setConnectorCursor] = useState<string | null>(null)
  const [loadingMore, setLoadingMore] = useState(false)
  const [skills, setSkills] = useState<MarketSkill[]>([])
  const [loadingConnectors, setLoadingConnectors] = useState(true)
  const [loadingSkills, setLoadingSkills] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [installing, setInstalling] = useState<MarketConnector | null>(null)
  const [busySkill, setBusySkill] = useState<string | null>(null)
  const [addingConnector, setAddingConnector] = useState(false)
  const [writingSkill, setWritingSkill] = useState(false)
  const [viewingSkill, setViewingSkill] = useState<SkillRef | null>(null)
  const [skillsExpanded, setSkillsExpanded] = useState(false)
  const [showInstalled, setShowInstalled] = useState(false)
  const [showCredentials, setShowCredentials] = useState(false)
  const [showMcp, setShowMcp] = useState(false)
  const request = useRef(0)
  const connectorQuery = useRef('')

  const query = search.trim()
  const matches = (text: string | null | undefined) => (text ?? '').toLowerCase().includes(query.toLowerCase())
  const shownSkills = query ? skills.filter((s) => matches(s.name) || matches(s.description)) : skills
  const installedCount = plugins.installedConnectors.length + plugins.installedSkills.length

  const loadConnectors = async (q = query) => {
    const id = ++request.current
    connectorQuery.current = q
    setLoadingConnectors(true)
    setLoadingMore(false)
    try {
      const page = await market.marketConnectors(await store.ready(), q)
      if (request.current !== id) return
      setConnectors(replacePage(page.items, connectorId))
      setConnectorCursor(page.next)
      setError(null)
    } catch (e) {
      if (request.current !== id) return
      setConnectors(emptyPage())
      setConnectorCursor(null)
      setError(errorText(e))
    } finally {
      if (request.current === id) setLoadingConnectors(false)
    }
  }

  const loadSkills = async () => {
    setLoadingSkills(true)
    try {
      setSkills(await market.marketSkills(await store.ready()))
    } catch (e) {
      setError(errorText(e))
    }
    setLoadingSkills(false)
  }

  const loadMoreConnectors = async () => {
    if (loadingMore || loadingConnectors) return
    if (hasHiddenItems(connectors)) {
      setConnectors(revealMore)
      return
    }
    if (!connectorCursor) return
    const id = request.current
    setLoadingMore(true)
    try {
      const page = await market.marketConnectors(await store.ready(), connectorQuery.current, connectorCursor)
      if (request.current !== id) return
      setConnectors((old) => revealMore(appendPage(old, page.items, connectorId)))
      setConnectorCursor(page.next)
      setError(null)
    } catch (e) {
      if (request.current === id) setError(errorText(e))
    } finally {
      if (request.current === id) setLoadingMore(false)
    }
  }

  const reloadAll = () => {
    void plugins.refresh()
    void loadConnectors(connectorQuery.current)
    void loadSkills()
  }

  useEffect(() => {
    reloadAll()
    return () => void request.current++
    // Mount only, like the view's `.task`s.
  }, [])

  const submit = (q: string) => {
    setSearchToken((t) => t + 1)
    setSkillsExpanded(false)
    void loadConnectors(q)
  }

  const installSkill = async (s: MarketSkill) => {
    setBusySkill(s.source)
    try {
      await plugins.installSkill(s.source)
    } catch (e) {
      setError(errorText(e))
    }
    setBusySkill(null)
  }

  const all = kind === 'all'
  const connectorRows = all ? visible(connectors).slice(0, SECTION_ROWS) : visible(connectors)
  const skillRows = all && !skillsExpanded ? shownSkills.slice(0, SECTION_ROWS * 2) : shownSkills

  const header = (
    <div className="market-heading">
      {/* Everything here lives on one computer; say which. */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 5, minWidth: 0, flex: 1 }}>
        <span style={{ ...font('title', 'semibold'), color: 'var(--text)' }}>Plugins</span>
        <span style={{ ...font('subheadline'), color: 'var(--secondary)' }}>
          Connect an app or your own MCP server once. Then choose which bots may use it.
        </span>
        {computers.length > 1 ? (
          <DropdownMenu
            title="Switch computer"
            style={{ display: 'flex', alignItems: 'center', gap: 4, alignSelf: 'flex-start', ...font('subheadline'), color: 'var(--secondary)', minWidth: 0 }}
            items={() => computers.map((c) => ({ title: c.name, selected: c.id === computer, action: () => onComputer(c.id) }))}
          >
            <Icon name="desktopcomputer" size={11} />
            <span aria-label={`Computer: ${store.hostName}`} style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>{store.hostName}</span>
            <Icon name="chevron.down" size={10} weight="semibold" />
          </DropdownMenu>
        ) : (
          <span style={{ display: 'flex', alignItems: 'center', gap: 4, ...font('subheadline'), color: 'var(--secondary)', minWidth: 0 }}>
            <Icon name="desktopcomputer" size={11} />
            <span style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>{store.hostName}</span>
          </span>
        )}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flex: 'none' }}>
        {installedCount > 0 ? <MarketLink title="Connected" onClick={() => setShowInstalled(true)} /> : null}
        <IconButton title="Check again" icon="arrow.clockwise" onClick={reloadAll} />
      </div>
    </div>
  )

  const searchField = (
    <div className="market-search">
      <Icon name="magnifyingglass" size={13} color="var(--secondary)" />
      <input
        value={search}
        placeholder="Search apps, connectors and skills"
        aria-label="Search apps, connectors and skills"
        spellCheck={false}
        onChange={(e) => setSearch(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && !e.nativeEvent.isComposing) submit(search.trim())
        }}
      />
      {search ? (
        <button
          aria-label="Clear search"
          title="Clear search"
          style={{ display: 'flex', color: 'var(--tertiary)' }}
          onClick={() => {
            setSearch('')
            submit('')
          }}
        >
          <Icon name="xmark.circle.fill" size={13} />
        </button>
      ) : null}
    </div>
  )

  const centered = (node: ReactNode) => <div style={{ display: 'flex', justifyContent: 'center' }}>{node}</div>

  const page = (
    <div className="market-page">
      <div className="market-scroll">
        <div className="market-page-content">
          {header}
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            {searchField}
            <button className="press mcp-button" title="MCP servers" onClick={() => setShowMcp(true)}>
              <Icon name="server.rack" size={13} />
              MCP servers
            </button>
          </div>
          <div className="chips">
            {KINDS.map(([id, label]) => (
              <button key={id} className={`press chip ${kind === id ? 'selected' : ''}`} aria-pressed={kind === id} onClick={() => setKind(id)}>
                {label}
              </button>
            ))}
          </div>
          {kind !== 'connectors' && kind !== 'skills' ? (
            <ComposioSection
              query={query}
              searchToken={searchToken}
              limit={all ? SECTION_ROWS : undefined}
              onViewAll={all ? () => setKind('apps') : undefined}
            />
          ) : null}
          {kind !== 'apps' && kind !== 'skills' ? (
            <MarketSection
              title="Connectors"
              action={
                all && (connectorRows.length < visible(connectors).length || connectorCursor) ? (
                  <MarketLink title="View all" onClick={() => setKind('connectors')} />
                ) : undefined
              }
            >
              {loadingConnectors ? (
                <SkeletonGrid />
              ) : connectors.items.length === 0 ? (
                <span style={{ color: 'var(--secondary)' }}>{query ? `No connectors match “${query}”.` : "Couldn't load connectors."}</span>
              ) : (
                <>
                  <ItemGrid>
                    {connectorRows.map((c) => (
                      <MarketRow
                        key={c.name}
                        title={c.title}
                        subtitle={c.description ?? c.name}
                        added={c.installed}
                        icon={<ServiceLogo website={c.website} name={c.title} registryName={c.name} size={36} />}
                        onAdd={() => setInstalling(c)}
                      />
                    ))}
                  </ItemGrid>
                  {all && (hasHiddenItems(connectors) || connectorCursor || visible(connectors).length > connectorRows.length)
                    ? centered(
                        <Button kind="secondary" disabled={loadingMore} onClick={() => void loadMoreConnectors()}>
                          {loadingMore ? 'Loading…' : 'Show more connectors'}
                        </Button>,
                      )
                    : null}
                  {!all && (hasHiddenItems(connectors) || connectorCursor)
                    ? centered(
                        <Button kind="secondary" disabled={loadingMore} onClick={() => void loadMoreConnectors()}>
                          {loadingMore ? 'Loading…' : 'Load more connectors'}
                        </Button>,
                      )
                    : null}
                </>
              )}
            </MarketSection>
          ) : null}
          {kind !== 'apps' && kind !== 'connectors' ? (
            <MarketSection
              title="Skills"
              action={all && shownSkills.length > skillRows.length ? <MarketLink title="View all" onClick={() => setKind('skills')} /> : undefined}
            >
              {loadingSkills ? (
                <SkeletonGrid />
              ) : (
                <ItemGrid>
                  {skillRows.map((s) => (
                    <MarketRow
                      key={s.source}
                      title={s.name}
                      subtitle={s.description}
                      added={s.installed || plugins.installedSkills.some((i) => i.id === s.source.toLowerCase())}
                      busy={busySkill === s.source}
                      icon={<SkillGlyph />}
                      onAdd={() => void installSkill(s)}
                      onOpen={() => setViewingSkill(s)}
                    />
                  ))}
                </ItemGrid>
              )}
              {all && !skillsExpanded && shownSkills.length > skillRows.length ? (
                centered(<Button kind="secondary" onClick={() => setSkillsExpanded(true)}>Show more skills</Button>)
              ) : null}
            </MarketSection>
          ) : null}
          <div style={{ display: 'flex' }}>
            <Button kind="secondary" onClick={() => setShowCredentials(true)}>
              <Icon name="lock.shield" size={12} />
              Credentials
            </Button>
          </div>
          {kind !== 'apps' ? (
            <MarketSection title="Make your own">
              <ItemGrid>
                {kind !== 'skills' ? (
                  <MarketRow
                    title="Custom connector"
                    subtitle="Any MCP server: a command or a URL"
                    added={false}
                    addLabel="New"
                    icon={<TileIcon name="point.3.connected.trianglepath.dotted" />}
                    onAdd={() => setAddingConnector(true)}
                  />
                ) : null}
                {kind !== 'connectors' ? (
                  <MarketRow
                    title="Your own skill"
                    subtitle="Write instructions a bot can follow"
                    added={false}
                    addLabel="New"
                    icon={<TileIcon name="square.and.pencil" />}
                    onAdd={() => setWritingSkill(true)}
                  />
                ) : null}
              </ItemGrid>
            </MarketSection>
          ) : null}
          {error ? <span style={{ ...font('footnote'), color: 'var(--danger)' }}>{error}</span> : null}
          <span style={{ ...font('footnote'), color: 'var(--tertiary)' }}>
            Connectors come from the official MCP Registry, apps through Composio, skills from Anthropic. Everything installs on {store.hostName}.
          </span>
        </div>
      </div>
      {/* Dismissing slides the sheet away; clearing the caller's state would cut it off. */}
      <div className="market-close">
        <IconButton title="Close" icon="xmark" onClick={dismiss} />
      </div>
    </div>
  )

  return (
    <>
      <SlideStack
        base={page}
        top={
          showCredentials ? (
            <CredentialsView onBack={() => setShowCredentials(false)} />
          ) : showInstalled ? (
            <InstalledView back={() => setShowInstalled(false)} />
          ) : showMcp ? (
            <McpServersView back={() => setShowMcp(false)} />
          ) : null
        }
      />
      <FormSheet open={installing !== null} onClose={() => setInstalling(null)}>
        {installing ? <InstallConnectorSheet key={installing.name} item={installing} done={() => void loadConnectors(connectorQuery.current)} /> : null}
      </FormSheet>
      <FormSheet open={addingConnector} onClose={() => setAddingConnector(false)}>
        <CustomConnectorSheet />
      </FormSheet>
      <FormSheet open={writingSkill} onClose={() => setWritingSkill(false)}>
        <NewSkillSheet />
      </FormSheet>
      <FormSheet open={viewingSkill !== null} onClose={() => setViewingSkill(null)}>
        {viewingSkill ? <SkillSheet key={viewingSkill.name} skill={viewingSkill} /> : null}
      </FormSheet>
    </>
  )
}

// MARK: - Connected

/** What this computer has: connected apps, MCP servers and skills (the header's "Connected"). */
function InstalledView({ back }: { back: () => void }) {
  const store = useStore()
  const plugins = usePlugins(store)
  const [removing, setRemoving] = useState<{ id: string; name: string; isSkill: boolean } | null>(null)
  const [signingIn, setSigningIn] = useState<string | null>(null)
  const [viewingSkill, setViewingSkill] = useState<SkillRef | null>(null)
  const empty = plugins.installedConnectors.length === 0 && plugins.installedSkills.length === 0

  const signIn = async (id: string) => {
    setSigningIn(id)
    try {
      await plugins.signIn(id)
    } catch (e) {
      store.setError(errorText(e))
    }
    setSigningIn(null)
  }

  const remove = async (r: { id: string; isSkill: boolean }) => {
    try {
      if (r.isSkill) await plugins.removeSkill(r.id)
      else await plugins.removeConnector(r.id)
    } catch (e) {
      store.setError(errorText(e))
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--background)' }}>
      <ScreenHeader title="Connected" onBack={back} />
      <div className="market-scroll">
        <div className="market-page-content">
          {empty ? <EmptyState icon="shippingbox" title="Nothing connected" detail="Apps, MCP servers and skills you add show up here." /> : null}
          {plugins.installedConnectors.length ? (
            <MarketSection title="Connectors">
              <ItemGrid>
                {plugins.installedConnectors.map((c) => (
                  <ManagedRow
                    key={c.id}
                    title={c.name}
                    subtitle={needsSignIn(c) ? 'Sign in so bots can use it' : (c.command ?? c.url ?? c.description)}
                    icon={c.kind === 'composio' ? <AppLogo url={c.logo} name={c.name} size={36} /> : <ServiceLogo name={c.name} registryName={c.registryName} size={36} />}
                    accessory={signingIn === c.id ? <Spinner /> : needsSignIn(c) ? <Button onClick={() => void signIn(c.id)}>Sign in</Button> : null}
                    onRemove={() => setRemoving({ id: c.id, name: c.name, isSkill: false })}
                  />
                ))}
              </ItemGrid>
            </MarketSection>
          ) : null}
          {plugins.installedSkills.length ? (
            <MarketSection title="Skills">
              <ItemGrid>
                {plugins.installedSkills.map((s) => (
                  <ManagedRow
                    key={s.id}
                    title={s.name}
                    subtitle={s.description}
                    icon={<SkillGlyph size={36} />}
                    onOpen={() => setViewingSkill({ id: s.id, name: s.name, description: s.description })}
                    onRemove={() => setRemoving({ id: s.id, name: s.name, isSkill: true })}
                  />
                ))}
              </ItemGrid>
            </MarketSection>
          ) : null}
        </div>
      </div>
      <Dialog
        open={removing !== null}
        title={`Remove ${removing?.name ?? ''}?`}
        message={removing ? (removing.isSkill ? 'Bots stop using this skill.' : 'Bots lose this connector, and the keys saved for it are deleted.') : null}
        actions={removing ? [{ title: 'Remove', destructive: true, action: () => void remove(removing) }] : []}
        onClose={() => setRemoving(null)}
      />
      <FormSheet open={viewingSkill !== null} onClose={() => setViewingSkill(null)}>
        {viewingSkill ? <SkillSheet key={viewingSkill.name} skill={viewingSkill} /> : null}
      </FormSheet>
    </div>
  )
}
