import courses from '../assets/icons/nav-courses.svg'
import exercises from '../assets/icons/nav-exercises.svg'
import home from '../assets/icons/nav-home.svg'
import lectures from '../assets/icons/nav-lectures.svg'
import profile from '../assets/icons/nav-profile.svg'

// `also`: trang con nằm ngoài tiền tố của `to` nhưng thuộc mục này.
export const navItems: { to: string; label: string; mobileLabel?: string; icon: string; also?: string[] }[] = [
  { to: '/', label: 'Trang chủ', mobileLabel: 'Học', icon: home },
  { to: '/khoa-hoc', label: 'Khóa học', icon: courses, also: ['/thanh-toan'] },
  { to: '/bai-giang', label: 'Bài giảng', icon: lectures },
  { to: '/bai-tap', label: 'Bài tập', icon: exercises },
  { to: '/ho-so', label: 'Hồ sơ', icon: profile, also: ['/chuoi-hoc', '/muc-tieu', '/don-hang'] },
]

export const inPath = (path: string, base: string) => path === base || path.startsWith(`${base}/`)
