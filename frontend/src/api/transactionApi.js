import client from './client'

export const fetchTransactions = (params) => client.get('/transactions', { params })
export const createTransaction = (payload) => client.post('/transactions', payload)
export const updateTransaction = (id, payload) => client.put(`/transactions/${id}`, payload)
export const deleteTransaction = (id) => client.delete(`/transactions/${id}`)
