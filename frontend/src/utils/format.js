export const won = (n) => `${Number(n || 0).toLocaleString('ko-KR')}원`

export const thisMonth = () => {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

export const today = () => new Date().toISOString().slice(0, 10)
