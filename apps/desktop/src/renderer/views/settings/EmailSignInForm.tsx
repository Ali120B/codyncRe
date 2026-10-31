import { useState } from 'react'
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
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Email" type="email" style={{ padding: '8px 10px', borderRadius: 8, border: '1px solid var(--border)', background: 'var(--background)', color: 'var(--text)' }} />
      <input value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Password" type="password" style={{ padding: '8px 10px', borderRadius: 8, border: '1px solid var(--border)', background: 'var(--background)', color: 'var(--text)' }} />
      {account.emailVerificationPending ? (
        <input value={code} onChange={(e) => setCode(e.target.value)} placeholder="Verification code" style={{ padding: '8px 10px', borderRadius: 8, border: '1px solid var(--border)', background: 'var(--background)', color: 'var(--text)' }} />
      ) : null}
      <Button onClick={() => {
        if (account.emailVerificationPending) void account.verifyEmail(code)
        else if (mode === 'signin') void account.signInWithEmail(email, password)
        else void account.signUpWithEmail(email, password)
      }} disabled={!account.isConfigured || account.isBusy || !email || !password} style={{ ...font('headline'), minHeight: 40, width: '100%' }}>
        {account.isBusy ? <Spinner size={16} /> : (account.emailVerificationPending ? 'Verify' : (mode === 'signin' ? 'Sign in with email' : 'Create account'))}
      </Button>
      {!account.emailVerificationPending ? (
        <button onClick={() => setMode(mode === 'signin' ? 'signup' : 'signin')} style={{ ...font('footnote'), color: 'var(--secondary)', background: 'none', border: 'none', cursor: 'pointer', textAlign: 'left' }}>
          {mode === 'signin' ? 'Need an account? Sign up' : 'Already have an account? Sign in'}
        </button>
      ) : (
        <button onClick={() => void account.cancelEmailAuth()} style={{ ...font('footnote'), color: 'var(--secondary)', background: 'none', border: 'none', cursor: 'pointer', textAlign: 'left' }}>
          Cancel
        </button>
      )}
    </div>
  )
}