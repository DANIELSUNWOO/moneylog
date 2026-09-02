import client from './client'

export const fetchCategories = (type) =>
  client.get('/categories', { params: type ? { type } : {} })

export const createCategory = (payload) => client.post('/categories', payload)
export const updateCategory = (id, payload) => client.put(`/categories/${id}`, payload)
export const deleteCategory = (id) => client.delete(`/categories/${id}`)
