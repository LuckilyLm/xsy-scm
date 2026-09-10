import {apiClient} from '../http';
import {toQueryParams} from '../../utils/query';
import type {
    Department,
    DepartmentInput,
    DepartmentQuery,
    DepartmentTreeNode,
    EnableStatus,
    PageData,
    UpdateDepartmentInput,
} from '../../types/system';

export function fetchDepartments(query: DepartmentQuery) {
    return apiClient
        .get<PageData<Department>>('/system/departments', {params: toQueryParams(query)})
        .then((response) => response.data);
}

export function fetchDepartmentTree() {
    return apiClient
        .get<DepartmentTreeNode[]>('/system/departments/tree')
        .then((response) => response.data);
}

export function createDepartment(payload: DepartmentInput) {
    return apiClient
        .post<Department>('/system/departments', payload)
        .then((response) => response.data);
}

export function updateDepartment(id: number, payload: UpdateDepartmentInput) {
    return apiClient
        .put<Department>(`/system/departments/${id}`, payload)
        .then((response) => response.data);
}

export function changeDepartmentStatus(id: number, status: EnableStatus, version: number) {
    return apiClient
        .post<Department>(`/system/departments/${id}/status`, {status, version})
        .then((response) => response.data);
}

export function deleteDepartment(id: number, version: number) {
    return apiClient.delete<void>(`/system/departments/${id}`, {params: {version}});
}
