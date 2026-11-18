import { Link } from 'react-router'
import cam from '../../assets/exercises/cam-kinh-lup.webp'
import { chip, chipTone } from '../lectures/ui'
import { pillNeutral, pillPrimary } from '../practice/questions/ui'
import { panelCard } from './Shell'

interface Props {
  isGuest: boolean
  resume: boolean
  retake: boolean // đã có kết quả cũ: nút đổi thành "Làm lại"
  starting: boolean
  error: string | null
  onStart: () => void
  onResume: () => void
}

// Chỉ tạo lượt khi bấm "Bắt đầu" để mở trang không tốn lượt. Số câu và thời gian có sau khi bắt đầu (chưa có API xem trước).
export function Intro({ isGuest, resume, retake, starting, error, onStart, onResume }: Props) {
  return (
    <section className={panelCard}>
      <img src={cam} alt="" width={96} height={96} />
      <h1 className="text-2xl font-black leading-[30px] text-ink">Kiểm tra đầu vào</h1>
      <p className="font-semibold text-ink-secondary">Làm bài ngắn để biết band hiện tại và nhận gợi ý khóa học phù hợp.</p>
      <ul className="flex flex-wrap justify-center gap-2">
        <li className={`${chip} ${chipTone.plain}`}>Nghe · Đọc · Ngữ pháp</li>
        <li className={`${chip} ${chipTone.plain}`}>Có giới hạn thời gian</li>
        <li className={`${chip} ${chipTone.plain}`}>Không tạm dừng được</li>
      </ul>
      {isGuest && <p className="text-sm font-semibold text-ink-secondary">Bạn làm được mà không cần đăng nhập, nhưng cần đăng ký hoặc đăng nhập để xem kết quả.</p>}
      {error && <p role="alert" className="text-sm font-bold text-danger">{error}</p>}
      <div className="mt-2 flex flex-wrap justify-center gap-3">
        {resume ? (
          <button type="button" onClick={onResume} className={pillPrimary}>Tiếp tục bài đang làm</button>
        ) : (
          <button type="button" onClick={onStart} disabled={starting} className={pillPrimary}>
            {starting ? 'Đang bắt đầu…' : retake ? 'Làm lại' : 'Bắt đầu'}
          </button>
        )}
        <Link to="/khoa-hoc" className={pillNeutral}>Để sau</Link>
      </div>
    </section>
  )
}
