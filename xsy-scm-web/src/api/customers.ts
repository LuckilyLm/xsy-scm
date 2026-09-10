import {apiClient} from './http';
import type {
    CustomerDetail,
    CustomerPage,
    CustomerPayload,
    SalesPageParams,
    CustomerType,
    CustomerTypeInput,
    AgreementPrice,
    AgreementPricePayload,
    SkuSaleOption
} from '../types/sales';
import type {ProductPageParams, ProductSummary} from '../types/product';
import type {PageData} from '../types/product';

export async function fetchCustomers(params: SalesPageParams) {
    return (await apiClient.get<CustomerPage>('/customers', {params})).data;
}

export async function fetchCustomerTypes() {
    return (await apiClient.get<CustomerType[]>('/customer-types')).data;
}

export async function createCustomerType(payload: CustomerTypeInput) {
    return (await apiClient.post<number>('/customer-types', payload)).data;
}

export async function updateCustomerType(id: number, payload: CustomerTypeInput) {
    await apiClient.put(`/customer-types/${id}`, payload);
}

export async function fetchCustomer(id: number) {
    return (await apiClient.get<CustomerDetail>(`/customers/${id}`)).data;
}

export async function fetchCustomerSkus(id: number) {
    return (await apiClient.get<SkuSaleOption[]>(`/customers/${id}/skus`)).data;
}

export async function createCustomer(payload: CustomerPayload) {
    return (await apiClient.post<number>('/customers', payload)).data;
}

export async function updateCustomer(id: number, payload: CustomerPayload) {
    await apiClient.put(`/customers/${id}`, payload);
}

export async function updateCustomerStatus(id: number, version: number, status: 'ENABLED' | 'DISABLED') {
    await apiClient.put(`/customers/${id}/status`, {version, status});
}

export async function deleteCustomer(id: number, version: number) {
    await apiClient.delete(`/customers/${id}`, {params: {version}});
}

export async function fetchAgreementPrices(params: SalesPageParams) {
    return (await apiClient.get<PageData<AgreementPrice>>('/customer-agreement-prices', {params})).data;
}

export async function createAgreementPrice(payload: AgreementPricePayload) {
    return (await apiClient.post<number>('/customer-agreement-prices', payload)).data;
}

export async function updateAgreementPrice(id: number, payload: AgreementPricePayload) {
    await apiClient.put(`/customer-agreement-prices/${id}`, payload);
}

export async function deleteAgreementPrice(id: number, version: number) {
    await apiClient.delete(`/customer-agreement-prices/${id}`, {params: {version}});
}

export async function fetchSkuCatalog(params: ProductPageParams) {
    return (await apiClient.get<PageData<ProductSummary>>('/products', {params})).data;
}
