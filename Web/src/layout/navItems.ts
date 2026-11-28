import courses from '../assets/icons/nav-courses.svg'
import exercises from '../assets/icons/nav-exercises.svg'
import home from '../assets/icons/nav-home.svg'
import lectures from '../assets/icons/nav-lectures.svg'
import teaching from '../assets/icons/nav-teaching.svg'
import profile from '../assets/icons/nav-profile.svg'

// `also`: trang con nằm ngoài tiền tố của `to` nhưng thuộc mục này.
export interface NavItem { to: string; label: string; mobileLabel?: string; icon: string; also?: string[] }
export const navItems: NavItem[] = [
  { to: '/', label: 'Trang chủ', mobileLabel: 'Học', icon: home },
  { to: '/khoa-hoc', label: 'Khóa học', icon: courses, also: ['/thanh-toan'] },
  { to: '/bai-giang', label: 'Bài giảng', icon: lectures },
  { to: '/bai-tap', label: 'Bài tập', icon: exercises, also: ['/bai-giao', '/lop'] },
  { to: '/ho-so', label: 'Hồ sơ', icon: profile, also: ['/chuoi-hoc', '/muc-tieu', '/don-hang'] },
]

// Chỉ hiện khi có quyền class.teach (D-112); quyền theo lớp vẫn do API kiểm.
export const teachItem: NavItem = { to: '/giang-day', label: 'Giảng dạy', mobileLabel: 'Dạy', icon: teaching }

export const inPath = (path: string, base: string) => path === base || path.startsWith(`${base}/`)
