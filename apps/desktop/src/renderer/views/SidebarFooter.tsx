import { IconButton } from '../components/Controls'
import { localAccountName } from '../lib/local-account'
import { prefs, usePref } from '../lib/prefs'
import { NeedsYouInbox } from './NeedsYouInbox'
import { ProfileAvatar } from './PanelMenus'
import { UpdateBadge } from './UpdateButton'
import './sidebar-footer.css'

/** The profile box at the foot of the sidebar: who you are, and the plugins button beside it. */
export function SidebarFooter({ accountOpen, onAccount, onExtensions }: {
  accountOpen: boolean
  onAccount: () => void
  onExtensions: () => void
}) {
  const [saved] = usePref(prefs.profileName)
  const name = localAccountName(saved)
  return (
    <div className="sidebar-footer">
      <div className="sidebar-footer-fade" aria-hidden />
      <NeedsYouInbox />
      <div className="footer-row">
        <div className={`profile-box ${accountOpen ? 'active' : ''}`}>
          <button
            className="profile-button"
            aria-label={`Open account menu for ${name}`}
            aria-expanded={accountOpen}
            title={`${name} · account menu`}
            onClick={onAccount}
          >
            <ProfileAvatar />
            <span className="profile-name">{name}</span>
          </button>
          <IconButton title="Plugins" icon="puzzlepiece" size={34} onClick={onExtensions} />
        </div>
        <UpdateBadge size={38} />
      </div>
    </div>
  )
}
