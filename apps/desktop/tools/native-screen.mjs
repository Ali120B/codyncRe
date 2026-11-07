// Builds Codync Screen (Remote screen capture and input) into build/native/CodyncScreen.app for the
// Mac app to bundle. Xcode builds it unsigned by default; CODYNC_SIGN_IDENTITY (with
// CODYNC_DEVELOPMENT_TEAM) makes Xcode sign it with that stable identity instead, so macOS TCC
// grants survive rebuilds. Without it the helper is ad-hoc signed for electron-builder to replace.
// The cua-driver the Xcode target embeds keeps its own signature (never sign with --deep). Without
// it the app registers a launch agent pointing at a missing helper and Remote screen never starts.
import { execFileSync } from 'node:child_process'
import { cpSync, mkdirSync, rmSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

if (process.platform !== 'darwin') process.exit(0)
const env = process.argv[2] ?? 'main'
if (!['main', 'dev'].includes(env)) throw new Error(`unknown environment ${env}`)
const name = env === 'dev' ? 'CodyncDevScreen' : 'CodyncScreen'
const configuration = env === 'dev' ? 'DevRelease' : 'Release'
const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const repo = join(root, '../..')
const derived = join(repo, 'build/dd')
// CODYNC_SIGN_IDENTITY (plus CODYNC_DEVELOPMENT_TEAM) lets Xcode sign the helper with the same
// stable identity as the app; without it CI builds it unsigned for electron-builder to sign.
const identity = process.env.CODYNC_SIGN_IDENTITY
const team = process.env.CODYNC_DEVELOPMENT_TEAM
if (identity && !team) throw new Error('CODYNC_DEVELOPMENT_TEAM is required with CODYNC_SIGN_IDENTITY')
const signing = identity
  ? [
      `DEVELOPMENT_TEAM=${team}`, 'CODE_SIGN_STYLE=Automatic', 'CODE_SIGN_IDENTITY=Apple Development',
      'CODE_SIGNING_REQUIRED=YES', 'CODE_SIGNING_ALLOWED=YES', '-allowProvisioningUpdates',
    ]
  : ['CODE_SIGN_IDENTITY=-', 'CODE_SIGNING_REQUIRED=NO', 'CODE_SIGNING_ALLOWED=NO']
execFileSync('xcodebuild', [
  'build', '-project', join(repo, 'apps/Codync.xcodeproj'), '-scheme', env === 'dev' ? 'Screen Dev' : 'Screen', '-configuration', configuration,
  '-destination', 'generic/platform=macOS', '-derivedDataPath', derived, '-quiet',
  ...signing,
], { stdio: 'inherit' })
const out = join(root, `build/native/${name}.app`)
mkdirSync(dirname(out), { recursive: true })
rmSync(out, { recursive: true, force: true })
cpSync(join(derived, `Build/Products/${configuration}/${name}.app`), out, { recursive: true, verbatimSymlinks: true })
// Xcode already signed it with the stable identity when one was given. Unsigned CI builds get an
// ad-hoc signature so a local run works; electron-builder re-signs them for distribution. No
// --deep: the cua-driver keeps its maker's signature.
if (!identity) execFileSync('codesign', ['--force', '--sign', '-', out], { stdio: 'inherit' })
console.log(`screen: built (${identity ?? 'ad-hoc'})`)
