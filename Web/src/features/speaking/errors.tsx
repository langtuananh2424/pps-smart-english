import { Link } from 'react-router'
import { ApiError } from '../../lib/api'
import { Card } from '../practice/StatusCard'
import { pillNeutral, pillPrimary } from '../practice/questions/ui'
import { inlineMessage, inOpenAttempt, QuotaCard, ResumeAttemptLink } from '../writing/errors'
import { UploadError } from './api'
import { AudioError } from './wav'
import type { RecorderError } from './useRecorder'
import type { UnclearReason } from './types'

// Lỗi chặn cả màn (tải đề / tải bài nói): thẻ trạng thái.
export function BlockingError({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const e = error instanceof ApiError ? error : null
  let title = 'Không tải được bài nói'
  let body = 'Kiểm tra kết nối rồi thử lại.'
  if (inOpenAttempt(e)) [title, body] = ['Câu này đang nằm trong bài bạn chưa nộp', 'Hãy làm tiếp câu này trong bài đang làm dở.']
  else if (e?.status === 404) [title, body] = ['Không tìm thấy bài nói', 'Câu hỏi hoặc bài nói này không tồn tại.']
  else if (e?.code === 'exam_question_not_practicable') [title, body] = ['Câu thuộc bài thi khóa', 'Câu này chỉ làm trong bài thi, không luyện tập riêng được.']
  else if (e?.status === 403 && e.code === 'email_not_verified') [title, body] = ['Cần xác thực email', e.message]
  else if (e?.status === 403) [title, body] = ['Bộ bài tập đang khóa', 'Bạn cần đăng ký khóa học chứa câu này để làm bài.']
  else if (e?.status === 400) [title, body] = ['Không phải câu nói', 'Câu này không làm ở màn Nói.']
  else if (e?.status === 402) return <QuotaCard error={e} />
  else if (e) body = e.status === 429 ? 'Bạn thao tác nhanh quá, thử lại sau ít giây.' : e.message
  return (
    <Card title={title} body={body}>
      {e && inOpenAttempt(e) && <ResumeAttemptLink error={e} />}
      {onRetry && (!e || e.status >= 500 || e.status === 429) && <button type="button" onClick={onRetry} className={pillPrimary}>Thử lại</button>}
      <Link to="/bai-tap" className={pillNeutral}>Về danh sách bài tập</Link>
      <Link to="/khoa-hoc" className={pillNeutral}>Xem khóa học</Link>
    </Card>
  )
}

const AUDIO: Record<string, string> = {
  audio_missing: 'Chưa thấy bản ghi trên máy chủ. Hãy nộp lại.',
  audio_too_large: 'Bản ghi quá lớn. Hãy ghi ngắn hơn.',
  audio_invalid: 'Bản ghi không đúng định dạng. Hãy ghi lại hoặc chọn tệp khác.',
  audio_too_long: 'Bản ghi dài hơn thời lượng cho phép. Hãy ghi ngắn lại.',
  audio_too_short: 'Bản ghi quá ngắn (cần ít nhất 1 giây). Hãy ghi lại.',
}

// Lỗi khi chuyển đổi hoặc chọn tệp, trước khi tải lên.
export const audioMessage = (e: unknown, maxSeconds: number): string => {
  const issue = e instanceof AudioError ? e.issue : 'undecodable'
  if (issue === 'too_short') return 'Bản ghi quá ngắn (cần ít nhất 1 giây). Hãy ghi lại hoặc chọn tệp khác.'
  if (issue === 'too_long') return `Bản ghi dài hơn ${maxSeconds} giây. Hãy ghi ngắn lại hoặc chọn tệp khác.`
  if (issue === 'too_big') return 'Tệp quá lớn (tối đa 30 MB). Hãy chọn tệp khác.'
  return 'Không đọc được tệp âm thanh này. Hãy thử tệp khác (mp3, m4a, wav).'
}

export const recorderMessage = (e: RecorderError) =>
  e === 'denied'
    ? 'Trình duyệt không cho dùng micro. Hãy cấp quyền micro hoặc dùng “Chọn tệp audio”.'
    : 'Trình duyệt này không ghi âm được. Hãy dùng “Chọn tệp audio”.'

// Lỗi giữa chừng khi nộp (tạo lượt, tải lên, xác nhận): báo trên màn, giữ nguyên bản ghi.
export function submitMessage(e: unknown): string {
  if (e instanceof UploadError) return e.status === 0
    ? 'Không tải được bản ghi lên máy chủ (mất mạng hoặc bị chặn kết nối). Kiểm tra mạng rồi bấm nộp lại.'
    : `Tải bản ghi lên thất bại (mã ${e.status}). Bấm nộp lại để thử lần nữa.`
  if (!(e instanceof ApiError)) return 'Mất kết nối, kiểm tra mạng rồi thử lại.'
  if (AUDIO[e.code]) return AUDIO[e.code]
  if (e.code.startsWith('attempt_')) return inlineMessage(e)
  if (e.status === 410) return 'Phiên tải lên đã hết hạn. Bấm nộp lại để tạo phiên mới.'
  if (e.status === 409) return 'Bản ghi đang được xử lý, vui lòng chờ.'
  if (e.status === 429) return 'Bạn thao tác nhanh quá, thử lại sau ít giây.'
  if (e.status === 503) return 'Hệ thống chấm đang bận, hãy thử lại sau ít phút. Bản ghi của bạn vẫn được giữ.'
  return e.message
}

// Lỗi chặn khi nộp: hết lượt, câu bị khóa, không tồn tại.
export const isBlocking = (e: unknown): e is ApiError => e instanceof ApiError && [402, 403, 404].includes(e.status) && e.code !== 'attempt_not_found'

export const UNCLEAR: Record<UnclearReason, string> = {
  too_short: 'Bản ghi quá ngắn để chấm.',
  too_few_words: 'Bản ghi có quá ít từ để chấm.',
  low_confidence: 'Hệ thống nghe không rõ lời nói trong bản ghi.',
  too_slow: 'Bản ghi nói quá chậm hoặc ngắt quãng nhiều để chấm.',
}
