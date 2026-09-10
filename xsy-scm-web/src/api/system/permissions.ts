import {apiClient} from '../http';
import {toQueryParams} from '../../utils/query';
import type {
    EnableStatus,
    PageData,
    PermissionInput,
    PermissionItem,
    PermissionQuery,
    UpdatePermissionInput,
} from '../../types/system';

export function fetchPermissions(query: PermissionQuery) {
    return apiClient
        .get<PageData<PermissionItem>>('/system/permissions', {params: toQueryParams(query)})
        .then((response) => response.data);
}

export function createPermission(payload: PermissionInput) {
    return apiClient
        .post<PermissionItem>('/system/permissions', payload)
        .then((response) => response.data);
}

export function updatePermission(id: number, payload: UpdatePermissionInput) {
    return apiClient
        .put<PermissionItem>(`/system/permissions/${id}`, payload)
        .then((response) => response.data);
}

export function changePermissionStatus(id: number, status: EnableStatus, version: number) {
    return apiClient
        .post<PermissionItem>(`/system/permissions/${id}/status`, {status, version})
        .then((response) => response.data);
}

export function deletePermission(id: number, version: number) {
    return apiClient.delete<void>(`/system/permissions/${id}`, {params: {version}});
}
