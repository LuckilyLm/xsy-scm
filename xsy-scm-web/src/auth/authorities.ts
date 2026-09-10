/**
 * 后端 Spring Security 使用的权威权限编码。
 *
 * 前端只用它做导航裁剪和按钮显隐，真正的访问控制始终由后端 `AuthorityRules.hasAuthority`
 * 和 `@PreAuthorize` 判定；隐藏按钮不等于拒绝请求。
 */
export const AUTHORITIES = {
  /* 业务模块 */
  productRead: 'product.read',
  productManage: 'product.manage',
  customerRead: 'customer.read',
  customerManage: 'customer.manage',
  orderRead: 'order.read',
  orderManage: 'order.manage',
  supplierRead: 'supplier.read',
  supplierManage: 'supplier.manage',
  purchaseRead: 'purchase.read',
  purchaseManage: 'purchase.manage',
  inventoryRead: 'inventory.read',

  /* 部门 */
  departmentList: 'system:department:list',
  departmentCreate: 'system:department:create',
  departmentUpdate: 'system:department:update',
  departmentStatus: 'system:department:status',
  departmentDelete: 'system:department:delete',

  /* 用户 */
  userList: 'system:user:list',
  userCreate: 'system:user:create',
  userUpdate: 'system:user:update',
  userStatus: 'system:user:status',
  userDelete: 'system:user:delete',
  userResetPassword: 'system:user:reset-password',
  userAssignRoles: 'system:user:assign-roles',

  /* 角色 */
  roleList: 'system:role:list',
  roleCreate: 'system:role:create',
  roleUpdate: 'system:role:update',
  roleStatus: 'system:role:status',
  roleDelete: 'system:role:delete',
  roleAssignPermissions: 'system:role:assign-permissions',
  roleAssignMenus: 'system:role:assign-menus',

  /* 菜单 */
  menuList: 'system:menu:list',
  menuCreate: 'system:menu:create',
  menuUpdate: 'system:menu:update',
  menuStatus: 'system:menu:status',
  menuDelete: 'system:menu:delete',

  /* 审计 */
  loginLogList: 'system:login-log:list',
  operationLogList: 'system:operation-log:list',

  /* 权限 */
  permissionList: 'system:permission:list',
  permissionCreate: 'system:permission:create',
  permissionUpdate: 'system:permission:update',
  permissionStatus: 'system:permission:status',
  permissionDelete: 'system:permission:delete',
} as const;
