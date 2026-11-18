import { setAccessToken } from './api'
import { disablePush, enablePush, getPushState } from './push'

const json = (status: number, body?: unknown) => new Response(body === undefined ? null : JSON.stringify(body), { status })

function setup(opts: { permission?: NotificationPermission; subscribed?: boolean; supported?: boolean } = {}) {
  const sub = { endpoint: 'https://push/e1', toJSON: () => ({ endpoint: 'https://push/e1', keys: { p256dh: 'p', auth: 'a' } }), unsubscribe: vi.fn().mockResolvedValue(true) }
  let current: typeof sub | null = opts.subscribed ? sub : null
  const pushManager = {
    getSubscription: vi.fn(async () => current),
    subscribe: vi.fn(async () => (current = sub)),
  }
  const reg = { pushManager }
  if (opts.supported !== false) {
    vi.stubGlobal('navigator', { serviceWorker: { register: vi.fn(async () => reg), getRegistration: vi.fn(async () => reg) } })
    vi.stubGlobal('PushManager', class {})
    vi.stubGlobal('Notification', { permission: opts.permission ?? 'default', requestPermission: vi.fn(async () => opts.permission ?? 'granted') })
  } else {
    vi.stubGlobal('navigator', {})
  }
  return { sub, pushManager }
}

beforeEach(() => setAccessToken('t'))
afterEach(() => vi.unstubAllGlobals())

test('getPushState: unsupported, denied, off, on', async () => {
  setup({ supported: false })
  expect(await getPushState()).toBe('unsupported')
  setup({ permission: 'denied' })
  expect(await getPushState()).toBe('denied')
  setup()
  expect(await getPushState()).toBe('off')
  setup({ subscribed: true, permission: 'granted' })
  expect(await getPushState()).toBe('on')
})

test('enablePush: lấy khóa, xin quyền, đăng ký rồi gửi lên backend', async () => {
  const { pushManager } = setup()
  const f = vi.fn(async (url: string) => (url === '/api/push/vapid-key' ? json(200, { publicKey: 'AQAB' }) : json(201, { id: 1 })))
  vi.stubGlobal('fetch', f)
  expect(await enablePush()).toBe('on')
  expect(pushManager.subscribe).toHaveBeenCalledWith(expect.objectContaining({ userVisibleOnly: true }))
  const post = f.mock.calls.find((c) => c[0] === '/api/me/push-subscriptions') as unknown as [string, RequestInit]
  expect(JSON.parse(String(post[1].body))).toEqual({ endpoint: 'https://push/e1', keys: { p256dh: 'p', auth: 'a' } })
})

test('enablePush: backend chưa có VAPID thì báo lỗi và không xin quyền', async () => {
  setup()
  vi.stubGlobal('fetch', vi.fn(async () => json(200, { publicKey: null })))
  await expect(enablePush()).rejects.toThrow('chưa bật')
  expect(Notification.requestPermission).not.toHaveBeenCalled()
})

test('enablePush: bị từ chối quyền thì không đăng ký', async () => {
  const { pushManager } = setup({ permission: 'denied' })
  vi.stubGlobal('fetch', vi.fn(async () => json(200, { publicKey: 'AQAB' })))
  expect(await enablePush()).toBe('denied')
  expect(pushManager.subscribe).not.toHaveBeenCalled()
})

test('disablePush: DELETE rồi hủy đăng ký', async () => {
  const { sub } = setup({ subscribed: true, permission: 'granted' })
  const f = vi.fn(async () => json(204))
  vi.stubGlobal('fetch', f)
  expect(await disablePush()).toBe('off')
  expect(f).toHaveBeenCalledWith('/api/me/push-subscriptions', expect.objectContaining({ method: 'DELETE' }))
  expect(sub.unsubscribe).toHaveBeenCalled()
})
