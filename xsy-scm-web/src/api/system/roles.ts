import {apiClient} from '../http';
import {toQueryParams} from '../../utils/query';
import type {
    EnableStatus,
    PageData,
    Role,
    RoleInput,
    RoleQuery,
    UpdateRoleInput,
} from '../../types/system';

export function fetchRoles(query: RoleQuery) {
    return apiClient
        .get<PageData<Role>>('/system/roles', {params: toQueryParams(query)})
        .then((response) => response.data);
}

export function createRole(payload: RoleInput) {
    return apiClient.post<Role>('/system/roles', payload).then((response) => response.data);
}

export function updateRole(id: number, payload: UpdateRoleInput) {
    return apiClient.put<Role>(`/system/roles/${id}`, payload).then((response) => response.data);
}

export function changeRoleStatus(id: number, status: EnableStatus, version: number) {
    return apiClient
        .post<Role>(`/system/roles/${id}/status`, {status, version})
        .then((response) => response.data);
}

export function deleteRole(id: number, version: number) {
    return apiClient.delete<void>(`/system/roles/${id}`, {params: {version}});
}
