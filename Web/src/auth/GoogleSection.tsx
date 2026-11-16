import { useEffect, useRef } from 'react'
import { ApiError } from '../lib/api'
import { useAuth } from './AuthContext'
import { NETWORK_ERROR } from './formErrors'

// Google Identity Services, nạp động; không thêm thư viện npm.
interface Gsi {
  accounts: {
    id: {
      initialize: (c: { client_id: string; callback: (r: { credential: string }) => void }) => void
      renderButton: (el: HTMLElement, o: Record<string, unknown>) => void
    }
  }
}
declare global {
  interface Window {
    google?: Gsi
  }
}

let gsiLoading: Promise<void> | null = null
function loadGsi(): Promise<void> {
  if (window.google) return Promise.resolve()
  gsiLoading ??= new Promise<void>((resolve, reject) => {
    const s = document.createElement('script')
    s.src = 'https://accounts.google.com/gsi/client'
    s.async = true
    s.onload = () => resolve()
    s.onerror = () => {
      gsiLoading = null // cho phép thử lại lần mở sau
      s.remove()
      reject(new Error('gsi'))
    }
    document.head.append(s)
  })
  return gsiLoading
}

// Dòng "hoặc" + nút Google. Không có client id thì không hiện gì.
export function GoogleSection({ onError }: { onError: (msg: string) => void }) {
  const clientId: string = import.meta.env.VITE_GOOGLE_CLIENT_ID ?? ''
  const { loginWithGoogle } = useAuth()
  const host = useRef<HTMLDivElement>(null)
  // Callback luôn gọi bản mới nhất, không phải khởi tạo lại GIS mỗi lần render.
  const handler = useRef<(idToken: string) => void>(() => {})
  handler.current = async (idToken) => {
    onError('')
    try {
      await loginWithGoogle(idToken)
    } catch (e) {
      onError(e instanceof ApiError ? e.message : NETWORK_ERROR)
    }
  }
  const errorRef = useRef(onError)
  errorRef.current = onError

  useEffect(() => {
    if (!clientId) return
    let alive = true
    loadGsi()
      .then(() => {
        if (!alive || !host.current || !window.google) return
        const { id } = window.google.accounts
        id.initialize({ client_id: clientId, callback: (r) => handler.current(r.credential) })
        // Google giới hạn nút tối đa 400px và cao 40px (size large).
        id.renderButton(host.current, {
          type: 'standard',
          theme: 'outline',
          size: 'large',
          text: 'continue_with',
          shape: 'rectangular',
          locale: 'vi',
          width: Math.min(400, Math.max(200, host.current.clientWidth)),
        })
      })
      .catch(() => alive && errorRef.current('Không tải được đăng nhập Google, vui lòng thử lại sau.'))
    return () => {
      alive = false
    }
  }, [clientId])

  if (!clientId) return null
  return (
    <>
      <div className="flex items-center gap-3 text-sm font-bold text-ink-muted" aria-hidden="true">
        <span className="h-0.5 flex-1 bg-line" />
        hoặc
        <span className="h-0.5 flex-1 bg-line" />
      </div>
      <div ref={host} data-testid="google-button" className="flex min-h-14 items-center justify-center" />
    </>
  )
}
