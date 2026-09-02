import client from './client'

export const fetchMonthlyStatistics = (yearMonth) =>
  client.get('/statistics/monthly', { params: { yearMonth } })
