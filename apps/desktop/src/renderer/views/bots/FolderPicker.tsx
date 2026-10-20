import { useEffect, useState } from 'react'
import type { DirListing } from '@shared/models'
import { CardSection, IconButton, SearchField, Spinner } from '../../components/Controls'
import { Icon } from '../../components/Icon'
import { ModalHeader } from '../../components/Overlay'
import { font } from '../../lib/fonts'
import { useStore } from '../../store/context'
import { errorText, lastPathComponent } from './drafts'
import './bots.css'

/** Browses folders on the host, drilling down in place. Must sit under a StoreContext. */
export function FolderPicker({ start, onPick }: { start: string | null; onPick: (path: string) => void }) {
  // The folders opened so far; the last one is shown.
  const [stack, setStack] = useState<(string | null)[]>([start])
  const [forward, setForward] = useState(true)
  const current = stack[stack.length - 1] ?? null
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--background)' }}>
      <div style={{ display: 'flex', alignItems: 'center' }}>
        {stack.length > 1 ? (
          <div className="fade-in" style={{ paddingLeft: 10, display: 'flex' }}>
            <IconButton
              title="Back"
              icon="chevron.left"
              onClick={() => {
                setForward(false)
                setStack((s) => s.slice(0, -1))
              }}
            />
          </div>
        ) : null}
        <div style={{ flex: 1, minWidth: 0 }}>
          <ModalHeader title={current ? lastPathComponent(current) : 'Folders'} />
        </div>
      </div>
      <div style={{ flex: 1, minHeight: 0, overflowY: 'auto' }}>
        <FolderLevel
          key={stack.length}
          path={current}
          forward={forward}
          onPick={onPick}
          onOpen={(path) => {
            setForward(true)
            setStack((s) => [...s, path])
          }}
        />
      </div>
    </div>
  )
}

/** One folder's subfolders. */
function FolderLevel({ path, forward, onPick, onOpen }: { path: string | null; forward: boolean; onPick: (path: string) => void; onOpen: (path: string) => void }) {
  const store = useStore()
  const [listing, setListing] = useState<DirListing | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState('')

  useEffect(() => {
    let live = true
    store
      .listDirs(path)
      .then((l) => live && setListing(l))
      .catch((e: unknown) => live && setError(errorText(e)))
    return () => {
      live = false
    }
  }, [store, path])

  const dirs = listing?.dirs.filter((d) => !filter || d.name.toLowerCase().includes(filter.toLowerCase())) ?? []

  return (
    <div className={`bot-form level-slide ${forward ? 'from-trailing' : 'from-leading'}`}>
      <div className="bot-form-content" style={{ gap: 14 }}>
        <SearchField value={filter} onChange={setFilter} />
        {listing ? (
          <>
            <CardSection footer={listing.path}>
              <button className="press" style={{ display: 'flex', alignItems: 'center', gap: 8, ...font('headline'), color: 'var(--accent)' }} onClick={() => onPick(listing.path)}>
                <Icon name="checkmark.circle.fill" size={13} />
                <span style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{`Use “${lastPathComponent(listing.path)}”`}</span>
              </button>
            </CardSection>
            {dirs.length ? (
              <CardSection>
                {dirs.map((dir) => (
                  <button key={dir.path} className="press" style={{ display: 'flex', alignItems: 'center', gap: 10 }} onClick={() => onOpen(dir.path)}>
                    <span style={{ width: 20, display: 'flex', justifyContent: 'center' }}>
                      <Icon name={dir.isGit ? 'arrow.triangle.branch' : 'folder'} size={12} color={dir.isGit ? 'var(--accent)' : 'var(--secondary)'} />
                    </span>
                    <span style={{ flex: 1, minWidth: 0, color: 'var(--text)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{dir.name}</span>
                    <Icon name="chevron.right" size={10} weight="semibold" color="var(--tertiary)" />
                  </button>
                ))}
              </CardSection>
            ) : null}
          </>
        ) : error ? (
          <span style={{ color: 'var(--danger)' }}>{error}</span>
        ) : (
          <div style={{ display: 'flex', justifyContent: 'center' }}>
            <Spinner size={20} />
          </div>
        )}
      </div>
    </div>
  )
}
