import { useEffect, useState } from 'react'
import Layout from '../components/Layout'
import Toast from '../components/Toast'
import { fetchMonthlyStatistics } from '../api/statisticsApi'
import { thisMonth, won } from '../utils/format'

export default function StatisticsPage() {
  const [yearMonth, setYearMonth] = useState(thisMonth())
  const [stats, setStats] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    fetchMonthlyStatistics(yearMonth)
      .then((body) => { setStats(body.data); setError('') })
      .catch((err) => setError(err.message))
  }, [yearMonth])

  const max = Math.max(...(stats?.byCategory.map((c) => c.total) ?? [0]), 1)

  return (
    <Layout>
      <div className="toolbar">
        <input type="month" value={yearMonth} onChange={(e) => setYearMonth(e.target.value)} />
      </div>

      <div className="summary">
        {/* balance는 서버가 계산해 내려준 값을 그대로 쓴다. 프론트가 다시 더하면 기준이 갈린다. */}
        <div className="chip"><small>총수입</small><b className="amount-income">{won(stats?.income)}</b></div>
        <div className="chip"><small>총지출</small><b className="amount-expense">{won(stats?.expense)}</b></div>
        <div className="chip"><small>잔액</small><b>{won(stats?.balance)}</b></div>
      </div>

      <h3 className="section-title">카테고리별 지출</h3>
      <div className="bars">
        {stats?.byCategory.length ? (
          stats.byCategory.map((c) => (
            <div className="bar-row" key={c.categoryId}>
              <span>{c.categoryName}</span>
              <span className="bar-track">
                <span className="bar-fill" style={{ width: `${(c.total / max) * 100}%` }} />
              </span>
              <span className="right">{won(c.total)}</span>
            </div>
          ))
        ) : (
          <div className="empty">이 달에는 지출 기록이 없습니다.</div>
        )}
      </div>

      <Toast message={error} isError />
    </Layout>
  )
}
