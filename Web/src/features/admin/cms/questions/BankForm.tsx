import { useState } from 'react'
import { btnOutline, btnPrimary, Section, WriteError } from '../../ui'
import { bankWrites, useCmsWrite } from './api'
import { Err, Labeled, smInput } from './fields'
import type { BankView, LessonView } from './types'
import { detailsOf, errAt } from './util'

interface Props {
  bank?: BankView
  topicId?: number
  // Không truyền thì ẩn ô bài giảng (màn soạn không có danh sách bài giảng của topic).
  lessons?: LessonView[]
  onDone: (b: BankView) => void
  onCancel: () => void
}

// Thuộc tính bộ câu hỏi. timeLimitSec để trống thì không gửi (backend chưa có cách xóa giờ đã đặt).
export function BankForm({ bank, topicId, lessons, onDone, onCancel }: Props) {
  const [title, setTitle] = useState(bank?.title ?? '')
  const [lessonId, setLessonId] = useState(bank?.lessonId ? String(bank.lessonId) : '')
  const [isTrial, setIsTrial] = useState(bank?.isTrial ?? false)
  const [mode, setMode] = useState(bank?.mode ?? 'PRACTICE')
  const [timeLimit, setTimeLimit] = useState(bank?.timeLimitSec ? String(bank.timeLimitSec) : '')
  const [playbackMode, setPlaybackMode] = useState(bank?.playbackMode ?? 'FREE')
  const [playLimit, setPlayLimit] = useState(bank?.playLimit ? String(bank.playLimit) : '')
  const [passPct, setPassPct] = useState(bank?.passPct ? String(bank.passPct) : '')
  const placement = bank?.purpose === 'PLACEMENT'

  const save = useCmsWrite(() => {
    const body = {
      title,
      isTrial,
      mode,
      playbackMode,
      ...(lessonId ? { lessonId: Number(lessonId) } : {}),
      ...(timeLimit ? { timeLimitSec: Number(timeLimit) } : {}),
      ...(playLimit ? { playLimit: Number(playLimit) } : {}),
      // Sửa mà để trống thì về mặc định 70 (không gửi thì BE giữ ngưỡng cũ).
      ...(!placement && (passPct || bank) ? { passPct: passPct ? Number(passPct) : 70 } : {}),
    }
    return bank ? bankWrites.patch(bank.id, body) : bankWrites.create(topicId!, body)
  })
  const errors = detailsOf(save.error)
  const clientErr = title.trim() ? [] : ['Vui lòng nhập tên bộ câu hỏi']
  const [submitted, setSubmitted] = useState(false)

  return (
    <form
      aria-label="Thuộc tính bộ câu hỏi"
      onSubmit={async (e) => {
        e.preventDefault()
        setSubmitted(true)
        if (clientErr.length) return
        const b = await save.mutateAsync().catch(() => null)
        if (b) onDone(b)
      }}
    >
      <Section title={bank ? 'Thuộc tính bộ câu hỏi' : 'Tạo bộ câu hỏi'}>
        <div className="grid gap-3 md:grid-cols-2">
          <Labeled label="Tên bộ câu hỏi" htmlFor="b-title">
            <input id="b-title" maxLength={200} value={title} onChange={(e) => setTitle(e.target.value)} className={smInput} aria-invalid={submitted && clientErr.length > 0} />
            <Err list={[...(submitted ? clientErr : []), ...errAt(errors, 'title')]} />
          </Labeled>
          {lessons && (
            <Labeled label="Bài giảng gắn kèm" htmlFor="b-lesson">
              <select id="b-lesson" value={lessonId} onChange={(e) => setLessonId(e.target.value)} className={smInput}>
                <option value="">Không gắn bài giảng</option>
                {lessons.map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.title}
                  </option>
                ))}
              </select>
              <Err list={errAt(errors, 'lessonId')} />
            </Labeled>
          )}
          <Labeled label="Chế độ làm bài" htmlFor="b-mode">
            <select id="b-mode" value={mode} onChange={(e) => setMode(e.target.value as BankView['mode'])} className={smInput}>
              <option value="PRACTICE">Luyện tập (được tạm dừng)</option>
              <option value="EXAM">Thi (không tạm dừng)</option>
            </select>
            <Err list={errAt(errors, 'mode')} />
          </Labeled>
          <Labeled label="Giới hạn thời gian (giây)" htmlFor="b-time">
            <input id="b-time" type="number" min={1} value={timeLimit} onChange={(e) => setTimeLimit(e.target.value)} className={smInput} />
            <Err list={errAt(errors, 'timeLimitSec')} />
          </Labeled>
          <Labeled label="Chế độ phát audio" htmlFor="b-playback">
            <select id="b-playback" value={playbackMode} disabled={placement} onChange={(e) => setPlaybackMode(e.target.value as BankView['playbackMode'])} className={smInput}>
              <option value="FREE">Tự do</option>
              <option value="EXAM">Thi (tự phát)</option>
              <option value="LOCKED">Nghe ép (khóa)</option>
            </select>
            <Err list={errAt(errors, 'playbackMode')} />
          </Labeled>
          <Labeled label="Số lần nghe tối đa" htmlFor="b-play-limit">
            <input id="b-play-limit" type="number" min={1} value={playLimit} onChange={(e) => setPlayLimit(e.target.value)} className={smInput} />
            <Err list={errAt(errors, 'playLimit')} />
          </Labeled>
          {!placement && (
            <Labeled label="Ngưỡng đạt (%)" htmlFor="b-pass" hint="Để trống thì dùng 70. Khác ngưỡng của Nghe ép.">
              <input id="b-pass" type="number" min={1} max={100} step={1} value={passPct} onChange={(e) => setPassPct(e.target.value)} className={smInput} />
              <Err list={errAt(errors, 'passPct')} />
            </Labeled>
          )}
        </div>
        <label className="flex min-h-10 items-center gap-2 font-extrabold text-ink">
          <input type="checkbox" checked={isTrial} onChange={(e) => setIsTrial(e.target.checked)} />
          Cho học thử
        </label>
        {save.isError && <WriteError error={save.error} />}
        <div className="flex flex-wrap gap-2">
          <button type="submit" className={btnPrimary} disabled={save.isPending}>
            {bank ? 'Lưu thuộc tính' : 'Tạo bộ'}
          </button>
          <button type="button" className={btnOutline} onClick={onCancel}>
            Hủy
          </button>
        </div>
      </Section>
    </form>
  )
}
