import axios from 'axios'

export const TOKEN_KEY = 'moneylog.accessToken'

/**
 * 모든 API 호출이 지나는 단 하나의 창구.
 * 개발에서는 Vite 프록시가, 배포에서는 nginx가 /api를 백엔드로 넘긴다.
 * 둘 다 같은 오리진이라 CORS가 발생하지 않는다.
 */
const client = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

/** 요청 인터셉터 — 저장된 토큰을 Authorization 헤더에 자동 첨부한다. */
client.interceptors.request.use((config) => {
  const token = readToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

/**
 * 응답 인터셉터 — 공통 응답 껍데기를 여기서 벗긴다.
 * 성공: { success:true, message, data, (meta) } → 그대로 반환(호출부는 data/meta 사용)
 * 실패: { success:false, code, message, data:null } → Error로 변환해 던진다.
 */
client.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const body = error.response?.data

    // 토큰 만료·위조: 토큰을 버리고 로그인 화면으로 되돌린다.
    if (error.response?.status === 401 && readToken()) {
      clearToken()
      window.location.replace('/login')
    }

    // 검증 실패면 data에 필드별 사유가 배열로 온다.
    const fieldErrors = Array.isArray(body?.data) ? body.data : null
    const message = fieldErrors?.length
      ? fieldErrors.map((d) => d.reason).join('\n')
      : body?.message || '요청을 처리하지 못했습니다.'

    return Promise.reject(Object.assign(new Error(message), { code: body?.code }))
  },
)

export function readToken() {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function saveToken(token) {
  try {
    localStorage.setItem(TOKEN_KEY, token)
  } catch {
    /* 프라이빗 모드 등에서 저장이 막힐 수 있다 */
  }
}

export function clearToken() {
  try {
    localStorage.removeItem(TOKEN_KEY)
  } catch {
    /* noop */
  }
}

export default client
