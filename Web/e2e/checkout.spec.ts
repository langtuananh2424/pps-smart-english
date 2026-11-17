import { expect, test, type Page } from '@playwright/test'

// Đặt CHECKOUT_SHOTS=<thư mục> để chụp ảnh đối chiếu Figma.
const shots = process.env.CHECKOUT_SHOTS
const viewports = [
  { name: 'desktop', width: 1280, height: 800 },
  { name: 'mobile', width: 390, height: 844 },
]

const ok = (body: unknown, status = 200) => ({ status, contentType: 'application/json', body: JSON.stringify(body) })
const me = { id: 1, email: 'minh.anh@example.com', emailVerified: true, role: 'STUDENT', displayName: 'Minh Anh', selfLevel: null }
const course = { id: 7, slug: 'ielts-inter', title: 'IELTS Intermediate' }
const detail = {
  ...course,
  products: [
    { id: 12, name: 'Gói 12 tháng', priceVnd: 1_790_000, accessDays: 365 },
    { id: 6, name: 'Gói 6 tháng', priceVnd: 990_000, accessDays: 180 },
  ],
}
const mins = (m: number) => new Date(Date.now() + m * 60_000).toISOString()

interface Order {
  code: string
  status: string
  totalVnd: number
  expiresAt: string
  createdAt: string
  items: { productId: number; name: string; priceVnd: number; course: typeof course }[]
}
const order = (code: string, status: string, expiresInMin: number): Order => ({
  code,
  status,
  totalVnd: 990_000,
  expiresAt: mins(expiresInMin),
  createdAt: mins(-5),
  items: [{ productId: 6, name: 'Gói 6 tháng', priceVnd: 990_000, course }],
})

// Giả backend có trạng thái: đơn tạo ra là PENDING, "dev pay" chuyển sang PAID.
async function mockShop(page: Page, seed: Order[] = []) {
  const orders = new Map(seed.map((o) => [o.code, o]))
  const created: { body: unknown; key: string | undefined }[] = []
  const view = (o: Order) =>
    o.status === 'PENDING'
      ? { ...o, payment: { qrContent: `FAKEQR|${o.code}|${o.totalVnd}`, expiresAt: o.expiresAt, simulatable: true }, transferContent: `Thanh toan ${o.code}` }
      : o
  await page.route('**/api/**', async (route) => {
    const req = route.request()
    const { pathname } = new URL(req.url())
    const m = req.method()
    if (pathname === '/api/auth/refresh') return route.fulfill(ok({ accessToken: 't', expiresIn: 900, user: { id: 1, displayName: 'Minh Anh', role: 'STUDENT' } }))
    if (pathname === '/api/me') return route.fulfill(ok(me))
    if (pathname === '/api/me/courses') return route.fulfill(ok([]))
    if (pathname === `/api/catalog/courses/${course.slug}`) return route.fulfill(ok(detail))
    if (pathname === '/api/me/orders') return route.fulfill(ok({ data: [...orders.values()].reverse().map((o) => ({ ...o, paidAt: null })), nextCursor: null }))
    if (pathname === '/api/orders' && m === 'POST') {
      created.push({ body: req.postDataJSON(), key: req.headers()['idempotency-key'] })
      const o = order(`ORD-NEW00${created.length}`, 'PENDING', 30)
      orders.set(o.code, o)
      return route.fulfill(ok({ code: o.code, status: o.status, totalVnd: o.totalVnd, payment: view(o).payment }, 201))
    }
    const pay = pathname.match(/^\/api\/dev\/payments\/(.+)\/pay$/)
    if (pay) {
      const o = orders.get(pay[1])!
      o.status = 'PAID'
      return route.fulfill(ok(view(o)))
    }
    const one = pathname.match(/^\/api\/orders\/(.+)$/)
    if (one) return orders.has(one[1]) ? route.fulfill(ok(view(orders.get(one[1])!))) : route.fulfill(ok({ error: { code: 'order_not_found', message: 'x' } }, 404))
    return route.fulfill(ok({ error: { code: 'x', message: 'x' } }, 404))
  })
  return created
}

for (const vp of viewports) {
  test(`mua → QR → giả lập thanh toán → thành công → đơn hàng của tôi (${vp.name})`, async ({ page }) => {
    await page.setViewportSize({ width: vp.width, height: vp.height })
    const created = await mockShop(page)
    await page.goto(`/thanh-toan?khoa=${course.slug}`)
    await expect(page.getByRole('heading', { name: 'Xác nhận đơn hàng' })).toBeVisible()
    await expect(page.getByRole('radio', { name: /^6 tháng/ })).toBeChecked()
    if (shots) await page.screenshot({ path: `${shots}/confirm-${vp.name}.png`, fullPage: true })

    await page.getByRole('button', { name: 'Tạo đơn và thanh toán' }).click()
    await expect(page).toHaveURL(/\/don-hang\/ORD-NEW001$/)
    await expect(page.getByRole('img', { name: 'Mã QR thanh toán' })).toBeVisible()
    await expect(page.getByText('Thanh toan ORD-NEW001')).toBeVisible()
    expect(created[0].body).toEqual({ productIds: [6] })
    expect(created[0].key).toBeTruthy()
    if (shots) await page.screenshot({ path: `${shots}/pending-${vp.name}.png`, fullPage: true })

    await page.getByRole('button', { name: 'Giả lập đã thanh toán' }).click()
    await expect(page.getByRole('heading', { name: 'Thanh toán thành công' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Vào bài giảng' })).toHaveAttribute('href', '/bai-giang/khoa/7')
    if (shots) await page.screenshot({ path: `${shots}/paid-${vp.name}.png`, fullPage: true })

    await page.getByRole('link', { name: 'Về đơn hàng' }).click()
    await expect(page.getByRole('heading', { name: 'Đơn hàng của tôi' })).toBeVisible()
    await expect(page.getByText('Đã thanh toán')).toBeVisible()
    if (shots) await page.screenshot({ path: `${shots}/orders-${vp.name}.png`, fullPage: true })
  })

  test(`đơn hết hạn → tạo đơn mới; danh sách có đơn chờ (${vp.name})`, async ({ page }) => {
    await page.setViewportSize({ width: vp.width, height: vp.height })
    const created = await mockShop(page, [order('ORD-OLD001', 'EXPIRED', -5), order('ORD-OLD002', 'PENDING', 20), order('ORD-OLD003', 'PAID', 0)])
    await page.goto('/don-hang')
    await expect(page.getByRole('link', { name: 'Tiếp tục thanh toán' })).toHaveAttribute('href', '/don-hang/ORD-OLD002')
    if (shots) await page.screenshot({ path: `${shots}/orders-mixed-${vp.name}.png`, fullPage: true })

    await page.goto('/don-hang/ORD-OLD001')
    await expect(page.getByRole('heading', { name: 'Mã thanh toán đã hết hạn' })).toBeVisible()
    if (shots) await page.screenshot({ path: `${shots}/expired-${vp.name}.png`, fullPage: true })
    await page.getByRole('button', { name: 'Tạo đơn mới' }).click()
    await expect(page).toHaveURL(/\/don-hang\/ORD-NEW001$/)
    expect(created[0].body).toEqual({ productIds: [6] })
    await expect(page.getByText('Đang chờ thanh toán')).toBeVisible()
  })
}
