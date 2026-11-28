import { expect, test, type Page } from '@playwright/test'
import { courses, mockLectures } from './lecturesMock'

// FR-LMS-02, D-172/D-173: phụ đề song ngữ và giải nghĩa cụm từ dưới video STORAGE.
const subtitles = {
  en: [
    { startMs: 1000, endMs: 4000, text: 'We protect the environment today.' },
    { startMs: 5000, endMs: 8000, text: 'Next line' },
  ],
  vi: [{ startMs: 1000, endMs: 4000, text: 'Chúng ta bảo vệ môi trường hôm nay.' }],
}
const glossary = [{ phrase: 'environment', partOfSpeech: 'noun', meaningVi: 'Môi trường', example: 'Protect the environment.', source: 'WIKTIONARY' }]
const lesson = {
  id: 103, title: 'Triển khai luận điểm', type: 'VIDEO', durationSec: 1110, video: { source: 'STORAGE', url: '/lectures-mock.mp4' },
  subtitles, glossary, courseId: 1, courseSlug: 'ielts-inter', courseTitle: courses[0].title, topicId: 10, topicTitle: 'Writing Task 2', levelId: 1,
}

// Không có file mp4 thật: thay play/pause/currentTime của thẻ media để điều khiển thời gian bằng tay.
async function setup(page: Page) {
  await page.addInitScript(() => {
    let t = 0
    let paused = true
    const P = HTMLMediaElement.prototype
    Object.defineProperty(P, 'currentTime', { configurable: true, get: () => t, set: (v) => { t = v } })
    Object.defineProperty(P, 'paused', { configurable: true, get: () => paused })
    P.play = function () { paused = false; this.dispatchEvent(new Event('play')); return Promise.resolve() }
    P.pause = function () { if (!paused) { paused = true; this.dispatchEvent(new Event('pause')) } }
    // File mp4 giả không tải được: chặn lỗi media để trang không chuyển sang màn "không phát được".
    document.addEventListener('error', (e) => e.target instanceof HTMLMediaElement && e.stopImmediatePropagation(), true)
  })
  await mockLectures(page)
  await page.route('**/api/lessons/103', (route) =>
    new URL(route.request().url()).pathname === '/api/lessons/103'
      ? route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(lesson) })
      : route.fallback(),
  )
  // Không gọi mạng ngoài (YouTube, Wiktionary...).
  await page.route(/^https?:\/\/(?!localhost|127\.0\.0\.1)/, (route) => route.abort())
  await page.goto('/bai-giang/103')
  await expect(page.locator('video')).toBeVisible()
}
const at = (page: Page, sec: number) =>
  page.locator('video').evaluate((v: HTMLVideoElement, s) => { v.currentTime = s; v.dispatchEvent(new Event('timeupdate')) }, sec)
const paused = (page: Page) => page.locator('video').evaluate((v: HTMLVideoElement) => v.paused)

test('phụ đề EN và VI đúng thời điểm; đổi chế độ và giữ sau khi tải lại', async ({ page }) => {
  await setup(page)
  const block = page.getByRole('region', { name: 'Phụ đề' })
  await at(page, 2)
  await expect(block.getByText('Chúng ta bảo vệ môi trường hôm nay.')).toBeVisible()
  await expect(block.getByRole('button', { name: 'environment' })).toBeVisible()
  await at(page, 4.5)
  await expect(block.getByText('Chúng ta bảo vệ môi trường hôm nay.')).toHaveCount(0)
  await at(page, 6)
  await expect(block.getByText('Next line')).toBeVisible()

  await at(page, 2)
  await block.getByRole('combobox', { name: 'Phụ đề' }).selectOption('en')
  await expect(block.getByRole('button', { name: 'environment' })).toBeVisible()
  await expect(block.getByText('Chúng ta bảo vệ môi trường hôm nay.')).toHaveCount(0)

  await page.reload()
  await expect(page.locator('video')).toBeVisible()
  await at(page, 2)
  await expect(block.getByRole('combobox', { name: 'Phụ đề' })).toHaveValue('en')
  await expect(block.getByRole('button', { name: 'environment' })).toBeVisible()
  await expect(block.getByText('Chúng ta bảo vệ môi trường hôm nay.')).toHaveCount(0)

  await block.getByRole('combobox', { name: 'Phụ đề' }).selectOption('off')
  await expect(block.getByRole('button', { name: 'environment' })).toHaveCount(0)
})

test('bấm cụm từ nối: video dừng, popup có nghĩa, ví dụ, nguồn; Esc đóng thì video phát lại', async ({ page }) => {
  await setup(page)
  await page.locator('video').evaluate((v: HTMLVideoElement) => v.play())
  await at(page, 2)
  await page.getByRole('button', { name: 'environment' }).click()
  const dlg = page.getByRole('dialog')
  await expect(dlg).toBeVisible()
  await expect(dlg).toContainText('Môi trường')
  await expect(dlg).toContainText('Protect the environment.')
  await expect(dlg).toContainText('Nguồn: Wiktionary, CC BY-SA 4.0')
  expect(await paused(page)).toBe(true)
  await page.keyboard.press('Escape')
  await expect(dlg).toHaveCount(0)
  expect(await paused(page)).toBe(false)
})

test('mobile: popup giải nghĩa là sheet sát đáy màn hình', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 700 })
  await setup(page)
  await at(page, 2)
  await page.getByRole('button', { name: 'environment' }).click()
  const box = await page.getByRole('dialog').boundingBox()
  expect(box).not.toBeNull()
  expect(Math.round(box!.width)).toBe(375)
  expect(Math.round(box!.y + box!.height)).toBe(700)
})
