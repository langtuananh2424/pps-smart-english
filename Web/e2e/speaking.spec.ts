import { expect, test, type Page } from '@playwright/test'

const ok = (body: unknown, status = 200) => ({ status, contentType: 'application/json', body: JSON.stringify(body) })
const UP = 'http://localhost:9100/pps-media'
const CORS = { 'access-control-allow-origin': '*' }

const question = { id: 8, part: 2, prompt: 'Describe a place you enjoy visiting.', cueCard: ['where the place is', 'when you usually go there', 'what you do there', 'and explain why you enjoy visiting it.'], maxSeconds: 180 }
const graded = {
  id: 5, questionId: 8, status: 'GRADED', createdAt: '2026-11-17T00:00:00Z', audioUrl: 'http://localhost:9100/pps-media/a.wav', durationSec: 42,
  result: {
    status: 'GRADED', transcript: 'I like to visit the lake near my house because it is quiet.',
    bands: { pronunciation: 6, fluency: 6.5, lexical: 7, grammar: 5.5 }, overall: 6.5,
    fluency: { wordsPerMinute: 118, longPauses: 2, pausesPerMinute: 3, speechRatio: 0.8, band: 6 },
    pronunciationScore: 0.71,
    mispronounced: [{ word: 'quiet', score: 0.42, issues: ['vowel'], expectedIpa: 'ˈkwaɪət', heardIpa: 'ˈkwaɪt' }],
    feedback: 'Bài nói mạch lạc, cần thêm ví dụ cụ thể và mở rộng từ vựng.',
    suggestions: [{ original: 'very quiet', improved: 'wonderfully peaceful', reason: 'Từ miêu tả giàu hình ảnh hơn.' }],
  },
}

test.use({
  viewport: process.env.SHOTS && process.env.VW ? { width: Number(process.env.VW), height: 844 } : { width: 1280, height: 720 },
  // Micro giả của Chromium: trình duyệt phát âm thanh thử thay vì hỏi quyền.
  launchOptions: { args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'] },
  permissions: ['microphone'],
})

async function shot(page: Page, name: string) {
  if (process.env.SHOTS) await page.screenshot({ path: `${process.env.SHOTS}/${name}-${page.viewportSize()?.width}.png`, fullPage: true })
}

// WAV PCM16 16 kHz mono toàn mẫu 0, đủ để trình duyệt giải mã.
function silentWav(seconds: number) {
  const data = Math.round(seconds * 16000) * 2
  const b = Buffer.alloc(44 + data)
  b.write('RIFF', 0); b.writeUInt32LE(36 + data, 4); b.write('WAVE', 8); b.write('fmt ', 12)
  b.writeUInt32LE(16, 16); b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22); b.writeUInt32LE(16000, 24)
  b.writeUInt32LE(32000, 28); b.writeUInt16LE(2, 32); b.writeUInt16LE(16, 34); b.write('data', 36); b.writeUInt32LE(data, 40)
  return b
}

// Giả backend và MinIO ở tầng mạng. grading: số lần GET submission còn trả GRADING.
async function mockSpeaking(page: Page, opts: { latest?: unknown } = {}) {
  const sent = { creates: 0, uploads: [] as { size: number; fields: string[] }[], completes: 0 }
  let done = false
  await page.route(`${UP}**`, async (route) => {
    const req = route.request()
    if (req.method() === 'OPTIONS') return route.fulfill({ status: 204, headers: { ...CORS, 'access-control-allow-methods': 'POST' } })
    if (req.method() !== 'POST') return route.fulfill({ status: 200, headers: CORS, contentType: 'audio/wav', body: silentWav(1) }) // audio phát lại
    const body = req.postDataBuffer() ?? Buffer.alloc(0)
    const names = [...body.toString('latin1').matchAll(/; name="([^"]+)"/g)].map((m) => m[1])
    sent.uploads.push({ size: body.length, fields: names })
    return route.fulfill({ status: 204, headers: CORS })
  })
  await page.route('**/api/**', async (route) => {
    const req = route.request()
    const { pathname } = new URL(req.url())
    if (pathname === '/api/auth/refresh') return route.fulfill(ok({ accessToken: 't', expiresIn: 900, user: { id: 1, displayName: 'Lan', role: 'STUDENT' } }))
    if (pathname === '/api/speaking/questions/8') return route.fulfill(ok(question))
    if (pathname === '/api/me/speaking') return route.fulfill(ok({ data: opts.latest ? [opts.latest] : [] }))
    if (pathname === '/api/speaking/submissions' && req.method() === 'POST') {
      sent.creates++
      return route.fulfill(ok({ submissionId: 5, url: UP, fields: { key: 'speaking/1/x.wav', policy: 'p', 'x-amz-signature': 's' }, maxBytes: 6_000_000, contentType: 'audio/wav', maxSeconds: 180, expiresAt: '2026-11-17T01:00:00Z' }, 201))
    }
    if (pathname === '/api/speaking/submissions/5/complete') { sent.completes++; return route.fulfill(ok({ submissionId: 5, status: 'GRADING', jobId: 9, quotaRemaining: 2 }, 202)) }
    if (pathname === '/api/ai/jobs/9/stream') {
      done = true
      return route.fulfill({ status: 200, contentType: 'text/event-stream', body: `event: result\ndata: ${JSON.stringify({ id: 9, kind: 'SPEAKING_GRADE', status: 'DONE', quotaRefunded: false })}\n\n` })
    }
    if (pathname === '/api/speaking/submissions/5') return route.fulfill(ok(done ? graded : { ...graded, status: 'GRADING', result: null }))
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' })
  })
  return sent
}

test('chọn tệp audio → chuyển WAV → nộp → kết quả', async ({ page }) => {
  const sent = await mockSpeaking(page)
  await page.goto('/noi/8')
  await expect(page.getByText('Describe a place you enjoy visiting.')).toBeVisible()
  await expect(page.getByText('• where the place is')).toBeVisible()
  await expect(page.getByRole('button', { name: /Nộp bản ghi/ })).toBeDisabled()
  await shot(page, '1-de')

  await page.getByLabel('Chọn tệp audio').setInputFiles({ name: 'a.wav', mimeType: 'audio/wav', buffer: silentWav(3) })
  await expect(page.getByLabel('Nghe lại bản ghi')).toBeVisible()
  await expect(page.getByLabel('Thời gian ghi')).toContainText('00:03')
  await shot(page, '2-da-chon')
  await page.getByRole('button', { name: /Nộp bản ghi/ }).click()

  await expect(page.getByLabel('Điểm')).toContainText('6.5')
  await expect(page.getByRole('progressbar', { name: 'Ngữ pháp' })).toHaveAttribute('aria-valuenow', '5.5')
  await expect(page.getByLabel('Bản chép lời')).toContainText('lake near my house')
  await expect(page.getByLabel('Từ phát âm chưa chuẩn')).toContainText('ˈkwaɪət')
  await expect(page.getByLabel('Gợi ý diễn đạt')).toContainText('wonderfully peaceful')
  await expect(page.getByLabel('Nghe lại audio')).toBeVisible()
  await shot(page, '3-ket-qua')

  expect(sent.creates).toBe(1)
  expect(sent.completes).toBe(1)
  expect(sent.uploads).toHaveLength(1)
  expect(sent.uploads[0].fields).toEqual(['key', 'policy', 'x-amz-signature', 'file'])
  expect(sent.uploads[0].size).toBeGreaterThan(3 * 32000) // WAV 16 kHz mono 16 bit: 3 giây
})

test('ghi âm bằng micro giả → dừng → nghe lại → nộp', async ({ page }) => {
  const sent = await mockSpeaking(page)
  await page.goto('/noi/8')
  await page.getByRole('button', { name: 'Bắt đầu ghi' }).click()
  await expect(page.getByRole('button', { name: 'Dừng ghi' })).toBeVisible()
  await shot(page, '4-dang-ghi')
  await page.waitForTimeout(2200)
  await page.getByRole('button', { name: 'Dừng ghi' }).click()
  await expect(page.getByLabel('Nghe lại bản ghi')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Ghi lại' })).toBeVisible()
  await page.getByRole('button', { name: /Nộp bản ghi/ }).click()
  await expect(page.getByLabel('Điểm')).toBeVisible()
  expect(sent.uploads).toHaveLength(1)
})

test('tệp quá ngắn bị báo trước khi tải lên', async ({ page }) => {
  const sent = await mockSpeaking(page)
  await page.goto('/noi/8')
  await page.getByLabel('Chọn tệp audio').setInputFiles({ name: 'a.wav', mimeType: 'audio/wav', buffer: silentWav(0.5) })
  await expect(page.getByRole('alert')).toContainText('quá ngắn')
  await shot(page, '5-loi-ngan')
  expect(sent.creates).toBe(0)
})

test('vào trang có bản UNCLEAR: báo lý do, không trừ lượt, Nói lại', async ({ page }) => {
  const unclear = { id: 5, questionId: 8, status: 'UNCLEAR', createdAt: 'x', result: { status: 'UNCLEAR', reason: 'too_few_words' } }
  await mockSpeaking(page, { latest: unclear })
  await page.route('**/api/speaking/submissions/5', (r) => r.fulfill(ok(unclear)))
  await page.goto('/noi/8')
  await expect(page.getByText('Chưa chấm được bản ghi')).toBeVisible()
  await expect(page.getByText('Không trừ lượt chấm.')).toBeVisible()
  await shot(page, '6-unclear')
  await page.getByRole('button', { name: 'Nói lại' }).click()
  await expect(page.getByRole('button', { name: /Nộp bản ghi/ })).toBeDisabled()
})
