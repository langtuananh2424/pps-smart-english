import { expect, test } from '@playwright/test'
import { mockPractice } from './practiceMock'

test('bắt đầu → làm vài dạng → nộp → kết quả', async ({ page }) => {
  const sent = await mockPractice(page)
  await page.goto('/bai-tap/7')
  await expect(page.getByRole('heading', { name: 'Bộ 1 · Từ nối' })).toBeVisible()
  expect(sent.starts).toBe(0) // mở trang chưa tạo lượt
  await page.getByRole('button', { name: 'Bắt đầu' }).click()

  await expect(page.getByText(/Chọn từ nối thích hợp\./).first()).toBeVisible() // instruction thay tên dạng
  await page.getByRole('radio', { name: /so/ }).click()
  await page.getByRole('button', { name: 'Hỏi Cam' }).click()
  await expect(page.getByRole('region', { name: 'Gợi ý của Cam' })).toContainText('Mưa dẫn đến')
  await page.getByRole('button', { name: 'Câu tiếp' }).click()
  await page.getByRole('button', { name: 'tea' }).click()
  await page.getByRole('button', { name: 'Câu tiếp' }).click()
  await page.getByLabel('Câu trả lời của bạn').fill('It was built by them.')

  await expect(page.getByRole('status').filter({ hasText: 'Đã lưu' })).toBeVisible({ timeout: 6000 })
  expect(sent.drafts.length).toBeGreaterThan(0)

  await page.getByRole('button', { name: 'Nộp bài' }).click()
  await expect(page.getByRole('alertdialog')).toContainText('Nộp bài?')
  await page.getByRole('alertdialog').getByRole('button', { name: 'Nộp bài' }).click()

  await expect(page.getByText('1.5', { exact: false }).first()).toBeVisible()
  await expect(page.getByText('Đúng', { exact: true })).toBeVisible()
  await expect(page.getByText('Chưa đúng')).toBeVisible()
  await expect(page.getByText('so chỉ kết quả')).toBeVisible()
  expect(sent.submits[0]).toMatchObject({ answers: expect.arrayContaining([{ questionId: 1, answer: { choiceId: 11 } }, { questionId: 3, answer: { text: 'It was built by them.' } }]) })

  await page.getByRole('button', { name: 'Làm lại' }).click()
  await expect(page.getByRole('button', { name: 'Bắt đầu' })).toBeVisible()
})

test('có lượt dở thì làm tiếp với nháp, không tạo lượt mới', async ({ page }) => {
  const sent = await mockPractice(page, { open: true })
  await page.goto('/bai-tap/7')
  await expect(page.getByRole('radio', { name: /but/ })).toHaveAttribute('aria-checked', 'true')
  expect(sent.starts).toBe(0)
})

test('bộ bị khóa hiện thẻ trạng thái', async ({ page }) => {
  await mockPractice(page, { locked: true })
  await page.goto('/bai-tap/7')
  await expect(page.getByRole('heading', { name: 'Bộ bài tập đang khóa' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Xem khóa học' })).toHaveAttribute('href', '/khoa-hoc')
})
