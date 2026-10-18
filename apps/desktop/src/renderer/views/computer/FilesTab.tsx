import { useEffect, useState } from 'react'
import type { Bot } from '@shared/models'
import { Spinner } from '../../components/Controls'
import { Icon } from '../../components/Icon'
import { Sheet } from '../../components/Overlay'
import { font } from '../../lib/fonts'
import { fromBase64 } from '../../client/host-client'
import type { BotStore } from '../../store/bot-store'

interface Listing {
  path: string
  name: string
  parent: string | null
  dirs: { name: string; path: string }[]
  files: { name: string; path: string; size: number; modified: number | null }[]
}

interface Preview {
  name: string
  path: string
  text: string | null
  size: number
  truncated: boolean
}

const message = (e: unknown) => (e instanceof Error ? e.message : String(e))

const size = (bytes: number) =>
  bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : bytes >= 1024 ? `${Math.round(bytes / 1024)} KB` : `${bytes} B`

const when = (at: number | null) => (at ? new Date(at).toLocaleDateString(undefined, { month: 'short', day: 'numeric' }) : '')

/** The bot's working folder: browse it, preview a file, copy its path. */
export function FilesTab({ bot, store }: { bot: Bot; store: BotStore }) {
  const [listing, setListing] = useState<Listing | null>(null)
  const [path, setPath] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [preview, setPreview] = useState<Preview | null>(null)
  const [copied, setCopied] = useState<string | null>(null)

  useEffect(() => {
    let alive = true
    setLoading(true)
    void (async () => {
      try {
        const next = await (await store.ready()).call<Listing>('workspaceFiles', { botId: bot.id, path }, 60_000)
        if (!alive) return
        setListing(next)
        setError(null)
      } catch (e) {
        if (alive) setError(message(e))
      } finally {
        if (alive) setLoading(false)
      }
    })()
    return () => {
      alive = false
    }
  }, [bot.id, store, path])

  const openFile = async (file: { name: string; path: string; size: number }) => {
    try {
      const res = await (await store.ready()).call<{ data: string; size: number }>(
        'readWorkspaceFile',
        { botId: bot.id, path: file.path, offset: 0 },
        60_000,
      )
      const bytes = fromBase64(res.data)
      const binary = bytes.subarray(0, 8192).includes(0)
      setPreview({
        name: file.name,
        path: file.path,
        text: binary ? null : new TextDecoder().decode(bytes),
        size: file.size,
        truncated: res.size > bytes.length || bytes.length < file.size,
      })
    } catch (e) {
      setError(message(e))
    }
  }

  const copyPath = async (p: string) => {
    window.codync.app.copy(p)
    setCopied(p)
    setTimeout(() => setCopied((c) => (c === p ? null : c)), 1500)
  }

  if (error && !listing) {
    return (
      <div className="computer-pad">
        <p className="computer-hint" style={font('body')}>{error}</p>
      </div>
    )
  }

  const crumbs = listing ? listing.path.split('/').filter(Boolean) : []
  return (
    <div className="files-tab">
      <div className="files-header">
        <button
          className="files-up"
          disabled={!listing || listing.path === ''}
          title="Up one folder"
          aria-label="Up one folder"
          onClick={() => listing && setPath(listing.parent ?? '')}
        >
          <Icon name="chevron.up" size={13} weight="semibold" />
        </button>
        <div className="files-crumbs" style={font(12.5)}>
          <button className="files-crumb" onClick={() => setPath('')}>
            {bot.managedWorkspace ? 'Personal workspace' : listing?.name || 'Workspace'}
          </button>
          {crumbs.map((part, i) => (
            <span key={i} style={{ display: 'flex', alignItems: 'center', gap: 4, minWidth: 0 }}>
              <Icon name="chevron.right" size={9} weight="semibold" color="var(--tertiary)" />
              <button className="files-crumb" onClick={() => setPath(crumbs.slice(0, i + 1).join('/'))}>
                {part}
              </button>
            </span>
          ))}
        </div>
        {loading ? <Spinner size={12} /> : null}
      </div>
      <div className="files-list">
        {(listing?.dirs ?? []).map((dir) => (
          <button key={dir.path} className="files-row" onClick={() => setPath(dir.path)}>
            <Icon name="folder" size={15} color="var(--secondary)" />
            <span className="files-name" style={font(13)}>{dir.name}</span>
          </button>
        ))}
        {(listing?.files ?? []).map((file) => (
          <button
            key={file.path}
            className="files-row"
            onClick={() => void openFile(file)}
            onContextMenu={(e) => {
              e.preventDefault()
              void copyPath(file.path)
            }}
            title="Open a preview · right-click copies the path"
          >
            <Icon name="doc.text" size={15} color="var(--tertiary)" />
            <span className="files-name" style={font(13)}>{file.name}</span>
            {copied === file.path ? <span style={{ ...font('caption'), color: 'var(--secondary)' }}>copied</span> : null}
            <span className="files-size" style={font('caption')}>{size(file.size)}</span>
            <span className="files-when" style={font('caption')}>{when(file.modified)}</span>
          </button>
        ))}
        {listing && !listing.dirs.length && !listing.files.length && !loading ? (
          <div className="computer-hint" style={{ ...font('footnote'), padding: '14px 4px' }}>This folder is empty.</div>
        ) : null}
      </div>
      {error && listing ? <div className="browser-error" style={font('footnote')}>{error}</div> : null}
      <Sheet open={preview !== null} onClose={() => setPreview(null)} width={620} height={Math.min(680, window.innerHeight - 80)}>
        {preview ? (
          <div className="file-preview">
            <div className="file-preview-head">
              <Icon name="doc.text" size={14} color="var(--secondary)" />
              <span style={{ ...font(13, 'semibold') }}>{preview.name}</span>
              <span style={{ ...font('caption'), color: 'var(--tertiary)' }}>{size(preview.size)}</span>
              <span style={{ flex: 1 }} />
              <button className="browser-restart" onClick={() => void copyPath(preview.path)}>
                {copied === preview.path ? 'Copied' : 'Copy path'}
              </button>
            </div>
            <div className="file-preview-body">
              {preview.text !== null ? (
                <>
                  <pre style={font('caption')}>{preview.text}</pre>
                  {preview.truncated ? <div style={{ ...font('caption'), color: 'var(--tertiary)', paddingTop: 8 }}>Showing the start of the file.</div> : null}
                </>
              ) : (
                <div className="computer-hint" style={font('body')}>This looks like a binary file — no preview.</div>
              )}
            </div>
          </div>
        ) : null}
      </Sheet>
    </div>
  )
}
