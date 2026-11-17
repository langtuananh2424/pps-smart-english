import { expect, test } from '@playwright/test'
import { mockProfile } from './profileMock'

// Đặt PROFILE_SHOTS=<thư mục> để chụp ảnh đối chiếu Figma.
const shots = process.env.PROFILE_SHOTS
const viewports = [
  { name: 'desktop', width: 1280, height: 800 },
  { name: 'mobile', width: 390, height: 844 },
]

for (const vp of viewports) {
  test(`hồ sơ ${vp.name}: tổng quan, chỉnh sửa, lưu`, async ({ page }) => {
    await page.setViewportSize({ width: vp.width, height: vp.height })
    const patched = await mockProfile(page)
    await page.goto('/ho-so')
    await expect(page.getByRole('heading', { name: 'Hồ sơ', level: 1 })).toBeVisible()
    await expect(page.getByText('IELTS Intermediate')).toBeVisible()
    await expect(page.getByText('IELTS 7.0')).toBeVisible()
    await expect(page.getByText('1.240')).toBeVisible()
    await expect(page.getByText('12 ngày')).toBeVisible()
    await expect(page.getByRole('link', { name: 'Chỉnh sửa mục tiêu' })).toHaveAttribute('href', '/muc-tieu')
    if (shots) await page.screenshot({ path: `${shots}/overview-${vp.name}.png`, fullPage: true })

    await page.getByRole('button', { name: 'Chỉnh sửa hồ sơ' }).click()
    await expect(page.getByRole('heading', { name: 'Chỉnh sửa hồ sơ' })).toBeVisible()
    await expect(page.getByLabel('Email')).toHaveAttribute('readonly', '')
    if (shots) await page.screenshot({ path: `${shots}/edit-${vp.name}.png`, fullPage: true })

    await page.getByLabel('Tên hiển thị').fill('Lan Anh')
    await page.getByLabel('Trình độ tự đánh giá').selectOption('KHA')
    await page.getByRole('button', { name: 'Lưu thay đổi' }).click()
    await expect(page.getByText('Đã lưu thông tin hồ sơ.')).toBeVisible()
    await expect(page.getByText('Lan Anh').first()).toBeVisible()
    expect(patched).toEqual([{ displayName: 'Lan Anh', selfLevel: 'KHA' }])
    if (shots) await page.screenshot({ path: `${shots}/saved-${vp.name}.png`, fullPage: true })
  })
}

test('Đơn hàng dẫn tới màn chờ; Xem chuỗi học dẫn tới màn chuỗi học', async ({ page }) => {
  await mockProfile(page)
  await page.goto('/ho-so')
  await page.getByRole('link', { name: 'Đơn hàng' }).click()
  await expect(page).toHaveURL(/\/don-hang$/)
  await page.goBack()
  await page.getByRole('link', { name: 'Xem chuỗi học' }).click()
  await expect(page).toHaveURL(/\/chuoi-hoc$/)
})
