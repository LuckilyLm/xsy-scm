import {apiClient} from '../http';
import {toQueryParams} from '../../utils/query';
import type {
    CreateUserInput,
    EnableStatus,
    PageData,
    ResetPasswordInput,
    SystemUser,
    UpdateUserInput,
    UserQuery,
} from '../../types/system';

export function fetchUsers(query: UserQuery) {
    return apiClient
        .get<PageData<SystemUser>>('/system/users', {params: toQueryParams(query)})
        .then((response) => response.data);
}

export function createUser(payload: CreateUserInput) {
    return apiClient.post<SystemUser>('/system/users', payload).then((response) => response.data);
}

export function updateUser(id: number, payload: UpdateUserInput) {
    return apiClient
        .put<SystemUser>(`/system/users/${id}`, payload)
        .then((response) => response.data);
}

export function changeUserStatus(id: number, status: EnableStatus, version: number) {
    return apiClient
        .post<SystemUser>(`/system/users/${id}/status`, {status, version})
        .then((response) => response.data);
}

/** 管理员重置密码：后端生成一次性凭证并强制该用户下次登录改密。 */
export function resetUserPassword(id: number, payload: ResetPasswordInput) {
    return apiClient.post<void>(`/system/users/${id}/reset-password`, payload);
}

export function deleteUser(id: number, version: number) {
    return apiClient.delete<void>(`/system/users/${id}`, {params: {version}});
}
