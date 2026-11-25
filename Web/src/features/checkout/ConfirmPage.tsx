import { useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { ResendVerification } from '../../auth/ResendVerification'
import { NETWORK_ERROR } from '../../auth/formErrors'
import { ApiError } from '../../lib/api'
import { Skeleton } from '../home/BlockState'
import { useCatalogDetail } from '../lectures/api'
import { useMe } from '../profile/api'
import { isApiError, useCreateOrder } from './api'
import { formatDays, formatVnd } from './format'
import { btnOutline, btnPrimary, card, Field, Notice, PageHead, Pill } from './ui'

// FR-COM-01/02, D-76/D-78: xác nhận gói rồi tạo đơn. Mỗi (gói) một Idempotency-Key, bấm lại sau lỗi mạng dùng lại key.
export function ConfirmPage() {
  const [params] = useSearchParams()
  const slug = params.get('khoa')
  const navigate = useNavigate()
  const detail = useCatalogDetail(slug ?? undefined, true)
  const me = useMe()
  const create = useCreateOrder()
  const [picked, setPicked] = useState<number | null>(Number(params.get('goi')) || null) // gói chọn sẵn từ trang chi tiết khóa
  const key = useRef<{ id: number; key: string } | null>(null)
  const [error, setError] = useState<unknown>(null)

  if (!slug) return <Notice title="Chưa chọn khóa học" body="Hãy chọn khóa học để mua."><Link to="/khoa-hoc" className={btnPrimary}>Xem khóa học</Link></Notice>
  if (detail.isPending || me.isPending) return <Skeleton className="h-72 w-full" />
  if (detail.isError)
    return (
      <Notice title="Không tải được khóa học" body={isApiError(detail.error, 404) ? 'Khóa học không tồn tại.' : 'Vui lòng thử lại.'}>
        <Link to="/khoa-hoc" className={btnOutline}>Về khóa học</Link>
      </Notice>
    )

  const course = detail.data
  const back = <Link to={`/khoa-hoc/${slug}`} className={btnOutline}>Quay lại khóa học</Link>
  const products = [...course.products].sort((a, b) => a.accessDays - b.accessDays) // mặc định gói ngắn nhất
  if (products.length === 0) return <Notice title="Khóa học chưa mở bán" body="Khóa này chưa có gói học để mua.">{back}</Notice>

  const product = products.find((p) => p.id === picked) ?? products[0]
  const submit = () => {
    if (key.current?.id !== product.id) key.current = { id: product.id, key: crypto.randomUUID() }
    setError(null)
    create.mutate({ productIds: [product.id], key: key.current.key }, { onSuccess: (o) => navigate(`/don-hang/${o.code}`), onError: setError })
  }

  return (
    <div className="flex flex-col gap-4 lg:gap-6">
      <PageHead title="Xác nhận đơn hàng" sub="Kiểm tra gói học trước khi thanh toán." />
      <div className="grid gap-4 lg:grid-cols-2">
        <section aria-label="Gói học" className={card}>
          <Pill className="bg-subtle text-brand-strong">Khóa học</Pill>
          <h2 className="text-[21px] font-black leading-[29px] text-ink">{course.title}</h2>
          {products.length > 1 ? (
            <fieldset className="flex flex-col gap-2">
              <legend className="sr-only">Chọn gói</legend>
              {products.map((p) => (
                <label key={p.id} className={`flex min-h-12 cursor-pointer items-center gap-3 rounded-sm border px-3 py-2 ${p.id === product.id ? 'border-brand bg-subtle' : 'border-line'}`}>
                  <input type="radio" name="product" checked={p.id === product.id} onChange={() => setPicked(p.id)} className="size-4 accent-brand" />
                  <span className="flex-1 font-extrabold text-ink">{formatDays(p.accessDays)}</span>
                  <span className="font-extrabold text-brand-strong">{formatVnd(p.priceVnd)}</span>
                </label>
              ))}
            </fieldset>
          ) : (
            <p className="text-[15px] font-semibold text-ink-secondary">{formatDays(product.accessDays)}</p>
          )}
          <hr className="border-line" />
          <dl><Field label="Tài khoản nhận quyền học">{me.data?.email ?? '—'}</Field></dl>
        </section>
        <section aria-label="Tổng đơn hàng" className={card}>
          <h2 className="text-[21px] font-black leading-[29px] text-ink">Tổng đơn hàng</h2>
          <dl className="flex flex-col gap-3">
            <Field label="Tổng thanh toán">{formatVnd(product.priceVnd)}</Field>
            <hr className="border-line" />
            <Field label="Phương thức">Chuyển khoản QR</Field>
          </dl>
          <ErrorNote error={error} />
          <button type="button" onClick={submit} disabled={create.isPending} className={btnPrimary}>
            {create.isPending ? 'Đang tạo đơn...' : 'Tạo đơn và thanh toán'}
          </button>
          {back}
        </section>
      </div>
    </div>
  )
}

function ErrorNote({ error }: { error: unknown }) {
  if (!error) return null
  if (isApiError(error, 403, 'email_not_verified'))
    return (
      <div role="alert" className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-sm bg-brand-soft p-3 text-sm font-bold text-brand-ink">
        <span className="flex-1">Hãy xác minh email trước khi mua. Kiểm tra hộp thư hoặc gửi lại email xác minh.</span>
        <ResendVerification className="min-h-10 font-extrabold text-brand-strong underline" />
      </div>
    )
  const msg = isApiError(error, 503, 'payment_unavailable')
    ? 'Hiện tạm thời chưa thanh toán được, vui lòng thử lại sau.'
    : error instanceof ApiError ? error.message : NETWORK_ERROR
  return <p role="alert" className="text-sm font-bold text-danger">{msg}</p>
}
