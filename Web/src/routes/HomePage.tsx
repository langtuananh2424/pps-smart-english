import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import { AiSuggestCard } from '../features/home/AiSuggestCard'
import { CamBubble } from '../features/home/CamBubble'
import { HomeHeader } from '../features/home/HomeHeader'
import { ResumeCard } from '../features/home/ResumeCard'
import { RoadmapCard } from '../features/home/RoadmapCard'
import { TodayTasks } from '../features/home/TodayTasks'
import { WeekCard } from '../features/home/WeekCard'
import { api } from '../lib/api'

// Mobile xếp dọc theo `order`; từ xl chia hai cột (nội dung + cột phải) như Figma desktop.
export function HomePage() {
  const { user } = useAuth()
  const me = useQuery({ queryKey: ['me'], queryFn: () => api<{ displayName: string }>('/me') })
  const name = me.data?.displayName ?? user?.displayName ?? ''
  return (
    <div className="flex flex-col gap-4 xl:gap-6">
      <HomeHeader name={name} />
      <div className="flex flex-col gap-4 xl:grid xl:grid-cols-[minmax(0,1fr)_384px] xl:items-start xl:gap-6">
        <div className="contents xl:flex xl:flex-col xl:gap-6">
          <div className="order-1 xl:order-none">
            <ResumeCard />
          </div>
          <div className="order-3 empty:hidden xl:order-none">
            <RoadmapCard />
          </div>
          <div className="order-4 xl:order-none">
            <WeekCard />
          </div>
        </div>
        <div className="contents xl:flex xl:flex-col xl:gap-6">
          <div className="order-2 empty:hidden xl:order-none">
            <TodayTasks />
          </div>
          <div className="order-5 empty:hidden xl:order-none">
            <AiSuggestCard />
          </div>
        </div>
      </div>
      <CamBubble />
    </div>
  )
}
