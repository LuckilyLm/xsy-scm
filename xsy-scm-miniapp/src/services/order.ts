import { http } from './http'
import type { MallOrder, OrderStatus, PageData } from '../types/mall'

export function orderPage(params: {
  page?: number
  pageSize?: number
  status?: OrderStatus
  keyword?: string
}) {
  return http.get<PageData<MallOrder>>('/api/mall/orders', params)
}

export function orderDetail(id: number) {
  return http.get<MallOrder>(`/api/mall/orders/${id}`)
}
