import {apiClient} from './http';
import type {ProductCategoryInput, ProductCategoryTreeNode} from '../types/product';

export function fetchCategoryTree() {
    return apiClient.get<ProductCategoryTreeNode[]>('/product-categories/tree').then((response) => response.data);
}

export function createCategory(payload: ProductCategoryInput) {
    return apiClient.post<number>('/product-categories', payload).then((response) => response.data);
}

export function updateCategory(id: number, payload: ProductCategoryInput) {
    return apiClient.put<void>(`/product-categories/${id}`, payload);
}

export function deleteCategory(id: number) {
    return apiClient.delete<void>(`/product-categories/${id}`);
}
