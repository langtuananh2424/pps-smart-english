import { expect, test } from '@playwright/test'

test('mở /dang-nhap thấy tiêu đề', async ({ page }) => {
  await page.goto('/dang-nhap')
  await expect(page.getByRole('heading', { name: 'Chào mừng trở lại!' })).toBeVisible()
})
