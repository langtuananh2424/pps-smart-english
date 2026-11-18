import { Link } from 'react-router'
import cam from '../../assets/exercises/cam-kinh-lup.webp'
import { pillNeutral, pillPrimary } from '../practice/questions/ui'
import { panelCard } from './Shell'

const NEXT = encodeURIComponent('/kiem-tra-dau-vao')

// D-39: khách nộp xong phải đăng ký hoặc đăng nhập mới xem được kết quả; quay lại đây sẽ tự nhận kết quả (claim).
export function GuestGate() {
  return (
    <section className={panelCard}>
      <img src={cam} alt="" width={96} height={96} />
      <h1 className="text-2xl font-black leading-[30px] text-ink">Đăng ký hoặc đăng nhập để xem kết quả</h1>
      <p className="font-semibold text-ink-secondary">Bài làm của bạn đã được nộp và lưu lại. Sau khi đăng nhập, kết quả sẽ hiện ngay tại đây.</p>
      <div className="mt-2 flex flex-wrap justify-center gap-3">
        <Link to={`/dang-ky?next=${NEXT}`} className={pillPrimary}>Đăng ký</Link>
        <Link to={`/dang-nhap?next=${NEXT}`} className={pillNeutral}>Đăng nhập</Link>
      </div>
    </section>
  )
}
