import { useQuery } from '@tanstack/react-query'
import { api } from '../../lib/api'

export interface GrammarElement { code: string; name: string; colorHex: string | null }
const NEUTRAL = '#535e70'

// D-69: tên và màu nhãn yếu tố ngữ pháp do backend quản lý; danh sách gần như không đổi nên cache lâu.
export function useGrammarElements() {
  const q = useQuery({ queryKey: ['grammar-elements'], queryFn: () => api<GrammarElement[]>('/grammar-elements'), staleTime: 60 * 60 * 1000 })
  const list = q.data ?? []
  return {
    list,
    name: (code: string) => list.find((e) => e.code === code)?.name ?? code,
    color: (code: string) => list.find((e) => e.code === code)?.colorHex ?? NEUTRAL,
  }
}
