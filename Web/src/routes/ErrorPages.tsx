import { Link, useRouteError } from 'react-router'

function Screen({ title, text }: { title: string; text: string }) {
  return (
    <div className="grid min-h-dvh place-items-center bg-page px-4 text-center">
      <div>
        <h1 className="text-3xl font-black text-ink">{title}</h1>
        <p className="mt-2 font-semibold text-ink-secondary">{text}</p>
        <Link
          to="/"
          className="mt-6 inline-flex min-h-12 items-center rounded-md bg-brand px-6 font-black text-white shadow-edge-primary"
        >
          Về trang chủ
        </Link>
      </div>
    </div>
  )
}

export const NotFoundPage = () => <Screen title="Không tìm thấy trang" text="Đường dẫn này không tồn tại." />

export function ErrorPage() {
  const err = useRouteError()
  if (import.meta.env.DEV) console.error(err)
  return <Screen title="Đã có lỗi xảy ra" text="Vui lòng thử lại hoặc quay về trang chủ." />
}
