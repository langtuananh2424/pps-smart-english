import { screen } from '@testing-library/react'
import { auth, json, mockApi, renderAt } from '../testUtils'
import { inPath } from './navItems'

test('inPath khớp theo đoạn đường dẫn, không theo tiền tố chữ', () => {
  expect(inPath('/don-hang/PPS1', '/don-hang')).toBe(true)
  expect(inPath('/don-hang-khac', '/don-hang')).toBe(false)
})

test.each([
  ['/chuoi-hoc', 'Hồ sơ'],
  ['/muc-tieu', 'Hồ sơ'],
  ['/don-hang', 'Hồ sơ'],
  ['/don-hang/PPS1', 'Hồ sơ'],
  ['/thanh-toan', 'Khóa học'],
  ['/bai-giang/khoa/2', 'Bài giảng'],
  ['/bai-tap/khoa/2', 'Bài tập'],
])('%s sáng mục %s', async (path, label) => {
  mockApi({ '/api/auth/refresh': () => json(200, auth) })
  renderAt(path)
  const nav = await screen.findByRole('navigation', { name: 'Điều hướng chính' })
  const active = Array.from(nav.querySelectorAll('a')).filter((a) => a.className.includes('bg-brand-soft'))
  expect(active.map((a) => a.querySelector('span.hidden')?.textContent)).toEqual([label])
})
