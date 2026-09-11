import { http } from './http'
import type { MallAddress, MallAddressInput } from '../types/mall'

export function addressList() {
  return http.get<MallAddress[]>('/api/mall/addresses')
}

export function addressCreate(input: MallAddressInput) {
  return http.post<MallAddress>('/api/mall/addresses', input)
}

export function addressUpdate(id: number, input: MallAddressInput) {
  return http.put<MallAddress>(`/api/mall/addresses/${id}`, input)
}

export function addressSetDefault(id: number) {
  return http.post<MallAddress>(`/api/mall/addresses/${id}/default`)
}

export function addressDelete(id: number) {
  return http.del<void>(`/api/mall/addresses/${id}`)
}
