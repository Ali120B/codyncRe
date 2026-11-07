import { spawnSync } from 'node:child_process'
import { existsSync, mkdirSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { dirname, join } from 'node:path'
import { app, ipcMain } from 'electron'
import { prefs } from './host-controller'
import { identity } from './environment'
import { shouldSyncScreenAgent } from '../shared/computer-access'

// Codync Screen (capture + input for Remote screen): on macOS a launchd agent inside the app
// bundle (apps/screen-macos, registered through SMAppService), which owns the Screen Recording
// and Accessibility grants. Linux starts its helper from the host, so nothing happens here.
//
// An ad-hoc signature has no Team ID, and macOS refuses to spawn an SMAppService login item from
// one (launchd exits 78/EX_CONFIG). Those builds run the same helper through a user LaunchAgent,
// which launchd starts without a signing identity.

const serviceName = `${identity.screenLabel}.plist`
const isMac = process.platform === 'darwin'

function helperPath() {
  return join(process.resourcesPath, '..', `Library/LoginItems/${identity.screenName}.app/Contents/MacOS/${identity.screenName}`)
}

/** Identifies the helper binary inside this app: its path and modification date. */
function helperStamp() {
  const path = helperPath()
  try {
    return `${path}@${statSync(path).mtimeMs}`
  } catch {
    return `${path}@0`
  }
}

let adHocSigned: boolean | null = null
/** Ad-hoc builds cannot register an SMAppService login item, so they use a plain LaunchAgent. */
function usesLaunchAgent() {
  if (adHocSigned !== null) return adHocSigned
  const result = spawnSync('/usr/bin/codesign', ['-dv', app.getPath('exe')], { encoding: 'utf8' })
  const output = `${result.stdout ?? ''}${result.stderr ?? ''}`
  // Only a build with a real Apple Team ID can register an SMAppService login item.
  const team = /TeamIdentifier=([^\n]+)/.exec(output)?.[1]?.trim()
  adHocSigned = !team || team === 'not set'
  return adHocSigned
}

const fallbackPlist = () => join(homedir(), 'Library/LaunchAgents', serviceName)
const fallbackDomain = () => `gui/${process.getuid?.() ?? 0}`

function fallbackStatus(): string {
  if (!existsSync(fallbackPlist())) return 'not-registered'
  const result = spawnSync('/bin/launchctl', ['print', `${fallbackDomain()}/${identity.screenLabel}`])
  return result.status === 0 ? 'enabled' : 'not-registered'
}

function stopFallback() {
  spawnSync('/bin/launchctl', ['bootout', `${fallbackDomain()}/${identity.screenLabel}`])
  // Make sure no stale helper process survives a broken job.
  spawnSync('/usr/bin/pkill', ['-f', `${identity.screenName}.app/Contents/MacOS/${identity.screenName}`])
  spawnSync('/bin/sleep', ['0.5'])
  rmSync(fallbackPlist(), { force: true })
}

function startFallback() {
  const escape = (value: string) => value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  const plist = fallbackPlist()
  mkdirSync(dirname(plist), { recursive: true })
  writeFileSync(plist, `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>${escape(identity.screenLabel)}</string>
  <key>ProgramArguments</key>
  <array><string>${escape(helperPath())}</string></array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
  <key>ProcessType</key><string>Interactive</string>
  <key>LimitLoadToSessionType</key><string>Aqua</string>
  <key>AssociatedBundleIdentifiers</key><array><string>${escape(identity.appId)}</string></array>
</dict>
</plist>
`)
  let result = spawnSync('/bin/launchctl', ['bootstrap', fallbackDomain(), plist], { encoding: 'utf8' })
  if (result.status !== 0) {
    // launchd sometimes reports a transient I/O error on the first try (stale job state).
    spawnSync('/bin/sleep', ['1'])
    spawnSync('/bin/launchctl', ['bootout', `${fallbackDomain()}/${identity.screenLabel}`])
    spawnSync('/bin/sleep', ['0.5'])
    result = spawnSync('/bin/launchctl', ['bootstrap', fallbackDomain(), plist], { encoding: 'utf8' })
  }
  if (result.status !== 0) {
    const detail = (result.stderr || result.stdout || '').trim() || `launchctl exited ${result.status}`
    throw new Error(`Could not start Codync Screen: ${detail}`)
  }
}

function status(): string {
  if (!isMac || !app.isPackaged) return 'not-registered'
  if (usesLaunchAgent()) return fallbackStatus()
  return app.getLoginItemSettings({ type: 'agentService', serviceName }).status ?? 'not-registered'
}

/** Registers (or removes) the agent; returns whether macOS wants the user to allow it. */
export function setScreenAgent(on: boolean): { needsApproval: boolean } {
  if (!isMac || !app.isPackaged) return { needsApproval: false }
  if (usesLaunchAgent()) {
    stopFallback()
    if (on) {
      startFallback()
      // launchd needs a moment to register the job before status() reflects it.
      spawnSync('/bin/sleep', ['1'])
    }
  } else {
    // A signed build must not keep the ad-hoc fallback around: both jobs use the same label.
    if (existsSync(fallbackPlist())) stopFallback()
    // Registering an enabled agent is a no-op, so a helper launchd gave up on would never start again.
    if (on && status() === 'enabled') app.setLoginItemSettings({ type: 'agentService', serviceName, openAtLogin: false })
    app.setLoginItemSettings({ type: 'agentService', serviceName, openAtLogin: on })
  }
  prefs.set('screenHelperStamp', on ? helperStamp() : null)
  prefs.set('screenHelperResumeAfterUpdate', false)
  return { needsApproval: status() === 'requires-approval' }
}

/**
 * Keeps Codync Screen registered while the host has Remote screen on, and registers it again
 * when this app ships a different helper binary (launchd keeps running the old one).
 */
export function syncScreenAgent(enabled: boolean): { needsApproval: boolean } {
  if (!isMac || !app.isPackaged || !enabled) return { needsApproval: false }
  // A signed build takes over from the ad-hoc LaunchAgent: both use the same job label, so the
  // old helper must stop before SMAppService can register the new one.
  if (!usesLaunchAgent() && existsSync(fallbackPlist())) return setScreenAgent(true)
  // A first registration belongs to explicit setup, never application startup — except for the
  // LaunchAgent fallback, which must be (re)created on every launch because launchd drops it.
  const resume = prefs.get('screenHelperResumeAfterUpdate') === true
  const autoRegister = usesLaunchAgent() && enabled && status() !== 'enabled'
  if (!autoRegister && !shouldSyncScreenAgent(enabled, status(), resume)) {
    return { needsApproval: status() === 'requires-approval' }
  }
  const stale = prefs.get('screenHelperStamp') !== helperStamp()
  if (!stale && status() === 'enabled') return { needsApproval: false }
  return setScreenAgent(true)
}

/** Before the app is replaced: the old helper stops along with its job. */
export function unregisterScreenAgent() {
  if (!isMac || !app.isPackaged || status() !== 'enabled') return
  prefs.set('screenHelperResumeAfterUpdate', true)
  if (usesLaunchAgent()) stopFallback()
  else app.setLoginItemSettings({ type: 'agentService', serviceName, openAtLogin: false })
  prefs.set('screenHelperStamp', null)
}

export function registerScreenIPC() {
  ipcMain.handle('screen:state', () => ({
    available: !isMac || (app.isPackaged && existsSync(helperPath())),
    needsApproval: status() === 'requires-approval',
    registered: status() === 'enabled',
  }))
  ipcMain.handle('screen:setAgent', (_e, on: boolean) => setScreenAgent(on))
  ipcMain.handle('screen:syncAgent', (_e, enabled: boolean) => syncScreenAgent(enabled))
}
