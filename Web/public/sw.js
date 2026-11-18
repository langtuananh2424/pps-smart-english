// Service worker Web Push (FR-RET-03). Payload backend: {title, body, url}; url là đường dẫn tương đối.
self.addEventListener('push', (event) => {
  let p = {}
  try {
    p = event.data ? event.data.json() : {}
  } catch {
    // payload không phải JSON: dùng chữ mặc định
  }
  event.waitUntil(
    self.registration.showNotification(p.title || 'PPS Smart English', {
      body: p.body || '',
      data: { url: p.url || '/thong-bao' },
    }),
  )
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  // chỉ nhận đường dẫn cùng origin
  const raw = (event.notification.data && event.notification.data.url) || '/thong-bao'
  const url = new URL(raw, self.location.origin)
  const target = url.origin === self.location.origin ? url.href : self.location.origin + '/thong-bao'
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(async (list) => {
      const c = list.find((w) => 'focus' in w)
      if (c) {
        // navigate() lỗi với cửa sổ chưa do SW này kiểm soát (trang vừa đăng ký): mở cửa sổ mới
        const n = await c.navigate(target).catch(() => null)
        if (n) return n.focus()
      }
      return self.clients.openWindow(target)
    }),
  )
})
