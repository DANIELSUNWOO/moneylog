import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function Layout({ children }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <>
      <header className="topbar">
        <span className="brand-sm">머니로그</span>
        <nav>
          <NavLink to="/transactions" className={({ isActive }) => `btn btn-ghost${isActive ? ' is-active' : ''}`}>
            거래내역
          </NavLink>
          <NavLink to="/statistics" className={({ isActive }) => `btn btn-ghost${isActive ? ' is-active' : ''}`}>
            월별 통계
          </NavLink>
        </nav>
        <span className="spacer" />
        <span className="who">{user?.nickname} 님</span>
        <button className="btn btn-ghost" onClick={handleLogout}>로그아웃</button>
      </header>
      <main className="container">{children}</main>
    </>
  )
}
