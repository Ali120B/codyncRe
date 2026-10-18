import { useEffect, useState, type ReactNode } from 'react'
import { Spinner } from '../components/Controls'
import { Icon } from '../components/Icon'
import type { MenuItem } from '../components/Overlay'
import { font } from '../lib/fonts'
import { accountInitial, localAccountName } from '../lib/local-account'
import { prefs, usePref } from '../lib/prefs'
import { account, useAccount } from '../store/account'
import googleLogo from '../assets/google.svg'

// The chat window's floating panels: the account menu (with its profile avatar) and the roster's
// right-click actions, both built on the same keyboard-driven rows.

export function ProfileAvatar() {
  const { user, isBusy } = useAccount()
  const [saved] = usePref(prefs.profileName)
  return (
    <span className="profile-avatar" aria-hidden style={{ position: 'relative', overflow: 'hidden' }}>
      {user?.avatarURL ? (
        <img src={user.avatarURL} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
      ) : (
        <span className="profile-initial">{accountInitial(localAccountName(saved))}</span>
      )}
      {isBusy ? (
        <span style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Spinner size={14} />
        </span>
      ) : null}
    </span>
  )
}

interface PanelItem {
  title: string
  icon: string
  detail?: string | null
  /** A bundled image in place of the symbol (the Google logo). */
  image?: string
  chevron?: boolean
  disabled?: boolean
  destructive?: boolean
  action: () => void
}

/** The account menu: a window-local floating surface (no system menu styling). */
export function AccountPanelLayer({ compact, approvals, onDismiss, onUsage, onSettings, onProfile }: {
  compact: boolean
  approvals: number
  onDismiss: () => void
  onUsage: () => void
  onSettings: () => void
  onProfile: () => void
}) {
  const [page, setPage] = useState<'main' | 'support'>('main')
  const { user, isBusy, errorMessage } = useAccount()
  const [saved] = usePref(prefs.profileName)
  const name = localAccountName(saved)
  const signedIn = user !== null
  const auth: PanelItem[] = signedIn
    ? [{ title: isBusy ? 'Please wait…' : 'Sign out', icon: 'rectangle.portrait.and.arrow.right', disabled: isBusy, action: () => { onDismiss(); void account.signOut() } }]
    : [
        { title: 'Continue with Apple', icon: 'apple.logo', disabled: isBusy, action: () => { onDismiss(); void account.signIn('apple') } },
        { title: 'Continue with Google', icon: 'person.crop.circle.badge.plus', image: googleLogo, disabled: isBusy, action: () => { onDismiss(); void account.signIn('google') } },
      ]
  const open = (url: string) => {
    onDismiss()
    window.codync.app.openExternal(url)
  }
  const items: PanelItem[] =
    page === 'support'
      ? [
          { title: 'Support', icon: 'chevron.left', action: () => setPage('main') },
          { title: 'Help & documentation', icon: 'book', chevron: true, action: () => open('https://github.com/Ali120B/codyncRe#readme') },
          { title: 'Report an issue', icon: 'bubble.left', chevron: true, action: () => open('https://github.com/Ali120B/codyncRe/issues') },
        ]
      : [
          { title: name, icon: 'person.crop.circle', detail: 'Edit', action: () => { onDismiss(); onProfile() } },
          { title: 'Usage', icon: 'gauge.with.dots.needle.33percent', chevron: true, action: onUsage },
          { title: 'Get Codync for mobile', icon: 'iphone', action: () => open('https://apps.apple.com/app/id6760984418') },
          { title: 'Support', icon: 'book.closed', chevron: true, action: () => setPage('support') },
          { title: 'Settings', icon: 'gearshape', detail: approvals > 0 ? String(approvals) : null, action: onSettings },
          ...auth,
        ]
  return (
    <div className="context-layer" onMouseDown={(e) => e.target === e.currentTarget && onDismiss()}>
      <div className="account-panel" style={{ width: Math.min(300, window.innerWidth - 32), left: 16, bottom: compact ? 68 : 76 }}>
        <PanelRows
          items={items}
          onDismiss={onDismiss}
          after={(i) =>
            page === 'main' && items[i]?.title === 'Settings' ? (
              <>
                <div className="panel-divider" />
                <div className="panel-identity">
                  <Icon name="person.crop.circle" size={15} />
                  <span style={{ ...font(12), flex: 1, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{user?.email ?? 'This computer'}</span>
                  {signedIn ? null : <span style={font(11)}>Not signed in</span>}
                </div>
              </>
            ) : page === 'support' && i === 0 ? (
              <div className="panel-divider" />
            ) : null
          }
        />
        {page === 'main' && errorMessage ? (
          <div style={{ ...font(12), color: 'var(--warning)', padding: '8px 10px' }}>{errorMessage}</div>
        ) : null}
      </div>
    </div>
  )
}

function PanelRows({ items, onDismiss, after, dismissOnActivate = false }: { items: PanelItem[]; onDismiss: () => void; after?: (i: number) => ReactNode; dismissOnActivate?: boolean }) {
  const [highlighted, setHighlighted] = useState(0)
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault()
        onDismiss()
      } else if (e.key === 'ArrowDown' || (e.key === 'Tab' && !e.shiftKey)) {
        e.preventDefault()
        setHighlighted((h) => (h + 1) % items.length)
      } else if (e.key === 'ArrowUp' || (e.key === 'Tab' && e.shiftKey)) {
        e.preventDefault()
        setHighlighted((h) => (h - 1 + items.length) % items.length)
      } else if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault()
        const item = items[highlighted]
        if (item && !item.disabled) {
          if (dismissOnActivate) onDismiss()
          item.action()
        }
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [items, highlighted, onDismiss, dismissOnActivate])
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      {items.map((item, i) => (
        <div key={item.title}>
          <button
            className={`panel-row ${highlighted === i ? 'highlight' : ''}`}
            style={{ color: item.destructive ? 'var(--danger)' : 'var(--text)' }}
            disabled={item.disabled}
            onMouseEnter={() => setHighlighted(i)}
            onClick={() => {
              if (dismissOnActivate) onDismiss()
              item.action()
            }}
          >
            <span style={{ width: 22, display: 'flex', justifyContent: 'center' }}>
              {item.image ? <img src={item.image} alt="" width={16} height={16} /> : <Icon name={item.icon} size={16} />}
            </span>
            <span style={{ ...font(14), flex: 1, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{item.title}</span>
            {item.detail ? <span style={{ ...font(13), color: 'var(--secondary)' }}>{item.detail}</span> : null}
            {item.chevron ? <Icon name="chevron.right" size={11} color="var(--secondary)" /> : null}
          </button>
          {after?.(i)}
        </div>
      ))}
    </div>
  )
}

/** The roster's right-click actions, shared by pointer and keyboard. */
export function DesktopActionMenu({ items, onDismiss, style }: { items: MenuItem[]; onDismiss: () => void; style: React.CSSProperties }) {
  const rows = items.map<PanelItem>((m) => ({ title: m.title, icon: m.icon ?? 'circle', destructive: m.destructive, action: m.action }))
  return (
    <div className="action-menu" style={style}>
      <PanelRows
        items={rows}
        onDismiss={onDismiss}
        dismissOnActivate
        after={(i) => (items[i + 1]?.divider ? <div className="panel-divider" /> : null)}
      />
    </div>
  )
}
