import { Link } from 'react-router'
import { ApiError } from '../../lib/api'
import { Card } from '../practice/StatusCard'
import { pillNeutral, pillPrimary } from '../practice/questions/ui'

interface Product { name: string }
const productNames = (d: unknown) => ((d as { products?: Product[] } | undefined)?.products ?? []).map((p) => p.name)

// Lỗi chặn cả màn (mở phiên / tạo phiên): thẻ trạng thái.
export function BlockingError({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const e = error instanceof ApiError ? error : null
  let title = 'Không tải được bài viết'
  let body = 'Kiểm tra kết nối rồi thử lại.'
  if (e?.status === 404) [title, body] = ['Không tìm thấy bài viết', 'Câu hỏi hoặc phiên viết này không tồn tại.']
  else if (e?.code === 'exam_question_not_practicable') [title, body] = ['Câu thuộc bài thi khóa', 'Câu này chỉ làm trong bài thi, không luyện tập riêng được.']
  else if (e?.status === 403 && e.code === 'email_not_verified') [title, body] = ['Cần xác thực email', e.message]
  else if (e?.status === 403) [title, body] = ['Bộ bài tập đang khóa', 'Bạn cần đăng ký khóa học chứa câu này để làm bài.']
  else if (e?.status === 402) return <QuotaCard error={e} />
  else if (e) body = inlineMessage(e)
  return (
    <Card title={title} body={body}>
      {onRetry && (!e || e.status >= 500 || e.status === 429) && <button type="button" onClick={onRetry} className={pillPrimary}>Thử lại</button>}
      <Link to="/bai-tap" className={pillNeutral}>Về danh sách bài tập</Link>
      <Link to="/khoa-hoc" className={pillNeutral}>Xem khóa học</Link>
    </Card>
  )
}

// Chưa có màn mua gói AI: chỉ nêu tên gói, dẫn tới đơn hàng và khóa học (D-47).
export function QuotaCard({ error }: { error: ApiError }) {
  const names = productNames(error.details)
  return (
    <Card title="Bạn đã hết lượt chấm AI" body={names.length ? `Gói bổ sung: ${names.join(', ')}.` : 'Hãy bổ sung lượt để tiếp tục.'}>
      <Link to="/don-hang" className={pillPrimary}>Đơn hàng của tôi</Link>
      <Link to="/khoa-hoc" className={pillNeutral}>Xem khóa học</Link>
    </Card>
  )
}

// Lỗi thao tác giữa chừng (gửi trả lời, tạo bản nháp, gửi chấm): báo trên màn, giữ nguyên nội dung đã gõ.
export function inlineMessage(e: unknown): string {
  if (!(e instanceof ApiError)) return 'Mất kết nối, kiểm tra mạng rồi thử lại.'
  if (e.status === 429) return 'Bạn thao tác nhanh quá, thử lại sau ít giây.'
  if (e.status === 503) return 'Hệ thống đang bận, hãy thử lại sau ít phút. Nội dung bạn đã gõ vẫn được giữ.'
  if (e.status === 409) return e.code === 'session_busy' ? 'Cam đang xử lý bước trước, vui lòng chờ.' : e.message
  return e.message
}
