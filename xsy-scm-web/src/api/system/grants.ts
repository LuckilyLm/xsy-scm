import { apiClient } from '../http';
import type {
  RoleMenusGrant,
  RolePermissionsGrant,
  UserRolesGrant,
} from '../../types/system';

/* ------------------------------ 用户 — 角色 ------------------------------ */

export function fetchUserRoles(userId: number) {
  return apiClient
    .get<UserRolesGrant>(`/system/users/${userId}/roles`)
    .then((response) => response.data);
}

/** 全量替换，后端按 version 原子校验，最多 100 项。 */
export function replaceUserRoles(userId: number, roleIds: number[], version: number) {
  return apiClient
    .post<UserRolesGrant>(`/system/users/${userId}/roles`, { roleIds, version })
    .then((response) => response.data);
}

/* ------------------------------ 角色 — 权限 ------------------------------ */

export function fetchRolePermissions(roleId: number) {
  return apiClient
    .get<RolePermissionsGrant>(`/system/roles/${roleId}/permissions`)
    .then((response) => response.data);
}

export function replaceRolePermissions(roleId: number, permissionIds: number[], version: number) {
  return apiClient
    .post<RolePermissionsGrant>(`/system/roles/${roleId}/permissions`, { permissionIds, version })
    .then((response) => response.data);
}

/* ------------------------------ 角色 — 菜单 ------------------------------ */

export function fetchRoleMenus(roleId: number) {
  return apiClient
    .get<RoleMenusGrant>(`/system/roles/${roleId}/menus`)
    .then((response) => response.data);
}

export function replaceRoleMenus(roleId: number, menuIds: number[], version: number) {
  return apiClient
    .post<RoleMenusGrant>(`/system/roles/${roleId}/menus`, { menuIds, version })
    .then((response) => response.data);
}
