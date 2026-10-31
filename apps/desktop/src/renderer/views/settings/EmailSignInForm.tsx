import { useState } from 'react'
import type { CSSProperties } from 'react'
import { Button, Spinner } from '../../components/Controls'
import { font } from '../../lib/fonts'
import { useAccount } from '../../store/account'

export function EmailSignInForm() {
  const account = useAccount()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [code, setCode] = useState('')
  const [mode, setMode] = useState<'signin' | 'signup'>('signin')
  if (account.isSignedIn) return null
  const inputStyle: CSSProperties = {
    padding: '10px 12px', borderRadius: 10, border: '1px solid var(--border)',
    background: 'var(--surface)', color: 'var(--text)', width: '100%', boxSizing: 'border-box',
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, padding: 14, borderRadius: 12, background: 'var(--surface)', border: '1px solid var(--border)' }}>
      <div style={{ ...font('headline'), color: 'var(--text)' }}>Sign in with email</div>
      <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Email" type="email" style={inputStyle} />
      <input value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Password" type="password" style={inputStyle} />
      {account.emailVerificationPending ? (
        <input value={code} onChange={(e) => setCode(e.target.value)} placeholder="Verification code" style={inputStyle} />
      ) : null}
      <Button onClick={() => {
        if (account.emailVerificationPending) void account.verifyEmail(code)
        else if (mode === 'signin') void account.signInWithEmail(email, password)
        else void account.signUpWithEmail(email, password)
      }} disabled={!account.isConfigured || account.isBusy || !email || !password} style={{ ...font('headline'), minHeight: 40, width: '100%' }}>
        {account.isBusy ? <Spinner size={16} /> : (account.emailVerificationPending ? 'Verify' : (mode === 'signin' ? 'Sign in' : 'Create account'))}
      </Button>
      {!account.emailVerificationPending ? (
        <button onClick={() => setMode(mode === 'signin' ? 'signup' : 'signin')} style={{ ...font('footnote'), color: 'var(--secondary)', background: 'none', border: 'none', cursor: 'pointer', textAlign: 'left', padding: 0 }}>
          {mode === 'signin' ? 'Need an account? Sign up' : 'Already have an account? Sign in'}
        </button>
      ) : (
        <button onClick={() => void account.cancelEmailAuth()} style={{ ...font('footnote'), color: 'var(--secondary)', background: 'none', border: 'none', cursor: 'pointer', textAlign: 'left', padding: 0 }}>
          Cancel
        </button>
      )}
      {account.errorMessage ? <div style={{ ...font('footnote'), color: 'var(--warning)' }}>{account.errorMessage}</div> : null}
    </div>
  )
}