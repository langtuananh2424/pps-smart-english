import courses from '../assets/icons/nav-courses.svg'
import exercises from '../assets/icons/nav-exercises.svg'
import home from '../assets/icons/nav-home.svg'
import lectures from '../assets/icons/nav-lectures.svg'
import profile from '../assets/icons/nav-profile.svg'

export const navItems = [
  { to: '/', label: 'Trang chủ', mobileLabel: 'Học', icon: home },
  { to: '/khoa-hoc', label: 'Khóa học', icon: courses },
  { to: '/bai-giang', label: 'Bài giảng', icon: lectures },
  { to: '/bai-tap', label: 'Bài tập', icon: exercises },
  { to: '/ho-so', label: 'Hồ sơ', icon: profile },
]
