import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { clearToken, readToken, saveToken } from '../api/client'
import * as authApi from '../api/authApi'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  // 새로고침하면 메모리의 상태는 날아가고 토큰만 남는다.
  // 그 토큰으로 /users/me를 호출해 로그인 상태를 복구한다.
  useEffect(() => {
    if (!readToken()) {
      setLoading(false)
      return
    }
    authApi
      .fetchMe()
      .then((body) => setUser(body.data))
      .catch(() => clearToken())
      .finally(() => setLoading(false))
  }, [])

  const login = useCallback(async (email, password) => {
    const body = await authApi.login({ email, password })
    saveToken(body.data.accessToken)
    const me = await authApi.fetchMe()
    setUser(me.data)
  }, [])

  const logout = useCallback(() => {
    clearToken()
    setUser(null)
  }, [])

  const value = useMemo(() => ({ user, loading, login, logout }), [user, loading, login, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth는 AuthProvider 안에서만 쓸 수 있습니다.')
  return context
}
