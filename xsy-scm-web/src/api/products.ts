import { apiClient } from './http';
import type {
  PageData,
  ProductCategoryTreeNode,
  ProductDetail,
  ProductPageParams,
  ProductPayload,
  ProductSummary,
  ShelfStatus,
} from '../types/product';

export async function fetchProducts(params: ProductPageParams) {
  const response = await apiClient.get<PageData<ProductSummary>>('/products', { params });
  return response.data;
}

export async function fetchProduct(id: number) {
  const response = await apiClient.get<ProductDetail>(`/products/${id}`);
  return response.data;
}

export async function createProduct(payload: ProductPayload) {
  const response = await apiClient.post<number>('/products', payload);
  return response.data;
}

export async function updateProduct(id: number, payload: ProductPayload) {
  await apiClient.put(`/products/${id}`, payload);
}

export async function updateProductStatus(id: number, version: number, status: ShelfStatus) {
  await apiClient.put(`/products/${id}/status`, { version, status });
}

export async function deleteProduct(id: number, version: number) {
  await apiClient.delete(`/products/${id}`, { params: { version } });
}

export async function fetchCategoryTree() {
  const response = await apiClient.get<ProductCategoryTreeNode[]>('/product-categories/tree');
  return response.data;
}
