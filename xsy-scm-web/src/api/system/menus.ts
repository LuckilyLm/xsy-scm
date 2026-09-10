import {apiClient} from '../http';
import {toQueryParams} from '../../utils/query';
import type {
    EnableStatus,
    MenuInput,
    MenuNode,
    MenuQuery,
    PageData,
    UpdateMenuInput,
} from '../../types/system';

export function fetchMenus(query: MenuQuery) {
    return apiClient
        .get<PageData<MenuNode>>('/system/menus', {params: toQueryParams(query)})
        .then((response) => response.data);
}

/** 菜单树用于父级选择与授权勾选；后端会校验深度并过滤不匹配的节点。 */
export function fetchMenuTree(params?: { status?: EnableStatus; visible?: boolean }) {
    return apiClient
        .get<MenuNode[]>('/system/menus/tree', {params: toQueryParams(params ?? {})})
        .then((response) => response.data);
}

export function createMenu(payload: MenuInput) {
    return apiClient.post<MenuNode>('/system/menus', payload).then((response) => response.data);
}

export function updateMenu(id: number, payload: UpdateMenuInput) {
    return apiClient.put<MenuNode>(`/system/menus/${id}`, payload).then((response) => response.data);
}

export function changeMenuStatus(id: number, status: EnableStatus, version: number) {
    return apiClient
        .post<MenuNode>(`/system/menus/${id}/status`, {status, version})
        .then((response) => response.data);
}

export function deleteMenu(id: number, version: number) {
    return apiClient.delete<void>(`/system/menus/${id}`, {params: {version}});
}
