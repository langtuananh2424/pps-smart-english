import { useState } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { Skeleton } from '../home/BlockState'
import { useMe } from './api'
import { EditForm } from './EditForm'
import { Overview } from './Overview'

// Tổng quan và chỉnh sửa cùng một route; "đã lưu" chỉ là cờ hiển thị banner.
export function ProfilePage() {
  const { logout, setDisplayName } = useAuth()
  const navigate = useNavigate()
  const me = useMe()
  const [editing, setEditing] = useState(false)
  const [saved, setSaved] = useState(false)

  if (me.isPending) return <Skeleton className="h-[480px] w-full" />
  if (me.isError)
    return (
      <p role="alert" className="text-sm font-bold text-ink-secondary">
        Không tải được hồ sơ.{' '}
        <button type="button" onClick={() => me.refetch()} className="min-h-11 font-extrabold text-brand-strong underline">Thử lại</button>
      </p>
    )

  if (editing)
    return (
      <EditForm
        me={me.data}
        onCancel={() => setEditing(false)}
        onSaved={(name) => {
          setDisplayName(name)
          setSaved(true)
          setEditing(false)
        }}
      />
    )
  return (
    <Overview
      me={me.data}
      saved={saved}
      onEdit={() => {
        setSaved(false)
        setEditing(true)
      }}
      onLogout={async () => {
        await logout()
        navigate('/dang-nhap', { replace: true })
      }}
    />
  )
}
