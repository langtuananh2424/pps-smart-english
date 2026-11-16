import { useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { PlaceholderPage } from './PlaceholderPage'

// Nút đăng xuất tạm, chờ màn Hồ sơ thật.
export function ProfilePage() {
  const { logout } = useAuth()
  const navigate = useNavigate()
  return (
    <>
      <PlaceholderPage title="Hồ sơ" />
      <button
        type="button"
        onClick={async () => {
          await logout()
          navigate('/dang-nhap', { replace: true })
        }}
        className="mt-6 min-h-12 rounded-md border-2 border-line-input bg-surface px-6 font-extrabold text-ink"
      >
        Đăng xuất
      </button>
    </>
  )
}
