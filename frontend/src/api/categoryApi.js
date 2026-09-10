import client from './client'

export const fetchCategories = (type) =>
  client.get('/categories', { params: type ? { type } : {} })
