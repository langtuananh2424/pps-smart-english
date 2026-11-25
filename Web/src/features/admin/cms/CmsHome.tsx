import { Link } from 'react-router'

const links = [
  { to: '/quan-tri/cms/khoa-hoc', label: 'Khóa học' },
  { to: '/quan-tri/cms/ngan-hang', label: 'Ngân hàng câu hỏi' },
  { to: '/quan-tri/cms/de-thi', label: 'Đề thi và bảng quy đổi' },
]

// Trang chủ CMS: lối vào khóa học, ngân hàng câu hỏi, đề thi.
export function CmsHome() {
  return (
    <>
      <h1 className="text-2xl font-extrabold text-ink">CMS</h1>
      <ul className="flex flex-col gap-2">
        {links.map((l) => (
          <li key={l.to}>
            <Link to={l.to} className="inline-flex min-h-11 items-center rounded-md border border-line-brand px-3.5 font-extrabold text-ink hover:bg-subtle">
              {l.label}
            </Link>
          </li>
        ))}
      </ul>
    </>
  )
}
