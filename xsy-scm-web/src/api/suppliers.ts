import {apiClient} from './http';

export interface SupplierMaster {
    id: number;
    supplierCode: string;
    name: string;
    status: 'ENABLED' | 'DISABLED';
    version: number;
}

export interface WarehouseMaster {
    id: number;
    warehouseCode: string;
    name: string;
    status: 'ENABLED' | 'DISABLED';
    address?: string;
    version: number;
}

export async function fetchSuppliers() {
    return (await apiClient.get<SupplierMaster[]>('/suppliers')).data;
}

export async function fetchWarehouses() {
    return (await apiClient.get<WarehouseMaster[]>('/warehouses')).data;
}
