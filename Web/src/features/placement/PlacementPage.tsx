import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { ApiError } from '../../lib/api'
import { fromItems } from '../practice/api'
import { pillNeutral } from '../practice/questions/ui'
import { claimPlacement, fetchMine, isNetworkError, startPlacement } from './api'
import { inlineMessage, startFailure, type StartFailure } from './errors'
import { GuestGate } from './GuestGate'
import { Intro } from './Intro'
import { ResultCard } from './ResultCard'
import { Session } from './Session'
import { panelCard, Shell } from './Shell'
import { clearLocal, loadLocal, saveLocal } from './store'
import type { PlacementResult, Saved, Started, Submitted } from './types'

type Playable = Saved & { started: Started }
type View =
  | { k: 'intro' }
  | { k: 'play'; saved: Playable }
  | { k: 'gate' }
  | { k: 'busy' }
  | { k: 'claimError'; saved: Saved; text: string }
  | { k: 'result'; result: PlacementResult | null; notice?: string }

const CLAIM_EXHAUSTED = 'Bạn đã hết lượt làm kiểm tra đầu vào nên bài vừa làm không được ghi nhận. Đây là kết quả gần nhất của tài khoản.'
const START_EXHAUSTED = 'Bạn đã hết lượt làm lại kiểm tra đầu vào. Đây là kết quả gần nhất.'

// /kiem-tra-dau-vao (FR-TEST-01/02, FR-GOAL-03). Công khai: khách làm được, đăng ký/đăng nhập mới xem kết quả (D-39).
export function PlacementPage() {
  const { user, loading } = useAuth()
  const qc = useQueryClient()
  const [view, setView] = useState<View>({ k: 'intro' })
  const [local, setLocal] = useState<Saved | null>(null) // lượt dở dang trên máy
  const [starting, setStarting] = useState(false)
  const [failure, setFailure] = useState<StartFailure | null>(null)
  const booted = useRef(false)
  const mine = useQuery({ queryKey: ['me', 'placement'], queryFn: fetchMine, enabled: !!user, retry: false })

  // Kết quả đã chốt: dọn localStorage, làm mới các màn dùng band (trang chủ, thống kê bài tập, gợi ý khóa).
  const finish = useCallback((attemptId: number, result: PlacementResult) => {
    clearLocal(attemptId)
    setLocal(null)
    void qc.invalidateQueries({ queryKey: ['me'] })
    setView({ k: 'result', result })
  }, [qc])

  const claim = useCallback(async (s: Saved) => {
    setView({ k: 'busy' })
    try {
      finish(s.attemptId, await claimPlacement(s.attemptId, s.guestToken))
    } catch (e) {
      if (e instanceof ApiError && e.code === 'placement_retakes_exhausted') {
        clearLocal(s.attemptId)
        setView({ k: 'result', result: await fetchMine().catch(() => null), notice: CLAIM_EXHAUSTED })
      } else if (e instanceof ApiError && (e.status === 404 || e.status === 403)) {
        clearLocal(s.attemptId)
        setFailure({ text: 'Lượt làm bài này không còn hiệu lực (có thể đã quá hạn lưu). Bạn có thể làm bài mới.' })
        setView({ k: 'intro' })
      } else {
        setView({ k: 'claimError', saved: s, text: isNetworkError(e) ? 'Mất kết nối, kiểm tra mạng rồi thử lại.' : inlineMessage(e) })
      }
    }
  }, [finish])

  // Khôi phục sau khi biết đã đăng nhập hay chưa: đã nộp thì claim/chờ đăng nhập, chưa nộp thì mời tiếp tục.
  useEffect(() => {
    if (loading || booted.current) return
    booted.current = true
    const s = loadLocal()
    if (!s) return
    if (!s.submitted) setLocal(s)
    else if (user) void claim(s)
    else setView({ k: 'gate' })
  }, [loading, user, claim])

  const start = async () => {
    setStarting(true)
    setFailure(null)
    try {
      const st = await startPlacement()
      const prev = loadLocal()
      if (prev && prev.attemptId !== st.attemptId) clearLocal(prev.attemptId)
      // Cùng một lượt thì bản trên máy mới hơn nháp server (mỗi thay đổi lưu ngay).
      const saved: Playable = prev?.attemptId === st.attemptId && !prev.submitted && prev.started
        ? { ...prev, started: prev.started }
        : { attemptId: st.attemptId, guestToken: st.guestToken, submitted: false, started: st, answers: fromItems(st.draftAnswers), deadlineMs: Date.now() + st.remainingSec * 1000 }
      saveLocal(saved)
      setView({ k: 'play', saved })
    } catch (e) {
      const f = startFailure(e)
      if (f === 'exhausted') setView({ k: 'result', result: await fetchMine().catch(() => null), notice: START_EXHAUSTED })
      else setFailure(f)
    } finally {
      setStarting(false)
    }
  }

  const done = async (s: Saved, r: Submitted) => {
    if (r.result) return finish(s.attemptId, r.result)
    if (r.requiresLogin) {
      saveLocal({ attemptId: s.attemptId, guestToken: s.guestToken, submitted: true, answers: {}, deadlineMs: 0 })
      return setView({ k: 'gate' })
    }
    // Học viên: lượt đã được nộp ở nơi khác, lấy kết quả có sẵn.
    const old = await fetchMine().catch(() => null)
    if (old) finish(old.attemptId, old)
    else setView({ k: 'intro' })
  }

  if (loading) return <p role="status" className="grid min-h-dvh place-items-center bg-page font-bold text-ink-secondary">Đang tải…</p>
  if (view.k === 'play') return <Session saved={view.saved} onDone={(r) => void done(view.saved, r)} />

  const failureText = failure && failure !== 'unavailable' && failure !== 'exhausted' ? failure.text : null
  return (
    <Shell>
      {view.k === 'busy' && <p role="status" className="py-10 text-center font-bold text-ink-secondary">Đang lấy kết quả…</p>}
      {view.k === 'gate' && <GuestGate />}
      {view.k === 'claimError' && (
        <section role="alert" className={panelCard}>
          <h1 className="text-2xl font-black text-ink">Chưa lấy được kết quả</h1>
          <p className="font-semibold text-ink-secondary">{view.text}</p>
          <button type="button" onClick={() => void claim(view.saved)} className={pillNeutral}>Thử lại</button>
        </section>
      )}
      {view.k === 'result' && (
        <div className="flex flex-col gap-4">
          {view.result ? <ResultCard result={view.result} notice={view.notice} /> : <p role="status" className={panelCard}>{view.notice}</p>}
          {!view.result && <Link to="/khoa-hoc" className={`${pillNeutral} self-center`}>Xem các khóa học</Link>}
        </div>
      )}
      {view.k === 'intro' && failure === 'unavailable' && (
        <section role="alert" className={panelCard}>
          <h1 className="text-2xl font-black text-ink">Chưa có đề kiểm tra</h1>
          <p className="font-semibold text-ink-secondary">Đề kiểm tra đầu vào chưa được mở. Bạn vui lòng quay lại sau.</p>
          <Link to="/khoa-hoc" className={pillNeutral}>Xem các khóa học</Link>
        </section>
      )}
      {view.k === 'intro' && failure !== 'unavailable' && (
        <div className="flex flex-col gap-6">
          {mine.data && <ResultCard result={mine.data} />}
          <Intro
            isGuest={!user}
            resume={!!local?.started}
            retake={!!mine.data}
            starting={starting}
            error={failureText}
            onStart={() => void start()}
            onResume={() => local?.started && setView({ k: 'play', saved: { ...local, started: local.started } })}
          />
        </div>
      )}
    </Shell>
  )
}
