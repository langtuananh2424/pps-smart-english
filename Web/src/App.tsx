import { useEffect, useState } from 'react'

type Course = { id: number; title: string; description: string; levelLabel: string }

export default function App() {
  const [courses, setCourses] = useState<Course[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/api/courses')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json() as Promise<Course[]>
      })
      .then(setCourses)
      .catch((e: Error) => setError(e.message))
  }, [])

  return (
    <main style={{ maxWidth: 720, margin: '48px auto', fontFamily: 'system-ui, sans-serif' }}>
      <h1>PPS Smart English</h1>
      {error && <p role="alert">Không tải được danh sách khóa học ({error})</p>}
      <ul>
        {courses.map((c) => (
          <li key={c.id}>
            <strong>{c.title}</strong> — {c.description}
          </li>
        ))}
      </ul>
    </main>
  )
}
