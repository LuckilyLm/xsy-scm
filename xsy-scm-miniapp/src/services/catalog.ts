import { http } from './http'
import type { MallCategory, MallHomeResponse, MallProduct, PageData } from '../types/mall'

export function home() {
  return http.get<MallHomeResponse>('/api/mall/home')
}

export function categories() {
  return http.get<MallCategory[]>('/api/mall/catalog/categories')
}

export function products(params: {
  page?: number
  pageSize?: number
  keyword?: string
  categoryId?: number
}) {
  return http.get<PageData<MallProduct>>('/api/mall/catalog/products', params)
}

export function product(skuId: number) {
  return http.get<MallProduct>(`/api/mall/catalog/products/${skuId}`)
}
