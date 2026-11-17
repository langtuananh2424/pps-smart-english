import { useEffect, useState } from 'react'
import { pillNeutral } from '../practice/questions/ui'
import { JobTimeout, waitForJob } from '../../lib/sse'
import type { JobView } from './types'

type State = 'waiting' | 'timeout'

// Chờ một job AI qua SSE (fallback GET job). Quá 5 phút thì hiện nút "Tải lại".
export function JobWaiter({ jobId, label, onDone }: { jobId: number; label: string; onDone: (job: JobView) => void }) {
  const [state, setState] = useState<State>('waiting')
  const [round, setRound] = useState(0)

  useEffect(() => {
    const ctrl = new AbortController()
    setState('waiting')
    waitForJob(jobId, ctrl.signal).then(onDone, (e) => {
      if (e instanceof JobTimeout) setState('timeout')
      else if (!ctrl.signal.aborted) onDone({ id: jobId, kind: '', status: 'FAILED', error: 'network', quotaRefunded: false })
    })
    return () => ctrl.abort()
    // onDone đổi mỗi lần render: chỉ chạy lại khi đổi job hoặc bấm tải lại.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jobId, round])

  if (state === 'timeout') {
    return (
      <div role="alert" className="flex flex-col items-start gap-3 rounded-md bg-reward-bg p-4 text-sm font-bold text-reward-fg">
        <p>Chờ lâu hơn dự kiến. Kết quả vẫn được xử lý, bạn có thể tải lại để kiểm tra.</p>
        <button type="button" onClick={() => setRound((r) => r + 1)} className={`${pillNeutral} !h-11 !text-sm`}>Tải lại</button>
      </div>
    )
  }
  return (
    <p role="status" className="flex items-center gap-3 rounded-md bg-subtle p-4 text-base font-extrabold text-brand-ink">
      <span aria-hidden="true" className="size-5 animate-spin rounded-full border-2 border-brand border-t-transparent" />
      {label}
    </p>
  )
}
