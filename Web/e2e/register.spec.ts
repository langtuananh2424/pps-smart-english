import { expect, test } from '@playwright/test'

test('mở /dang-ky thấy tiêu đề', async ({ page }) => {
  await page.goto('/dang-ky')
  await expect(page.getByRole('heading', { name: 'Tạo tài khoản' })).toBeVisible()
})
