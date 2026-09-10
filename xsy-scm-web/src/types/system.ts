import type {PageData} from './product';

export type {PageData};

/** 与后端 ENABLED/DISABLED 状态枚举一致。 */
export type EnableStatus = 'ENABLED' | 'DISABLED';

export interface PageQuery {
    page?: number;
    pageSize?: number;
}

/** 启停与删除统一使用乐观锁版本号。 */
export interface VersionInput {
    version: number;
}

export interface StatusInput extends VersionInput {
    status: EnableStatus;
}

/* ---------------------------------- 部门 ---------------------------------- */

/** 与后端 `DepartmentResponse` 对齐。 */
export interface Department {
    id: number;
    parentId: number | null;
    code: string;
    name: string;
    sortOrder: number;
    status: EnableStatus;
    version: number;
    createdAt: string;
    updatedAt: string;
}

/** 与后端 `DepartmentTreeResponse` 对齐。 */
export interface DepartmentTreeNode {
    id: number;
    parentId: number | null;
    code: string;
    name: string;
    sortOrder: number;
    status: EnableStatus;
    version: number;
    children: DepartmentTreeNode[];
}

export interface DepartmentQuery extends PageQuery {
    keyword?: string;
    status?: EnableStatus;
    parentId?: number;
}

export interface DepartmentInput {
    code: string;
    name: string;
    parentId?: number | null;
    sortOrder: number;
}

/** 与后端 `UpdateDepartmentRequest` 对齐。 */
export interface UpdateDepartmentInput extends DepartmentInput {
    version: number;
}

/* ---------------------------------- 用户 ---------------------------------- */

/** 与后端 `UserResponse` 对齐；响应不含密码哈希、失败计数和锁定时间。 */
export interface SystemUser {
    id: number;
    username: string;
    displayName: string;
    departmentId: number | null;
    email: string | null;
    phone: string | null;
    status: EnableStatus;
    version: number;
    createdAt: string;
    updatedAt: string;
}

export interface UserQuery extends PageQuery {
    keyword?: string;
    status?: EnableStatus;
    departmentId?: number;
}

/** 与后端 `CreateUserRequest` 对齐；密码由后端策略校验（12—72 字节）。 */
export interface CreateUserInput {
    username: string;
    displayName: string;
    password: string;
    departmentId?: number | null;
    email?: string | null;
    phone?: string | null;
}

/** 与后端 `UpdateUserRequest` 对齐。 */
export interface UpdateUserInput {
    displayName: string;
    departmentId?: number | null;
    email?: string | null;
    phone?: string | null;
    version: number;
}

export interface ResetPasswordInput extends VersionInput {
    newPassword: string;
}

/* ---------------------------------- 角色 ---------------------------------- */

/** 与后端 `RoleResponse` 对齐。 */
export interface Role {
    id: number;
    roleCode: string;
    name: string;
    description: string | null;
    status: EnableStatus;
    systemRole: boolean;
    version: number;
    createdAt: string;
    updatedAt: string;
}

export interface RoleQuery extends PageQuery {
    keyword?: string;
    status?: EnableStatus;
}

export interface RoleInput {
    roleCode: string;
    name: string;
    description?: string | null;
}

/** 与后端 `UpdateRoleRequest` 对齐。 */
export interface UpdateRoleInput extends RoleInput {
    version: number;
}

/* ---------------------------------- 权限 ---------------------------------- */

export type PermissionResourceType = 'PAGE' | 'ACTION' | 'API';

/** 与后端 `PermissionResponse` 对齐。 */
export interface PermissionItem {
    id: number;
    permissionCode: string;
    name: string;
    type: PermissionResourceType;
    module: string;
    status: EnableStatus;
    systemPermission: boolean;
    version: number;
    createdAt: string;
    updatedAt: string;
}

export interface PermissionQuery extends PageQuery {
    keyword?: string;
    module?: string;
    type?: PermissionResourceType;
    status?: EnableStatus;
}

export interface PermissionInput {
    permissionCode: string;
    name: string;
    type: PermissionResourceType;
    module: string;
}

/** 与后端 `UpdatePermissionRequest` 对齐。 */
export interface UpdatePermissionInput extends PermissionInput {
    version: number;
}

/* ---------------------------------- 菜单 ---------------------------------- */

export type MenuType = 'DIRECTORY' | 'MENU';

/** 与后端 `MenuResponse` 对齐；`children` 只在树查询中返回。 */
export interface MenuNode {
    id: number;
    parentId: number | null;
    type: MenuType;
    name: string;
    routeKey: string | null;
    path: string | null;
    icon: string | null;
    requiredPermission: string | null;
    sort: number;
    visible: boolean;
    status: EnableStatus;
    version: number;
    children?: MenuNode[] | null;
}

export interface MenuQuery extends PageQuery {
    keyword?: string;
    type?: MenuType;
    status?: EnableStatus;
    parentId?: number;
    visible?: boolean;
}

export interface MenuInput {
    type: MenuType;
    parentId?: number | null;
    name: string;
    routeKey?: string | null;
    path?: string | null;
    icon?: string | null;
    requiredPermission?: string | null;
    sort: number;
    visible: boolean;
    status: EnableStatus;
}

/** 与后端 `UpdateMenuRequest` 对齐。 */
export interface UpdateMenuInput extends MenuInput {
    version: number;
}

/* --------------------------------- 授权关系 -------------------------------- */

/** 与后端 `UserRolesResponse.Role` 对齐。 */
export interface GrantRole {
    id: number;
    code: string;
    name: string;
    status: EnableStatus;
}

/** 与后端 `UserRolesResponse` 对齐。 */
export interface UserRolesGrant {
    userId: number;
    version: number;
    roles: GrantRole[];
}

/** 与后端 `RolePermissionsResponse.Permission` 对齐。 */
export interface GrantPermission {
    id: number;
    code: string;
    name: string;
    status: EnableStatus;
}

/** 与后端 `RolePermissionsResponse` 对齐。 */
export interface RolePermissionsGrant {
    roleId: number;
    version: number;
    permissions: GrantPermission[];
}

/** 与后端 `RoleMenusResponse.Menu` 对齐。 */
export interface GrantMenu {
    id: number;
    parentId: number | null;
    type: MenuType;
    name: string;
    status: EnableStatus;
    visible: boolean;
    requiredPermission: string | null;
}

/** 与后端 `RoleMenusResponse` 对齐。 */
export interface RoleMenusGrant {
    roleId: number;
    version: number;
    menus: GrantMenu[];
}

/** 三类授权均为 versioned 原子全量替换。 */
export interface ReplaceGrantInput {
    roleIds?: number[];
    permissionIds?: number[];
    menuIds?: number[];
    version: number;
}

export type SystemPage<T> = PageData<T>;
