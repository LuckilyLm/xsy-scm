import type { PageData } from './product';

/** 与后端 ENABLED/DISABLED 状态枚举一致。 */
export type EnableStatus = 'ENABLED' | 'DISABLED';

export interface PageQuery {
  page?: number;
  pageSize?: number;
}

/* ---------------------------------- 部门 ---------------------------------- */

export interface Department {
  id: number;
  parentId?: number | null;
  code: string;
  name: string;
  sortOrder: number;
  status: EnableStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface DepartmentTreeNode {
  id: number;
  parentId?: number | null;
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

/* ---------------------------------- 用户 ---------------------------------- */

export interface SystemUser {
  id: number;
  username: string;
  displayName: string;
  departmentId?: number | null;
  email?: string | null;
  phone?: string | null;
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

export interface CreateUserInput {
  username: string;
  displayName: string;
  /** 后端要求 12—72 字节且包含大小写、数字与符号。 */
  password: string;
  departmentId?: number | null;
  email?: string | null;
  phone?: string | null;
}

export interface UpdateUserInput {
  displayName: string;
  departmentId?: number | null;
  email?: string | null;
  phone?: string | null;
  version: number;
}

/* ---------------------------------- 角色 ---------------------------------- */

export interface Role {
  id: number;
  roleCode: string;
  name: string;
  description?: string | null;
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

/* ---------------------------------- 权限 ---------------------------------- */

export type PermissionResourceType = 'PAGE' | 'ACTION' | 'API';

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

/* ---------------------------------- 菜单 ---------------------------------- */

export type MenuType = 'DIRECTORY' | 'MENU';

export interface MenuNode {
  id: number;
  parentId?: number | null;
  type: MenuType;
  name: string;
  routeKey?: string | null;
  path?: string | null;
  icon?: string | null;
  requiredPermission?: string | null;
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

/* --------------------------------- 授权关系 -------------------------------- */

export interface GrantRole {
  id: number;
  code: string;
  name: string;
  status: EnableStatus;
}

export interface UserRolesGrant {
  userId: number;
  version: number;
  roles: GrantRole[];
}

export interface GrantPermission {
  id: number;
  code: string;
  name: string;
  status: EnableStatus;
}

export interface RolePermissionsGrant {
  roleId: number;
  version: number;
  permissions: GrantPermission[];
}

export interface GrantMenu {
  id: number;
  parentId?: number | null;
  type: MenuType;
  name: string;
  status: EnableStatus;
  visible: boolean;
  requiredPermission?: string | null;
}

export interface RoleMenusGrant {
  roleId: number;
  version: number;
  menus: GrantMenu[];
}

export interface ReplaceGrantInput {
  roleIds?: number[];
  permissionIds?: number[];
  menuIds?: number[];
  version: number;
}

export type SystemPage<T> = PageData<T>;
