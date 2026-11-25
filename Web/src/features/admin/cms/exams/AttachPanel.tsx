import { useState } from 'react'
import { btnOutline, btnPrimary, inputCls, Loading, RetryError, Section } from '../../ui'
import { examApi, useCourses, useCourseTree, useExamWrite, type Bank, type Course, type Topic } from './api'
import { ErrorText, Field } from './ui'

const intOrNull = (v: string, max: number) => (v !== '' && Number.isInteger(Number(v)) && Number(v) >= 0 && Number(v) <= max ? Number(v) : null)

// Đề cuối khóa: PATCH courses/{id} {examBankId | clearExamBank, examUnlockPercent, examRetakes}.
function FinalExam({ course, bank }: { course: Course; bank: Bank }) {
  const [unlock, setUnlock] = useState(String(course.examUnlockPercent))
  const [retakes, setRetakes] = useState(String(course.examRetakes))
  const write = useExamWrite((body: Record<string, unknown>) => examApi.patchCourse(course.id, body))
  const u = intOrNull(unlock, 100)
  const r = intOrNull(retakes, 1000)
  const attached = course.examBankId === bank.id
  return (
    <div className="flex flex-col gap-3">
      <h3 className="font-black text-ink">Đề thi cuối khóa</h3>
      <p className="font-semibold text-ink-secondary">{attached ? 'Đề này đang là đề thi cuối khóa.' : course.examBankId ? 'Khóa đã có đề thi cuối khóa khác; gắn đề này sẽ thay thế.' : 'Khóa chưa có đề thi cuối khóa.'}</p>
      <div className="flex flex-col gap-3 md:flex-row">
        <Field label="% bài học hoàn thành để mở đề" type="number" min={0} max={100} value={unlock} onChange={(e) => setUnlock(e.target.value)} />
        <Field label="Số lần thi lại" type="number" min={0} max={1000} value={retakes} onChange={(e) => setRetakes(e.target.value)} />
      </div>
      <ErrorText error={write.error} />
      <div className="flex flex-wrap gap-3">
        <button type="button" disabled={u === null || r === null || write.isPending} onClick={() => write.mutate({ examBankId: bank.id, examUnlockPercent: u, examRetakes: r })} className={btnPrimary}>
          {attached ? 'Lưu cấu hình đề cuối khóa' : 'Gắn làm đề thi cuối khóa'}
        </button>
        {attached && (
          <button type="button" disabled={write.isPending} onClick={() => write.mutate({ clearExamBank: true })} className={btnOutline}>
            Gỡ đề cuối khóa
          </button>
        )}
      </div>
    </div>
  )
}

// Kiểm tra chương: PATCH topics/{id} {chapterTestBankId | clearChapterTest, chapterTestRetakes}.
function ChapterTest({ topic, bank }: { topic: Topic; bank: Bank }) {
  const [retakes, setRetakes] = useState(String(topic.chapterTestRetakes))
  const write = useExamWrite((body: Record<string, unknown>) => examApi.patchTopic(topic.id, body))
  const r = intOrNull(retakes, 1000)
  const attached = topic.chapterTestBankId === bank.id
  return (
    <div className="flex flex-col gap-3">
      <h3 className="font-black text-ink">Kiểm tra chương: {topic.title}</h3>
      <p className="font-semibold text-ink-secondary">{attached ? 'Đề này đang là bài kiểm tra chương.' : topic.chapterTestBankId ? 'Chương đã có bài kiểm tra khác; gắn đề này sẽ thay thế.' : 'Chương chưa có bài kiểm tra.'}</p>
      <Field label="Số lần làm lại" type="number" min={0} max={1000} value={retakes} onChange={(e) => setRetakes(e.target.value)} />
      <ErrorText error={write.error} />
      <div className="flex flex-wrap gap-3">
        <button type="button" disabled={r === null || write.isPending} onClick={() => write.mutate({ chapterTestBankId: bank.id, chapterTestRetakes: r })} className={btnPrimary}>
          {attached ? 'Lưu cấu hình kiểm tra chương' : 'Gắn làm kiểm tra chương'}
        </button>
        {attached && (
          <button type="button" disabled={write.isPending} onClick={() => write.mutate({ clearChapterTest: true })} className={btnOutline}>
            Gỡ kiểm tra chương
          </button>
        )}
      </div>
    </div>
  )
}

// FR-ADM-06, FR-EXAM-02: gắn đề thi trong khóa làm đề cuối khóa hoặc kiểm tra chương.
export function AttachPanel({ bank }: { bank: Bank }) {
  const courses = useCourses()
  const [courseId, setCourseId] = useState('')
  const tree = useCourseTree(courseId)
  const topic = tree.data?.levels.flatMap((l) => l.topics.map((t) => t.topic)).find((t) => t.id === bank.topicId)
  return (
    <Section title="Gắn đề vào khóa">
      {courses.isError ? (
        <RetryError error={courses.error} onRetry={() => courses.refetch()} />
      ) : (
        <label className="flex flex-col gap-2">
          <span className="text-sm font-extrabold text-ink">Khóa học</span>
          <select value={courseId} onChange={(e) => setCourseId(e.target.value)} className={inputCls}>
            <option value="">Chọn khóa học</option>
            {courses.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
              </option>
            ))}
          </select>
        </label>
      )}
      {courseId &&
        (tree.isPending ? (
          <Loading />
        ) : tree.isError ? (
          <RetryError error={tree.error} onRetry={() => tree.refetch()} />
        ) : (
          <>
            <FinalExam key={`${tree.data.course.id}-${tree.data.course.examBankId}`} course={tree.data.course} bank={bank} />
            {topic ? (
              <ChapterTest key={`${topic.id}-${topic.chapterTestBankId}`} topic={topic} bank={bank} />
            ) : (
              <p className="font-semibold text-ink-secondary">Đề không thuộc topic nào của khóa này nên không gắn làm kiểm tra chương được.</p>
            )}
          </>
        ))}
    </Section>
  )
}
