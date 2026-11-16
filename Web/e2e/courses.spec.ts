import { expect, test } from '@playwright/test'

// Không cần backend: khối dữ liệu có thể báo lỗi, chỉ kiểm tiêu đề.
test('mở /khoa-hoc khi chưa đăng nhập thấy tiêu đề', async ({ page }) => {
  await page.goto('/khoa-hoc')
  await expect(page.getByRole('heading', { name: 'Khóa học', level: 1 })).toBeVisible()
})

test('mở /goi-y-khoa khi chưa đăng nhập thấy câu hỏi đầu', async ({ page }) => {
  await page.goto('/goi-y-khoa')
  await expect(page.getByRole('heading', { name: 'Trình độ tiếng Anh hiện tại của bạn?' })).toBeVisible()
})
