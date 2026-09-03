import { apiClient } from './http';
import type { CompleteRefundPayload, OrderReturn, OrderReturnPayload, Refund, ReturnPage, RefundPage, SalesPageParams } from '../types/sales';
const idempotency = (key?: string) => key ? { headers: { 'Idempotency-Key': key } } : undefined;
export async function fetchReturns(params: SalesPageParams) { return (await apiClient.get<ReturnPage>('/order-returns', { params })).data; }
export async function fetchReturn(id: number) { return (await apiClient.get<OrderReturn>(`/order-returns/${id}`)).data; }
export async function createReturn(payload: OrderReturnPayload, key: string) { return (await apiClient.post<number>('/order-returns', payload, idempotency(key))).data; }
export async function approveReturn(id: number, version: number, key: string) { await apiClient.post(`/order-returns/${id}/approve`, { version }, idempotency(key)); }
export async function rejectReturn(id: number, version: number, reason: string, key: string) { await apiClient.post(`/order-returns/${id}/reject`, { version, reason }, idempotency(key)); }
export async function cancelReturn(id: number, version: number, reason: string, key: string) { await apiClient.post(`/order-returns/${id}/cancel`, { version, reason }, idempotency(key)); }
export async function fetchRefunds(params: SalesPageParams) { return (await apiClient.get<RefundPage>('/order-refunds', { params })).data; }
export async function fetchRefund(id: number) { return (await apiClient.get<Refund>(`/order-refunds/${id}`)).data; }
export async function completeRefund(id: number, payload: CompleteRefundPayload, key: string) { await apiClient.post(`/order-refunds/${id}/complete`, payload, idempotency(key)); }
export type { Refund };
