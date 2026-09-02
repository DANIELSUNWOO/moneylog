import { useCallback, useEffect, useState } from 'react'
import Layout from '../components/Layout'
import Toast from '../components/Toast'
import TransactionForm from '../components/TransactionForm'
import { fetchCategories } from '../api/categoryApi'
import * as txApi from '../api/transactionApi'
import { thisMonth, won } from '../utils/format'

export default function TransactionsPage() {
  const [categories, setCategories] = useState([])
  const [items, setItems] = useState([])
  const [pagination, setPagination] = useState(null)
  const [filters, setFilters] = useState({ yearMonth: thisMonth(), type: '', categoryId: '' })
  const [page, setPage] = useState(0)
  const [modal, setModal] = useState({ open: false, editing: null })
  const [toast, setToast] = useState({ message: '', isError: false })

  const notify = (message, isError = false) => {
    setToast({ message, isError })
    setTimeout(() => setToast({ message: '', isError: false }), 2600)
  }

  // 진입 시 한 번만 받아 필터 드롭다운과 등록 폼 선택지 두 곳에서 재사용한다.
  useEffect(() => {
    fetchCategories()
      .then((body) => setCategories(body.data))
      .catch((err) => notify(err.message, true))
  }, [])

  const load = useCallback(async () => {
    try {
      const params = { yearMonth: filters.yearMonth, page, size: 20 }
      if (filters.type) params.type = filters.type
      if (filters.categoryId) params.categoryId = Number(filters.categoryId)

      const body = await txApi.fetchTransactions(params)
      setItems(body.data.transactions)
      setPagination(body.meta.pagination)
    } catch (err) {
      notify(err.message, true)
    }
  }, [filters, page])

  useEffect(() => { load() }, [load])

  const changeFilter = (e) => {
    setPage(0)
    setFilters((prev) => ({ ...prev, [e.target.name]: e.target.value }))
  }

  const save = async (payload) => {
    try {
      if (modal.editing) {
        await txApi.updateTransaction(modal.editing.id, payload)
        notify('거래를 수정했습니다.')
      } else {
        await txApi.createTransaction(payload)
        notify('거래를 등록했습니다.')
      }
      setModal({ open: false, editing: null })
      await load()
    } catch (err) {
      notify(err.message, true)
    }
  }

  const remove = async (id) => {
    if (!window.confirm('이 거래를 삭제할까요?')) return
    try {
      await txApi.deleteTransaction(id)
      notify('거래를 삭제했습니다.')
      await load()
    } catch (err) {
      notify(err.message, true)
    }
  }

  const pageIncome = items.filter((t) => t.type === 'INCOME').reduce((s, t) => s + t.amount, 0)
  const pageExpense = items.filter((t) => t.type === 'EXPENSE').reduce((s, t) => s + t.amount, 0)

  return (
    <Layout>
      <div className="toolbar">
        <input type="month" name="yearMonth" value={filters.yearMonth} onChange={changeFilter} />
        <select name="type" value={filters.type} onChange={changeFilter}>
          <option value="">전체</option>
          <option value="INCOME">수입</option>
          <option value="EXPENSE">지출</option>
        </select>
        <select name="categoryId" value={filters.categoryId} onChange={changeFilter}>
          <option value="">모든 카테고리</option>
          {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <span className="spacer" />
        <button className="btn btn-primary" onClick={() => setModal({ open: true, editing: null })}>
          + 거래 추가
        </button>
      </div>

      <div className="summary">
        <div className="chip"><small>이 페이지 수입</small><b className="amount-income">{won(pageIncome)}</b></div>
        <div className="chip"><small>이 페이지 지출</small><b className="amount-expense">{won(pageExpense)}</b></div>
        <div className="chip"><small>전체 건수</small><b>{pagination?.totalItems ?? 0}건</b></div>
      </div>

      <table className="table">
        <thead>
          <tr><th>날짜</th><th>카테고리</th><th>내용</th><th className="right">금액</th><th /></tr>
        </thead>
        <tbody>
          {items.length === 0 ? (
            <tr><td colSpan={5} className="empty">이 달에는 거래가 없습니다. &apos;+ 거래 추가&apos;로 첫 기록을 남겨보세요.</td></tr>
          ) : (
            items.map((t) => (
              <tr key={t.id}>
                <td>{t.transactionDate}</td>
                <td><span className="tag">{t.categoryName}</span></td>
                <td>{t.description || <span className="muted">—</span>}</td>
                <td className={`right ${t.type === 'INCOME' ? 'amount-income' : 'amount-expense'}`}>
                  {t.type === 'INCOME' ? '+' : '−'}{won(t.amount)}
                </td>
                <td className="right">
                  <button className="link-btn" onClick={() => setModal({ open: true, editing: t })}>수정</button>
                  <button className="link-btn" onClick={() => remove(t.id)}>삭제</button>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      <div className="pager">
        {/* hasPrev/hasNext를 서버가 계산해 주므로 프론트에서 페이지 산수를 하지 않는다 */}
        <button className="btn btn-ghost" disabled={!pagination?.hasPrev} onClick={() => setPage((p) => p - 1)}>‹ 이전</button>
        <span>{pagination?.totalPages ? `${pagination.page + 1} / ${pagination.totalPages}` : '0 / 0'}</span>
        <button className="btn btn-ghost" disabled={!pagination?.hasNext} onClick={() => setPage((p) => p + 1)}>다음 ›</button>
      </div>

      {modal.open && (
        <TransactionForm
          categories={categories}
          editing={modal.editing}
          onSubmit={save}
          onClose={() => setModal({ open: false, editing: null })}
        />
      )}
      <Toast message={toast.message} isError={toast.isError} />
    </Layout>
  )
}
