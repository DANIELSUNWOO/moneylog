import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

/**
 * 로그인하지 않았으면 로그인 화면으로 보낸다.
 * 화면 가드일 뿐이고 진짜 방어는 서버가 한다 — 누구든 curl로 API를 직접 부를 수 있다.
 */
export default function ProtectedRoute({ children }) {
  const { user, loading } = useAuth()

  if (loading) return <div className="center-note">불러오는 중…</div>
  if (!user) return <Navigate to="/login" replace />
  return children
}
