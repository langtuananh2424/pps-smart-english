import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import { ApiError } from '../lib/api'

export const NETWORK_ERROR = 'Không kết nối được máy chủ, vui lòng thử lại.'

// Lỗi field của backend (details: {field: message}) gắn vào ô tương ứng; không gắn được thì trả message chung.
export function applyApiError<T extends FieldValues>(e: unknown, setError: UseFormSetError<T>, fields: readonly string[]): string {
  if (!(e instanceof ApiError)) return NETWORK_ERROR
  let matched = false
  if (e.details && typeof e.details === 'object') {
    for (const [k, msg] of Object.entries(e.details)) {
      if (fields.includes(k) && typeof msg === 'string') {
        setError(k as Path<T>, { message: msg })
        matched = true
      }
    }
  }
  return matched ? '' : e.message
}
