import { useEffect, useState } from 'react'
import { Dialog } from '../components/Overlay'
import { localAccountName } from '../lib/local-account'
import { prefs, usePref } from '../lib/prefs'

/** Names the local account: the name the sidebar, its avatar and the account menu show. */
export function ProfileDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [saved, setSaved] = usePref(prefs.profileName)
  const [draft, setDraft] = useState('')
  useEffect(() => {
    if (open) setDraft(localAccountName(saved))
  }, [open, saved])
  const save = () => setSaved(draft.trim())
  return (
    <Dialog
      open={open}
      title="Your name"
      message="This computer's own account — no sign-in needed."
      actions={[{ title: 'Save', action: save }]}
      onClose={onClose}
    >
      <input
        className="field-box"
        autoFocus
        value={draft}
        placeholder="Your name"
        aria-label="Your name"
        spellCheck={false}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={(e) => {
          if (e.key !== 'Enter') return
          save()
          onClose()
        }}
      />
    </Dialog>
  )
}
