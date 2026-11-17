import type { Page } from '@playwright/test'

const ok = (body: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
const err = (status: number, code: string, details?: unknown) => ({ status, contentType: 'application/json', body: JSON.stringify({ error: { code, message: code, details } }) })

export const bank = {
  id: 7, title: 'Bộ 1 · Từ nối', lessonId: 101, isTrial: false, mode: 'PRACTICE', timeLimitSec: null,
  questions: [
    { id: 1, type: 'CONNECTIVE_MCQ', instruction: 'Chọn từ nối thích hợp.', prompt: 'It rained, ___ we stayed home.', payload: {}, choices: [{ id: 11, label: 'A', content: 'so' }, { id: 12, label: 'B', content: 'but' }], hints: [{ id: 1, content: 'Mưa dẫn đến ở nhà: kết quả.', detail: null }] },
    { id: 2, type: 'GRAMMAR_BUILD', prompt: 'Xếp thành câu.', payload: { blocks: [{ id: 'b1', text: 'I' }, { id: 'b2', text: 'like' }, { id: 'b3', text: 'tea' }] } },
    { id: 3, type: 'GRAMMAR_REWRITE', prompt: 'Viết lại với bị động.', payload: { sentence: 'They built it.', requiredElement: 'PASSIVE' } },
  ],
}
const submitted = {
  id: 9, bankId: 7, startedAt: '2026-11-17T00:00:00Z', submittedAt: '2026-11-17T00:10:00Z', score: 1.5, maxScore: 3,
  results: [
    { questionId: 1, isCorrect: true, correctAnswer: { choiceId: 11 }, explanation: 'so chỉ kết quả' },
    { questionId: 2, isCorrect: false, correctAnswer: { order: ['b1', 'b2', 'b3'] } },
    { questionId: 3, isCorrect: null },
  ],
  pendingJobs: [], 
}

// Giả backend ở tầng mạng. open: lượt dở kèm nháp; locked: bộ bị khóa. Trả các thân PUT draft và submit để test kiểm.
export async function mockPractice(page: Page, opts: { open?: boolean; locked?: boolean } = {}) {
  const sent = { drafts: [] as unknown[], submits: [] as unknown[], starts: 0 }
  await page.route('**/api/**', async (route) => {
    const req = route.request()
    const { pathname, search } = new URL(req.url())
    const m = req.method()
    if (pathname === '/api/auth/refresh') return route.fulfill(ok({ accessToken: 't', expiresIn: 900, user: { id: 1, displayName: 'Lan', role: 'STUDENT' } }))
    if (pathname === '/api/me') return route.fulfill(ok({ displayName: 'Lan' }))
    if (pathname === '/api/grammar-elements') return route.fulfill(ok([{ code: 'PASSIVE', name: 'Passive voice', colorHex: '#8b5308' }, { code: 'INVERSION', name: 'Inversion', colorHex: null }]))
    if (pathname === '/api/banks/7') return route.fulfill(opts.locked ? err(403, 'bank_locked') : ok(bank))
    if (pathname === '/api/me/attempts') return route.fulfill(ok({ data: opts.open ? [{ id: 9, bankId: 7, startedAt: 'x', submittedAt: null, score: null, maxScore: null }] : [], nextCursor: null }))
    if (pathname === '/api/banks/7/attempts' && m === 'POST') {
      sent.starts++
      return route.fulfill({ ...ok({ id: 9, bankId: 7, startedAt: 'x', timeLimitSec: null }), status: 201 })
    }
    if (pathname === '/api/attempts/9/progress') return route.fulfill(ok({ attemptId: 9, paused: false, answers: opts.open ? [{ questionId: 1, answer: { choiceId: 12 } }] : undefined }))
    if (pathname === '/api/attempts/9/draft') {
      sent.drafts.push(req.postDataJSON())
      return route.fulfill(ok({ attemptId: 9, paused: false }))
    }
    if (pathname === '/api/attempts/9/submit') {
      sent.submits.push(req.postDataJSON())
      return route.fulfill(ok(submitted))
    }
    return route.fulfill({ status: 404, contentType: 'application/json', body: '{}' + search })
  })
  return sent
}
