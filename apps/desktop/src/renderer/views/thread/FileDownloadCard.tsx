import type { Entry, SharedFile } from '@shared/models'
import { isImageName } from '@shared/models'
import { useEffect, useState } from 'react'
import { useStore } from '../../store/context'
import { Icon } from '../../components/Icon'
import { IconButtonBase, ModalHeader, Sheet } from '../../components/Overlay'
import { font } from '../../lib/fonts'
import { decodeFileChunk } from '../../../shared/files.ts'
import { attachmentSymbol, byteCount } from './Attachments'

/** A bot-sent image is fetched once and shown inline; the card below it still saves the original. */
function useFilePreview(entry: Entry, file: SharedFile) {
  const store = useStore()
  const [url, setUrl] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)
  const client = store.client
  useEffect(() => {
    if (!isImageName(file.name) || !client || file.size > 25_000_000) return
    let cancelled = false
    let made: string | null = null
    setUrl(null)
    setFailed(false)
    void (async () => {
      try {
        const parts: Uint8Array[] = []
        let offset = 0
        do {
          const chunk = await client.readFile(entry.id, file.id, offset)
          const bytes = decodeFileChunk(chunk, file.size, offset)
          if (!bytes.length) break
          parts.push(bytes)
          offset += bytes.length
        } while (offset < file.size)
        if (cancelled) return
        made = URL.createObjectURL(new Blob(parts as unknown as BlobPart[]))
        setUrl(made)
      } catch {
        if (!cancelled) setFailed(true)
      }
    })()
    return () => {
      cancelled = true
      if (made) URL.revokeObjectURL(made)
    }
  }, [client, entry.id, file.id, file.name, file.size])
  return { url, failed: failed || file.size > 25_000_000 }
}

export function FileDownloadCard({ entry, file }: { entry: Entry; file: SharedFile }) {
  const store = useStore()
  const state = store.fileDownloads.states.get(file.id)
  const busy = state?.kind === 'downloading'
  const action = busy ? 'Cancel download' : state?.kind === 'failed' ? 'Retry download' : state?.kind === 'saved' ? 'Save again' : 'Download'
  const detail = busy ? `${byteCount(state.received)} of ${byteCount(file.size)}` : state?.kind === 'failed' ? state.message : state?.kind === 'saved' ? `${byteCount(file.size)} · Saved` : byteCount(file.size)
  const image = isImageName(file.name)
  const preview = useFilePreview(entry, file)
  const [expanded, setExpanded] = useState(false)
  return (
    <div style={{ display: 'flex', flexDirection: 'column', background: 'var(--bubble-agent)', borderRadius: 16, overflow: 'hidden', maxWidth: 380 }}>
      {image ? (
        <div
          onClick={() => preview.url && setExpanded(true)}
          style={{ width: '100%', maxWidth: 320, minHeight: 120, display: 'grid', placeItems: 'center', background: 'var(--bubble-user)', cursor: preview.url ? 'zoom-in' : 'default' }}
        >
          {preview.url ? (
            <img src={preview.url} alt={file.name} style={{ display: 'block', width: '100%', maxHeight: 300, objectFit: 'cover' }} />
          ) : (
            <Icon name={preview.failed ? 'exclamationmark.triangle' : 'photo'} size={20} color="var(--tertiary)" />
          )}
        </div>
      ) : null}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 14 }}>
        <Icon name={attachmentSymbol(file.name)} size={24} color="var(--secondary)" />
        <div style={{ minWidth: 0, flex: 1 }}>
          <div style={{ ...font('subheadline', 'semibold'), whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }} title={file.name}>{file.name}</div>
          <div role={state?.kind === 'failed' ? 'alert' : 'status'} style={{ ...font('caption2'), color: state?.kind === 'failed' ? 'var(--danger)' : 'var(--secondary)', overflowWrap: 'anywhere' }}>{detail}</div>
        </div>
        <IconButtonBase title={action} icon={busy ? 'xmark' : 'arrow.down.to.line'} size={36} onClick={() => busy ? store.fileDownloads.cancel(file.id) : void store.fileDownloads.save(store.client, entry.id, file)} />
      </div>
    <Sheet open={expanded} onClose={() => setExpanded(false)} width={720} height={560}>
      {expanded && preview.url ? (
        <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: 'var(--background)' }}>
          <ModalHeader title={file.name} onClose={() => setExpanded(false)} />
          <div style={{ flex: 1, minHeight: 0, padding: 12, display: 'grid', placeItems: 'center' }}>
            <img src={preview.url} alt={file.name} style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }} />
          </div>
        </div>
      ) : null}
    </Sheet>
    </div>
  )
}
