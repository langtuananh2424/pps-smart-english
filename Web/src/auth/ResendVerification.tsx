import { useState } from 'react'
import { api, ApiError } from '../lib/api'
import { NETWORK_ERROR } from './formErrors'

const COOLDOWN_MS = 60_000

// Gửi lại email xác thực; khóa nút khi đang gửi và 60 giây sau khi gửi (backend cũng giới hạn theo giờ).
export function ResendVerification({ className = '' }: { className?: string }) {
  const [state, setState] = useState<'idle' | 'sending' | 'sent'>('idle')
  const [error, setError] = useState('')

  const send = async () => {
    setState('sending')
    setError('')
    try {
      await api('/auth/resend-verification', { method: 'POST' })
      setState('sent')
      setTimeout(() => setState('idle'), COOLDOWN_MS)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : NETWORK_ERROR)
      setState('idle')
    }
  }

  return (
    <>
      <button type="button" onClick={send} disabled={state !== 'idle'} className={`disabled:opacity-60 ${className}`}>
        {state === 'sending' ? 'Đang gửi...' : state === 'sent' ? 'Đã gửi email' : 'Gửi lại email'}
      </button>
      {error && <span role="alert" className="text-sm font-bold text-danger">{error}</span>}
    </>
  )
}
