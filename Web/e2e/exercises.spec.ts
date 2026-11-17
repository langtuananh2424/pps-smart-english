import { expect, test } from '@playwright/test'
import { mockExercises } from './exercisesMock'

test('chọn khóa → hệ thống bài tập: Cam, bài thi khóa khóa, bài theo bài giảng, nhóm dạng, tìm', async ({ page }) => {
  await mockExercises(page)
  await page.goto('/bai-tap')
  await expect(page.getByRole('heading', { name: 'Bài tập', level: 1 })).toBeVisible()
  await expect(page.getByText('Độ chính xác · 72%')).toBeVisible()
  await expect(page.getByRole('link', { name: 'Gia hạn' })).toHaveAttribute('href', '/khoa-hoc/cu')

  await page.getByRole('link', { name: 'Làm bài tập →' }).first().click()
  await expect(page).toHaveURL(/\/bai-tap\/khoa\/1$/)
  await expect(page.getByRole('region', { name: 'Cam gợi ý' })).toContainText('Nhận diện cấu trúc')
  await expect(page.getByRole('region', { name: 'Cam gợi ý' })).toContainText('54%')
  await expect(page.getByRole('button', { name: 'Bài thi khóa chưa mở' })).toBeDisabled()
  await expect(page.getByText('Hoàn thành ≥ 80% bài học để mở · hiện 39%')).toBeVisible()

  const list = page.getByRole('region', { name: 'Làm theo bài giảng' })
  await expect(list.getByRole('link', { name: 'Làm bài tập Bài 1' })).toHaveAttribute('href', '/bai-tap/7')
  await expect(page.getByText('Lượt chấm AI: 3/5 tuần này')).toBeVisible()

  await page.getByRole('button', { name: 'Ngữ pháp', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Tìm và sửa lỗi' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Trắc nghiệm từ nối' })).toHaveCount(0)
})

test('khóa không có bài thi (403) thì ẩn thẻ bài thi; bài thi mở dẫn tới bộ đề', async ({ page }) => {
  await mockExercises(page, { examStatus: 403 })
  await page.goto('/bai-tap/khoa/1')
  await expect(page.getByRole('region', { name: 'Cam gợi ý' })).toBeVisible()
  await expect(page.getByRole('region', { name: 'Bài thi khóa' })).toHaveCount(0)
})

test('bài thi khóa đã mở', async ({ page }) => {
  await mockExercises(page, { examBody: { bankId: 50, progressPercent: 90, requiredPercent: 80, unlocked: true, reason: null, attemptsUsed: 1, attemptsAllowed: 2, inProgressAttemptId: 3, lastResult: null } })
  await page.goto('/bai-tap/khoa/1')
  await expect(page.getByRole('link', { name: 'Làm tiếp' })).toHaveAttribute('href', '/bai-tap/50')
})
