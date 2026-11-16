import { expect, test } from '@playwright/test'
import { mockLectures } from './lecturesMock'

test('chọn khóa → hệ thống bài giảng → xem bài; khóa hết hạn có Gia hạn; topic khóa có link mua', async ({ page }) => {
  await mockLectures(page)
  await page.goto('/bai-giang')
  await expect(page.getByRole('heading', { name: 'Bài giảng', level: 1 })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Gia hạn' })).toHaveAttribute('href', '/khoa-hoc/cu')

  await page.getByRole('link', { name: 'Xem bài giảng →' }).first().click()
  await expect(page).toHaveURL(/\/bai-giang\/khoa\/1$/)
  await expect(page.getByRole('region', { name: 'Học tiếp' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Mua khóa học' })).toHaveAttribute('href', '/khoa-hoc/ielts-inter')

  await page.getByRole('link', { name: 'Xem tiếp' }).click()
  await expect(page).toHaveURL(/\/bai-giang\/103$/)
  await expect(page.locator('video')).toHaveAttribute('controlsList', 'nodownload')
  await expect(page.getByRole('heading', { name: /Bài 3 · Triển khai luận điểm/ })).toBeVisible()
  await expect(page.getByRole('complementary', { name: 'Bài trong chủ đề' })).toBeVisible()
})

test('mở lại bài đã xem xong thấy thẻ làm bài tập khi video kết thúc', async ({ page }) => {
  const posted = await mockLectures(page)
  await page.goto('/bai-giang/101')
  await expect(page.locator('video')).toBeVisible()
  await page.locator('video').dispatchEvent('play')
  await page.locator('video').dispatchEvent('ended')
  const card = page.getByRole('region', { name: 'Xem xong bài' })
  await expect(card.getByRole('link', { name: /Làm bài tập Bài 1/ })).toHaveAttribute('href', '/bai-tap/7')
  await expect.poll(() => posted.length).toBeGreaterThan(0)
})
