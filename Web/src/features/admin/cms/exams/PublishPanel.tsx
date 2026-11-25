import { useState } from 'react'
import cam from '../../../../assets/cam-chao.png'
import { btnOutline, btnPrimary, Section } from '../../ui'
import { examApi, isPlacement, SKILL_LABEL, useBandRules, useExamWrite, usePlacementBanks, type BankDetail } from './api'
import { publishBlockers } from './rules'
import { ErrorText } from './ui'

// Figma 482:17489: xác nhận ngừng xuất bản.
function ConfirmCard({ title, lines, confirm, busy, onConfirm, onCancel }: { title: string; lines: string[]; confirm: string; busy: boolean; onConfirm: () => void; onCancel: () => void }) {
  return (
    <section role="alertdialog" aria-label={title} className="flex w-full max-w-[400px] flex-col items-center gap-3 self-center rounded-lg border border-line-brand bg-page p-6 text-center shadow-edge-card">
      <img src={cam} alt="" className="size-28 rounded-full bg-subtle object-contain" />
      <h2 className="text-2xl font-black leading-[30px] text-ink">{title}</h2>
      {lines.map((l) => (
        <p key={l} className="font-semibold text-ink-secondary">
          {l}
        </p>
      ))}
      <div className="flex flex-wrap justify-center gap-3">
        <button type="button" disabled={busy} onClick={onConfirm} className={btnPrimary}>
          {confirm}
        </button>
        <button type="button" onClick={onCancel} className={btnOutline}>
          Hủy
        </button>
      </div>
    </section>
  )
}

// FR-ADM-06 (Figma 482:17381): tóm tắt đề, kiểm điều kiện, xuất bản / ngừng xuất bản.
export function PublishPanel({ detail }: { detail: BankDetail }) {
  const { bank, questions } = detail
  const placement = isPlacement(bank)
  const rules = useBandRules()
  const others = usePlacementBanks()
  const write = useExamWrite(({ published }: { published: boolean }) => examApi.setPublished(bank, published))
  const [confirm, setConfirm] = useState<'publish' | 'unpublish' | null>(null)

  const published = bank.status === 'PUBLISHED'
  const bySkill = new Map<string, number>()
  questions.forEach((q) => bySkill.set(q.skill, (bySkill.get(q.skill) ?? 0) + 1))
  const blockers = publishBlockers(bank.questionCount, placement, questions, rules.data ?? [])
  // Chỉ một đề đầu vào hoạt động: xuất bản đề này sẽ hạ các đề đang xuất bản khác xuống nháp.
  const replaced = placement ? (others.data ?? []).filter((b) => b.id !== bank.id && b.status === 'PUBLISHED') : []
  const run = (p: boolean) => write.mutate({ published: p }, { onSettled: () => setConfirm(null) })

  if (confirm === 'unpublish') {
    return (
      <ConfirmCard
        title="Xác nhận ngừng xuất bản"
        lines={['Đề sẽ chuyển sang trạng thái bản nháp.', 'Học viên không còn thấy đề này.']}
        confirm="Xác nhận ngừng xuất bản"
        busy={write.isPending}
        onConfirm={() => run(false)}
        onCancel={() => setConfirm(null)}
      />
    )
  }
  if (confirm === 'publish') {
    return (
      <ConfirmCard
        title="Xuất bản đề này?"
        lines={[`Đề đầu vào đang xuất bản (${replaced.map((b) => b.title).join(', ')}) sẽ chuyển về bản nháp.`]}
        confirm="Xác nhận xuất bản"
        busy={write.isPending}
        onConfirm={() => run(true)}
        onCancel={() => setConfirm(null)}
      />
    )
  }

  return (
    <Section title="Xem trước và xuất bản">
      <dl className="grid gap-1 md:grid-cols-[200px_1fr] md:gap-x-4">
        <dt className="font-extrabold text-ink">Loại đề</dt>
        <dd className="font-semibold text-ink-secondary">{placement ? 'Kiểm tra đầu vào' : 'Đề thi trong khóa'}</dd>
        <dt className="font-extrabold text-ink">Số câu</dt>
        <dd className="font-semibold text-ink-secondary">{bank.questionCount}</dd>
        <dt className="font-extrabold text-ink">Kỹ năng</dt>
        <dd className="font-semibold text-ink-secondary">{[...bySkill].map(([s, n]) => `${SKILL_LABEL[s] ?? s}: ${n}`).join(', ') || 'Chưa có'}</dd>
        <dt className="font-extrabold text-ink">Thời gian</dt>
        <dd className="font-semibold text-ink-secondary">{bank.timeLimitSec ? `${Math.round((bank.timeLimitSec / 60) * 10) / 10} phút` : 'Không giới hạn'}</dd>
        <dt className="font-extrabold text-ink">Trạng thái</dt>
        <dd className="font-semibold text-ink-secondary">{published ? 'Đã xuất bản' : 'Bản nháp'}</dd>
      </dl>
      {!published && blockers.length > 0 && (
        <ul role="alert" className="rounded-md bg-reward-bg p-3 font-bold text-reward-fg">
          {blockers.map((b) => (
            <li key={b}>{b}</li>
          ))}
        </ul>
      )}
      {published && <p className="rounded-md bg-reward-bg p-3 font-semibold text-reward-fg">Nếu thay đổi đáp án, hệ thống sẽ chấm lại bài đã nộp.</p>}
      <ErrorText error={write.error} />
      <div className="flex flex-wrap gap-3">
        {published ? (
          <button type="button" onClick={() => setConfirm('unpublish')} className={btnOutline}>
            Ngừng xuất bản
          </button>
        ) : (
          <button type="button" disabled={blockers.length > 0 || write.isPending} onClick={() => (replaced.length ? setConfirm('publish') : run(true))} className={btnPrimary}>
            Xuất bản
          </button>
        )}
      </div>
    </Section>
  )
}
