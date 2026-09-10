import {apiClient} from './http';
import type {
    OrderOperationLog,
    OrderPage,
    SalesOrder,
    SalesOrderPayload,
    SalesPageParams,
    ActualQuantityPayload,
    CancelOrderPayload
} from '../types/sales';

const idempotency = (key?: string) => key ? {headers: {'Idempotency-Key': key}} : undefined;

export async function fetchOrders(params: SalesPageParams) {
    return (await apiClient.get<OrderPage>('/orders', {params})).data;
}

export async function fetchOrder(id: number) {
    return (await apiClient.get<SalesOrder>(`/orders/${id}`)).data;
}

export async function fetchOrderLogs(id: number) {
    return (await apiClient.get<OrderOperationLog[]>(`/orders/${id}/logs`)).data;
}

export async function createOrder(payload: SalesOrderPayload, key: string) {
    return (await apiClient.post<number>('/orders', payload, idempotency(key))).data;
}

export async function updateOrder(id: number, payload: SalesOrderPayload) {
    await apiClient.put(`/orders/${id}`, payload);
}

export async function submitOrder(id: number, version: number, key: string) {
    return (await apiClient.post<SalesOrder>(`/orders/${id}/submit`, {version}, idempotency(key))).data;
}

export async function updateActualQuantity(id: number, itemId: number, payload: ActualQuantityPayload, key: string) {
    await apiClient.post(`/orders/${id}/items/${itemId}/actual-quantity`, payload, idempotency(key));
}

export async function confirmOrder(id: number, version: number, key: string) {
    await apiClient.post(`/orders/${id}/confirm`, {version}, idempotency(key));
}

export async function cancelOrder(id: number, payload: CancelOrderPayload, key: string) {
    await apiClient.post(`/orders/${id}/cancel`, payload, idempotency(key));
}
