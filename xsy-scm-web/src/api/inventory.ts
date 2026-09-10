import {apiClient} from './http';
import type {InventoryBalance, InventoryMovement} from '../types/inventory';

export async function fetchInventories(params?: { warehouseId?: number; skuId?: number }) {
    return (await apiClient.get<InventoryBalance[]>('/inventories', {params})).data;
}

export async function fetchInventoryMovements(params?: { warehouseId?: number; skuId?: number; receiptId?: number }) {
    return (await apiClient.get<InventoryMovement[]>('/inventory-movements', {params})).data;
}
