import { useEffect } from 'react'
import type { MessageFocus } from '../store/app-model'

const FLASH_MS = 1900
const ATTEMPT_MS = 100
const MAX_ATTEMPTS = 50

/**
 * Brings a search hit's row into view: retries until the row is rendered (its chat and
 * thread load first), scrolls it to the middle, flashes it, then hands the focus back.
 * The flash outlives the focus being consumed, so it is removed by its own timer.
 */
export function useFocusMessage(focus: MessageFocus | null, consume: (nonce: number) => void) {
  const nonce = focus?.nonce
  const entryId = focus?.entryId
  useEffect(() => {
    if (nonce === undefined || !entryId) return
    let cancelled = false
    let landed = false
    let attempts = 0
    let retry: ReturnType<typeof setTimeout> | undefined
    let flash: ReturnType<typeof setTimeout> | undefined
    let row: HTMLElement | null = null
    const attempt = () => {
      if (cancelled) return
      row = document.querySelector<HTMLElement>(`[data-mid="${CSS.escape(entryId)}"]`)
      if (!row) {
        if (attempts++ < MAX_ATTEMPTS) retry = setTimeout(attempt, ATTEMPT_MS)
        return
      }
      row.scrollIntoView({ block: 'center', behavior: 'smooth' })
      row.classList.add('search-flash')
      flash = setTimeout(() => row?.classList.remove('search-flash'), FLASH_MS)
      landed = true
      consume(nonce)
    }
    attempt()
    return () => {
      cancelled = true
      if (retry) clearTimeout(retry)
      // A teardown before the row was found leaves nothing behind; a landed flash keeps running
      // while the focus itself is cleared by `consume`.
      if (!landed) {
        if (flash) clearTimeout(flash)
        row?.classList.remove('search-flash')
      }
    }
    // `consume` is a stable method on the app model; the nonce identifies the one landing.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nonce, entryId])
}
