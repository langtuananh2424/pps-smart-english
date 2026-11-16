import abroad from '../../assets/qa/goal-abroad.svg'
import ielts from '../../assets/qa/goal-ielts.svg'
import school from '../../assets/qa/goal-school.svg'
import unsure from '../../assets/qa/goal-unsure.svg'
import work from '../../assets/qa/goal-work.svg'
import type { Goal, QaAnswers } from './schema'

// Toàn bộ chữ của Q&A ở đây để dễ sửa.
type Opt<T extends string> = { value: T; label: string; desc: string }

export const SELF_LEVELS: Opt<QaAnswers['selfLevel']>[] = [
  { value: 'MAT_GOC', label: 'Mất gốc', desc: 'Chưa nắm được ngữ pháp và từ vựng cơ bản' },
  { value: 'CO_BAN', label: 'Cơ bản', desc: 'Hiểu câu đơn giản, còn lúng túng khi giao tiếp' },
  { value: 'TRUNG_CAP', label: 'Trung cấp', desc: 'Dùng được trong tình huống quen, còn sai nhiều' },
  { value: 'KHA', label: 'Khá', desc: 'Dùng khá thoải mái, muốn nâng band lên cao' },
]

export const GOALS: (Opt<Goal> & { icon: string; tile: string })[] = [
  { value: 'IELTS_CERT', label: 'Thi chứng chỉ IELTS', desc: 'Từ mất gốc đến band 8.0', icon: ielts, tile: 'bg-subtle' },
  { value: 'STUDY_ABROAD', label: 'Du học', desc: 'Chuẩn bị điều kiện tiếng Anh để đi học', icon: abroad, tile: 'bg-[#e6f7f5]' },
  { value: 'WORK', label: 'Công việc', desc: 'Dùng tiếng Anh trong công việc hằng ngày', icon: work, tile: 'bg-reward-bg' },
  { value: 'SCHOOL', label: 'Học tốt ở trường', desc: 'Bám sát chương trình, cải thiện điểm số', icon: school, tile: 'bg-[#e8f0ff]' },
  { value: 'UNSURE', label: 'Chưa rõ, để Cam gợi ý', desc: 'Cam sẽ chọn lộ trình cân bằng cho bạn', icon: unsure, tile: 'bg-[#eaf1f4]' },
]
export const GOAL_LABEL = Object.fromEntries(GOALS.map((g) => [g.value, g.label])) as Record<Goal, string>

export const BANDS = [4, 4.5, 5, 5.5, 6, 6.5, 7, 7.5, 8]

export const HOURS: Opt<QaAnswers['weeklyHours']>[] = [
  { value: 'LT3', label: 'Dưới 3 giờ', desc: 'Vài buổi ngắn mỗi tuần' },
  { value: 'H3_5', label: '3–5 giờ', desc: 'Đều đặn khoảng 30–45 phút mỗi ngày' },
  { value: 'H5_10', label: '5–10 giờ', desc: 'Tập trung, tiến bộ nhanh' },
  { value: 'GT10', label: 'Trên 10 giờ', desc: 'Học chuyên sâu, về đích sớm' },
]

export const SKILLS: Opt<QaAnswers['skills'][number]>[] = [
  { value: 'LISTENING', label: 'Nghe', desc: 'Nghe hiểu bài nói tự nhiên' },
  { value: 'SPEAKING', label: 'Nói', desc: 'Nói trôi chảy, phát âm rõ' },
  { value: 'READING', label: 'Đọc', desc: 'Đọc nhanh, nắm ý chính' },
  { value: 'WRITING', label: 'Viết', desc: 'Viết mạch lạc, đúng ngữ pháp' },
]

export const STEP_META = [
  { topic: 'Trình độ', title: 'Trình độ tiếng Anh hiện tại của bạn?', desc: 'Chọn mức gần đúng nhất, không cần chính xác.' },
  { topic: 'Mục tiêu', title: 'Bạn học tiếng Anh để làm gì?', desc: 'Chọn tối đa 2 mục. Bạn đổi lại được bất cứ lúc nào.' },
  { topic: 'Band mục tiêu', title: 'Bạn muốn đạt band IELTS bao nhiêu?', desc: 'Chọn band bạn muốn chạm tới.' },
  { topic: 'Thời gian & kỹ năng', title: 'Bạn học được bao lâu mỗi tuần?', desc: 'Cam dùng thông tin này để chọn nhịp học vừa sức.' },
]
