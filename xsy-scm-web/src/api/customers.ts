import { apiClient } from './http';
import type { CustomerPage, CustomerPayload, SalesPageParams, CustomerType, AgreementPrice, AgreementPricePayload, SkuSaleOption } from '../types/sales';

export async function fetchCustomers(params: SalesPageParams) { return (await apiClient.get<CustomerPage>('/customers', { params })).data; }
export async function fetchCustomerTypes() { return (await apiClient.get<CustomerType[]>('/customer-types')).data; }
export async function fetchCustomer(id: number) { return (await apiClient.get(`/customers/${id}`)).data; }
export async function createCustomer(payload: CustomerPayload) { return (await apiClient.post<number>('/customers', payload)).data; }
export async function updateCustomer(id: number, payload: CustomerPayload) { await apiClient.put(`/customers/${id}`, payload); }
export async function fetchAgreementPrices(params: SalesPageParams) { return (await apiClient.get<AgreementPrice[]>('/customer-agreement-prices', { params })).data; }
export async function createAgreementPrice(payload: AgreementPricePayload) { return (await apiClient.post<number>('/customer-agreement-prices', payload)).data; }
export async function updateAgreementPrice(id: number, payload: AgreementPricePayload) { await apiClient.put(`/customer-agreement-prices/${id}`, payload); }
export async function fetchCustomerSkus(customerId: number) { return (await apiClient.get<SkuSaleOption[]>(`/customers/${customerId}/skus`)).data; }
