import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import * as authApi from '../api/authApi'
import Toast from '../components/Toast'

export default function LoginPage() {
  const { user, loading, login } = useAuth()
  const navigate = useNavigate()
  const [tab, setTab] = useState('login')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)

  if (loading) return <div className="center-note">불러오는 중…</div>
  if (user) return <Navigate to="/transactions" replace />

  const handleLogin = async (e) => {
    e.preventDefault()
    const f = e.target
    setError('')
    setBusy(true)
    try {
      await login(f.email.value, f.password.value)
      navigate('/transactions', { replace: true })
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const handleSignup = async (e) => {
    e.preventDefault()
    const f = e.target
    setError('')
    setBusy(true)
    try {
      await authApi.signup({
        email: f.email.value,
        password: f.password.value,
        nickname: f.nickname.value,
      })
      setNotice('가입이 완료되었습니다. 로그인해 주세요.')
      setTab('login')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="auth-view">
      <div className="card auth-card">
        <h1 className="brand">머니로그</h1>
        <p className="brand-sub">내 수입과 지출을 기록하고, 한 달을 한눈에.</p>

        <div className="tabs">
          <button className={`tab${tab === 'login' ? ' is-active' : ''}`} onClick={() => setTab('login')}>로그인</button>
          <button className={`tab${tab === 'signup' ? ' is-active' : ''}`} onClick={() => setTab('signup')}>회원가입</button>
        </div>

        {tab === 'login' ? (
          <form className="form" onSubmit={handleLogin}>
            <label>이메일<input type="email" name="email" required placeholder="sun@moneylog.com" /></label>
            <label>비밀번호<input type="password" name="password" required placeholder="8자 이상" /></label>
            <button type="submit" className="btn btn-primary" disabled={busy}>로그인</button>
          </form>
        ) : (
          <form className="form" onSubmit={handleSignup}>
            <label>이메일<input type="email" name="email" required /></label>
            <label>비밀번호<input type="password" name="password" required minLength={8} placeholder="8자 이상" /></label>
            <label>닉네임<input type="text" name="nickname" required maxLength={50} /></label>
            <button type="submit" className="btn btn-primary" disabled={busy}>가입하기</button>
          </form>
        )}

        {error && <p className="form-error">{error}</p>}
      </div>
      <Toast message={notice} />
    </section>
  )
}
