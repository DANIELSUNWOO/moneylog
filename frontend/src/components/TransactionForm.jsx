import { useEffect, useMemo, useState } from 'react'
import { today } from '../utils/format'

const emptyForm = { type: 'EXPENSE', amount: '', categoryId: '', description: '', transactionDate: today() }

/**
 * 등록·수정 공용 폼. PUT이 전체 교체라 두 경우의 바디가 같다.
 * 구분(type)을 바꾸면 카테고리 선택지도 그 타입만 남긴다.
 * 서버도 CATEGORY_TYPE_MISMATCH로 막지만, 화면에서 애초에 고를 수 없게 하는 편이 낫다.
 */
export default function TransactionForm({ categories, editing, onSubmit, onClose }) {
  const [form, setForm] = useState(emptyForm)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (editing) {
      setForm({
        type: editing.type,
        amount: String(editing.amount),
        categoryId: String(editing.categoryId),
        description: editing.description ?? '',
        transactionDate: editing.transactionDate,
      })
    } else {
      setForm(emptyForm)
    }
  }, [editing])

  const options = useMemo(
    () => categories.filter((c) => c.type === form.type),
    [categories, form.type],
  )

  // 구분을 바꿨는데 고른 카테고리가 그 타입에 없으면 첫 항목으로 맞춘다.
  useEffect(() => {
    if (options.length && !options.some((c) => String(c.id) === form.categoryId)) {
      setForm((prev) => ({ ...prev, categoryId: String(options[0].id) }))
    }
  }, [options, form.categoryId])

  const change = (e) => setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }))

  const submit = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      await onSubmit({
        type: form.type,
        amount: Number(form.amount),
        categoryId: Number(form.categoryId),
        description: form.description || null,
        transactionDate: form.transactionDate,
      })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="modal" onClick={(e) => e.target.classList.contains('modal') && onClose()}>
      <div className="modal-card">
        <h2>{editing ? '거래 수정' : '거래 등록'}</h2>
        <form className="form" onSubmit={submit}>
          <div className="row">
            <label>
              구분
              <select name="type" value={form.type} onChange={change}>
                <option value="EXPENSE">지출</option>
                <option value="INCOME">수입</option>
              </select>
            </label>
            <label>
              날짜
              <input type="date" name="transactionDate" value={form.transactionDate} onChange={change} required />
            </label>
          </div>
          <label>
            카테고리
            <select name="categoryId" value={form.categoryId} onChange={change} required>
              {options.length === 0 && <option value="">(해당 타입 카테고리가 없습니다)</option>}
              {options.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </label>
          <label>
            금액
            <input type="number" name="amount" value={form.amount} onChange={change} min="1" step="1" placeholder="12000" required />
          </label>
          <label>
            내용
            <input type="text" name="description" value={form.description} onChange={change} maxLength={255} placeholder="점심 - 김치찌개" />
          </label>
          <div className="row right-align">
            <button type="button" className="btn btn-ghost" onClick={onClose}>취소</button>
            <button type="submit" className="btn btn-primary" disabled={saving}>
              {saving ? '저장 중…' : '저장'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
