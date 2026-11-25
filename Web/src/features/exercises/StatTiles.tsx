import { Link } from 'react-router'
import accuracyIcon from '../../assets/exercises/stat-accuracy.svg'
import bandIcon from '../../assets/exercises/stat-band.svg'
import examIcon from '../../assets/exercises/stat-exam.svg'
import questionsIcon from '../../assets/exercises/stat-questions.svg'
import { bandSourceLabel, bandView, percent } from './format'
import type { ExamStatus, ExerciseStats } from './types'

function Tile({ icon, bg, value, label, children }: { icon: string; bg: string; value: string; label: string; children?: React.ReactNode }) {
  return (
    <div className="flex items-center gap-3 rounded-md border-2 border-line bg-surface px-4 py-3.5">
      <span className={`grid size-[42px] shrink-0 place-items-center rounded-sm ${bg}`}>
        <img src={icon} alt="" width={21} height={21} />
      </span>
      <div className="min-w-0">
        <p className="text-2xl font-black leading-[30px] text-ink">{value}</p>
        <p className="text-sm font-bold leading-[19px] text-ink-secondary">{label}</p>
        {children}
      </div>
    </div>
  )
}

// 4 ô số liệu; Bài thi khóa hiện — khi không có đề; Band ước tính theo D-63 (bài thi, rồi kiểm tra đầu vào).
export function StatTiles({ stats, exam }: { stats: ExerciseStats; exam: ExamStatus | null | undefined }) {
  const band = bandView(stats.band)
  const hasExam = exam?.bankId != null
  return (
    <div className="hidden grid-cols-4 gap-3.5 lg:grid">
      <Tile icon={questionsIcon} bg="bg-subtle" value={String(stats.answered)} label="Câu đã làm" />
      <Tile icon={accuracyIcon} bg="bg-success-bg" value={percent(stats.accuracy)} label="Độ chính xác" />
      <Tile icon={examIcon} bg="bg-[#e8f0ff]" value={hasExam ? `${exam.attemptsUsed}/${exam.attemptsAllowed} lượt` : '—'} label="Bài thi khóa" />
      <Tile icon={bandIcon} bg="bg-reward-bg" value={band.kind === 'value' ? band.text : 'Chưa có'} label="Band ước tính">
        {stats.band && <p className="text-xs font-bold text-ink-muted">{bandSourceLabel[stats.band.source]}</p>}
        {band.kind === 'none' && (
          <Link to="/kiem-tra-dau-vao" className="text-xs font-extrabold text-brand-strong underline">
            Làm kiểm tra đầu vào
          </Link>
        )}
      </Tile>
    </div>
  )
}
