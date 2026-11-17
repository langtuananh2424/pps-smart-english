import { expect, test, type Page } from '@playwright/test'

const ok = (body: unknown, status = 200) => ({ status, contentType: 'application/json', body: JSON.stringify(body) })
const sseBody = (job: unknown) => ({ status: 200, contentType: 'text/event-stream', body: `event: result\ndata: ${JSON.stringify(job)}\n\n` })

const question = { id: 8, prompt: 'Bạn nghĩ học trực tuyến có tốt hơn học ở lớp không?', payload: { requiredWords: 100 } }
const grade = {
  taskResponse: 80, coherence: 70, lexical: 60, grammar: 50, scorePercent: 65,
  correctedAnswer: 'Online learning gives students the flexibility to study from home.',
  markedAnswer: 'Online learning {{PASSIVE|is given}} flexibility.',
  connectives: { total: 3, types: 2, correct: 2, misused: ['But'], note: 'Dùng ổn.' },
  grammarElements: [{ code: 'PASSIVE', status: 'USED_WELL', quote: 'is given', upgrade: 'Thử thêm mệnh đề quan hệ.' }],
  gateNote: 'Bài có chép đề.', feedback: 'Ý rõ ràng, cần thêm ví dụ.',
}

test.use({ viewport: process.env.SHOTS && process.env.VW ? { width: Number(process.env.VW), height: 844 } : { width: 1280, height: 720 } })

// Chụp ảnh khi đặt SHOTS=<thư mục> (kiểm giao diện thủ công); bình thường bỏ qua.
async function shot(page: Page, name: string) {
  if (process.env.SHOTS) await page.screenshot({ path: `${process.env.SHOTS}/${name}-${page.viewportSize()?.width}.png`, fullPage: true })
}

// Giả backend ở tầng mạng: phiên chuyển ACTIVE → READY → SUBMITTED theo các lời gọi của học viên.
async function mockWriting(page: Page, opts: { start?: 'none' | 'active' } = {}) {
  const sent = { creates: 0, answers: [] as unknown[], keys: [] as (string | null)[] }
  const s = { status: 'ACTIVE', draft: undefined as string | undefined, finalText: undefined as string | undefined, turns: [{ role: 'CAM', text: question.prompt }, { role: 'CAM', text: 'Vì sao bạn nghĩ vậy?' }], created: opts.start === 'active' }
  const view = () => ({ sessionId: 3, status: s.status, question, turns: s.turns, draft: s.draft, finalText: s.finalText, createdAt: '2026-11-17T00:00:00Z', gradeJobId: s.status === 'SUBMITTED' ? 9 : undefined, grade: s.status === 'SUBMITTED' ? grade : undefined })
  await page.route('**/api/**', async (route) => {
    const req = route.request()
    const { pathname, search } = new URL(req.url())
    const m = req.method()
    if (pathname === '/api/auth/refresh') return route.fulfill(ok({ accessToken: 't', expiresIn: 900, user: { id: 1, displayName: 'Lan', role: 'STUDENT' } }))
    if (pathname === '/api/grammar-elements') return route.fulfill(ok([{ code: 'PASSIVE', name: 'Passive voice', colorHex: '#8b5308' }]))
    if (pathname === '/api/me/writing') return route.fulfill(ok({ data: s.created ? [{ sessionId: 3, questionId: 8, status: s.status, createdAt: 'x' }] : [] }))
    if (pathname === '/api/writing/sessions' && m === 'POST') { sent.creates++; s.created = true; return route.fulfill(ok({ sessionId: 3, question }, 201)) }
    if (pathname === '/api/writing/sessions/3' && m === 'GET') return route.fulfill(ok(view()))
    if (pathname === '/api/writing/sessions/3/answer') {
      sent.answers.push(req.postDataJSON())
      s.status = 'READY'
      s.turns = [...s.turns, { role: 'STUDENT', text: (req.postDataJSON() as { text: string }).text }]
      return route.fulfill(ok({ jobId: null, sessionStatus: 'READY' }))
    }
    if (pathname === '/api/writing/sessions/3/draft') { s.draft = 'Online learning is convenient because students can study from home.'; return route.fulfill(ok({ jobId: 20 }, 202)) }
    if (pathname === '/api/ai/jobs/20/stream') return route.fulfill(sseBody({ id: 20, kind: 'SCAFFOLD_DRAFT', status: 'DONE', quotaRefunded: false }))
    if (pathname === '/api/writing/sessions/3/submit') {
      sent.keys.push(req.headers()['idempotency-key'] ?? null)
      s.status = 'SUBMITTED'
      s.finalText = (req.postDataJSON() as { finalText: string }).finalText
      return route.fulfill(ok({ jobId: 9, status: 'PENDING', quotaRemaining: 2 }, 202))
    }
    if (pathname === '/api/ai/jobs/9/stream') return route.fulfill(sseBody({ id: 9, kind: 'WRITING_GRADE', status: 'DONE', result: grade, quotaRefunded: false }))
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' + search })
  })
  return sent
}

test('bắt đầu → trả lời → bản nháp → gửi chấm → kết quả', async ({ page }) => {
  const sent = await mockWriting(page)
  await page.goto('/viet/8')
  await expect(page.getByRole('heading', { name: 'Viết phát triển ý' })).toBeVisible()
  expect(sent.creates).toBe(0) // mở trang chưa tạo phiên
  await shot(page, '1-bat-dau')
  await page.getByRole('button', { name: 'Bắt đầu viết' }).click()

  await expect(page.getByText('CAM · VIẾT DẪN DẮT')).toBeVisible()
  await shot(page, '2-lam-bai')
  await page.getByLabel('Câu trả lời của bạn').fill('Vì học online linh hoạt hơn')
  await shot(page, '3-da-nhap')
  await page.getByRole('button', { name: 'Gửi trả lời' }).click()
  await page.getByRole('button', { name: /Xem bài viết/ }).click()

  await expect(page.getByLabel('Bản nháp bài viết')).toHaveValue(/Online learning is convenient/)
  await expect(page.getByText('10/100 từ')).toBeVisible()
  await shot(page, '4-ban-nhap')
  await page.getByLabel('Bản nháp bài viết').fill('Online learning is convenient and flexible.')
  await page.getByRole('button', { name: /Gửi chấm/ }).click()

  await expect(page.getByLabel('Điểm')).toContainText('65%')
  await expect(page.getByRole('progressbar', { name: 'Đáp ứng đề' })).toHaveAttribute('aria-valuenow', '80')
  await expect(page.getByText('Bản sửa · Gợi ý diễn đạt')).toBeVisible()
  await expect(page.getByText('Online learning is convenient and flexible.')).toBeVisible() // bản gốc
  await expect(page.getByText('Dùng sai: But')).toBeVisible()
  await expect(page.getByText('Ý rõ ràng, cần thêm ví dụ.')).toBeVisible()
  await shot(page, '5-ket-qua')
  expect(sent.keys).toHaveLength(1)
  expect(sent.keys[0]).toBeTruthy()
})

test('có phiên dở thì tiếp tục, không tạo phiên mới; reload giữ chữ gõ dở', async ({ page }) => {
  const sent = await mockWriting(page, { start: 'active' })
  await page.goto('/viet/8')
  await page.getByLabel('Câu trả lời của bạn').fill('Đang gõ dở')
  await page.reload()
  await expect(page.getByLabel('Câu trả lời của bạn')).toHaveValue('Đang gõ dở')
  expect(sent.creates).toBe(0)
})

test('hết lượt chấm AI hiện thẻ kèm tên gói', async ({ page }) => {
  await page.route('**/api/**', async (route) => {
    const { pathname } = new URL(route.request().url())
    if (pathname === '/api/auth/refresh') return route.fulfill(ok({ accessToken: 't', expiresIn: 900, user: { id: 1, displayName: 'Lan', role: 'STUDENT' } }))
    if (pathname === '/api/me/writing') return route.fulfill(ok({ data: [] }))
    if (pathname === '/api/writing/sessions') return route.fulfill(ok({ error: { code: 'quota_exhausted', message: 'x', details: { products: [{ type: 'AI_ADDON', name: 'Nạp 20 lượt' }] } } }, 402))
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' })
  })
  await page.goto('/viet/8')
  await page.getByRole('button', { name: 'Bắt đầu viết' }).click()
  await expect(page.getByRole('heading', { name: 'Bạn đã hết lượt chấm AI' })).toBeVisible()
  await expect(page.getByText(/Nạp 20 lượt/)).toBeVisible()
  await shot(page, '6-het-luot')
})
