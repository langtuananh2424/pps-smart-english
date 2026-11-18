import { useCallback, useEffect, useState } from 'react'
import { api, postJson } from './api'

export type PushState = 'unsupported' | 'denied' | 'off' | 'on'

export const isPushSupported = () =>
  typeof navigator !== 'undefined' && 'serviceWorker' in navigator && typeof window !== 'undefined' && 'PushManager' in window && 'Notification' in window

const registration = () => navigator.serviceWorker.register('/sw.js')

export async function getPushState(): Promise<PushState> {
  if (!isPushSupported()) return 'unsupported'
  if (Notification.permission === 'denied') return 'denied'
  const reg = await navigator.serviceWorker.getRegistration('/sw.js')
  return (await reg?.pushManager.getSubscription()) ? 'on' : 'off'
}

// base64url -> Uint8Array cho applicationServerKey.
const decodeKey = (b64: string) => {
  const raw = atob(b64.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(b64.length / 4) * 4, '='))
  return Uint8Array.from(raw, (c) => c.charCodeAt(0))
}

/** Chỉ gọi từ thao tác bấm của học viên: đây là chỗ duy nhất xin quyền (D-91). */
export async function enablePush(): Promise<PushState> {
  if (!isPushSupported()) throw new Error('Trình duyệt này chưa hỗ trợ thông báo đẩy.')
  const { publicKey } = await api<{ publicKey: string | null }>('/push/vapid-key')
  if (!publicKey) throw new Error('Máy chủ chưa bật thông báo đẩy.') // backend Noop: chưa cấu hình VAPID
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') return permission === 'denied' ? 'denied' : 'off'
  const reg = await registration()
  const sub = (await reg.pushManager.getSubscription()) ?? (await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: decodeKey(publicKey) }))
  const { endpoint, keys } = sub.toJSON()
  await postJson('/me/push-subscriptions', { endpoint, keys })
  return 'on'
}

export async function disablePush(): Promise<PushState> {
  const reg = await navigator.serviceWorker.getRegistration('/sw.js')
  const sub = await reg?.pushManager.getSubscription()
  if (sub) {
    await api('/me/push-subscriptions', { method: 'DELETE', body: JSON.stringify({ endpoint: sub.endpoint }) })
    await sub.unsubscribe()
  }
  return 'off'
}

// Cho màn Thông báo sắp tới (chờ Figma): trạng thái + hai thao tác.
export function usePush() {
  const [state, setState] = useState<PushState>('unsupported')
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    void getPushState().then(setState, () => setState('unsupported'))
  }, [])
  const run = useCallback(async (fn: () => Promise<PushState>) => {
    setError(null)
    try {
      setState(await fn())
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Có lỗi xảy ra, vui lòng thử lại.')
    }
  }, [])
  return { state, error, enable: () => run(enablePush), disable: () => run(disablePush) }
}
