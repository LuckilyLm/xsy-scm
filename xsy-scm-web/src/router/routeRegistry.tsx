import {
  AppstoreOutlined,
  InboxOutlined,
  SettingOutlined,
  ShopOutlined,
  ShoppingCartOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import type { ComponentType, ReactNode } from 'react';
import { AUTHORITIES } from '../auth/authorities';

/**
 * 受控路由注册表。
 *
 * 组件只能由本文件静态引入，数据库菜单或后端响应都不得指定可执行的模块路径，
 * 这是 Sprint 4 计划要求的动态路由安全边界。后端权限编码仍是唯一安全控制，
 * 这里的 `permission` 只用于导航裁剪和直达 URL 的友好拒绝。
 */

export interface NavigationGroup {
  key: string;
  label: string;
  icon: ReactNode;
}

/** 一级导航分组，与后端菜单的 DIRECTORY 概念对应。 */
export const NAVIGATION_GROUPS: NavigationGroup[] = [
  { key: 'products', label: '商品', icon: <ShopOutlined /> },
  { key: 'orders', label: '订单', icon: <InboxOutlined /> },
  { key: 'purchases', label: '采购', icon: <ShoppingCartOutlined /> },
  { key: 'warehouses', label: '库房', icon: <AppstoreOutlined /> },
  { key: 'customers', label: '客户', icon: <TeamOutlined /> },
  { key: 'system', label: '系统', icon: <SettingOutlined /> },
];

export interface RouteDefinition {
  /** 稳定的路由键，与后端菜单 `route_key` 使用同一套取值。 */
  key: string;
  /** 相对 AdminLayout 的路径，不带前导斜杠。 */
  path: string;
  title: string;
  /** 进入页面所需权限；null 表示登录即可访问。 */
  permission: string | null;
  /** 一级导航分组；null 表示详情/编辑页，不出现在侧栏。 */
  group: string | null;
  lazy: () => Promise<{ Component: ComponentType }>;
}

function page<K extends string>(
  loader: () => Promise<Record<K, ComponentType>>,
  exportName: K,
): () => Promise<{ Component: ComponentType }> {
  return async () => {
    const module = await loader();
    return { Component: module[exportName] };
  };
}

export const ROUTE_REGISTRY: RouteDefinition[] = [
  /* --------------------------------- 商品 --------------------------------- */
  {
    key: 'products',
    path: 'products',
    title: '商品档案',
    permission: AUTHORITIES.productRead,
    group: 'products',
    lazy: page(() => import('../pages/product/ProductPage'), 'ProductPage'),
  },
  {
    key: 'product-categories',
    path: 'products/categories',
    title: '商品分类',
    permission: AUTHORITIES.productRead,
    group: 'products',
    lazy: page(() => import('../pages/product/ProductCategoryPage'), 'ProductCategoryPage'),
  },

  /* --------------------------------- 订单 --------------------------------- */
  {
    key: 'orders',
    path: 'orders',
    title: '销售订单',
    permission: AUTHORITIES.orderRead,
    group: 'orders',
    lazy: page(() => import('../pages/order/OrderListPage'), 'OrderListPage'),
  },
  {
    key: 'order-returns',
    path: 'order-returns',
    title: '退货申请',
    permission: AUTHORITIES.orderRead,
    group: 'orders',
    lazy: page(() => import('../pages/order/ReturnListPage'), 'ReturnListPage'),
  },
  {
    key: 'order-refunds',
    path: 'order-refunds',
    title: '退款记录',
    permission: AUTHORITIES.orderRead,
    group: 'orders',
    lazy: page(() => import('../pages/order/RefundListPage'), 'RefundListPage'),
  },
  {
    key: 'orders-new',
    path: 'orders/new',
    title: '新增销售订单',
    permission: AUTHORITIES.orderManage,
    group: null,
    lazy: page(() => import('../pages/order/OrderEditorPage'), 'OrderEditorPage'),
  },
  {
    key: 'orders-edit',
    path: 'orders/:id/edit',
    title: '编辑销售订单',
    permission: AUTHORITIES.orderManage,
    group: null,
    lazy: page(() => import('../pages/order/OrderEditorPage'), 'OrderEditorPage'),
  },
  {
    key: 'orders-detail',
    path: 'orders/:id',
    title: '销售订单详情',
    permission: AUTHORITIES.orderRead,
    group: null,
    lazy: page(() => import('../pages/order/OrderDetailPage'), 'OrderDetailPage'),
  },
  {
    key: 'order-returns-new',
    path: 'order-returns/new',
    title: '新增退货申请',
    permission: AUTHORITIES.orderManage,
    group: null,
    lazy: page(() => import('../pages/order/ReturnCreatePage'), 'ReturnCreatePage'),
  },
  {
    key: 'order-returns-detail',
    path: 'order-returns/:id',
    title: '退货详情',
    permission: AUTHORITIES.orderRead,
    group: null,
    lazy: page(() => import('../pages/order/ReturnDetailPage'), 'ReturnDetailPage'),
  },
  {
    key: 'order-refunds-detail',
    path: 'order-refunds/:id',
    title: '退款详情',
    permission: AUTHORITIES.orderRead,
    group: null,
    lazy: page(() => import('../pages/order/RefundDetailPage'), 'RefundDetailPage'),
  },

  /* --------------------------------- 采购 --------------------------------- */
  {
    key: 'purchase-demands',
    path: 'purchases/demands',
    title: '采购需求',
    permission: AUTHORITIES.purchaseRead,
    group: 'purchases',
    lazy: page(() => import('../pages/purchase/PurchaseDemandPage'), 'PurchaseDemandPage'),
  },
  {
    key: 'purchase-orders',
    path: 'purchases/orders',
    title: '采购订单',
    permission: AUTHORITIES.purchaseRead,
    group: 'purchases',
    lazy: page(() => import('../pages/purchase/PurchaseOrderListPage'), 'PurchaseOrderListPage'),
  },
  {
    key: 'purchase-receipts',
    path: 'purchases/receipts',
    title: '收货管理',
    permission: AUTHORITIES.purchaseRead,
    group: 'purchases',
    lazy: page(() => import('../pages/purchase/PurchaseReceiptListPage'), 'PurchaseReceiptListPage'),
  },
  {
    key: 'purchase-orders-new',
    path: 'purchases/orders/new',
    title: '新增采购订单',
    permission: AUTHORITIES.purchaseManage,
    group: null,
    lazy: page(() => import('../pages/purchase/PurchaseOrderEditorPage'), 'PurchaseOrderEditorPage'),
  },
  {
    key: 'purchase-orders-edit',
    path: 'purchases/orders/:id/edit',
    title: '编辑采购订单',
    permission: AUTHORITIES.purchaseManage,
    group: null,
    lazy: page(() => import('../pages/purchase/PurchaseOrderEditorPage'), 'PurchaseOrderEditorPage'),
  },
  {
    key: 'purchase-orders-detail',
    path: 'purchases/orders/:id',
    title: '采购订单详情',
    permission: AUTHORITIES.purchaseRead,
    group: null,
    lazy: page(() => import('../pages/purchase/PurchaseOrderDetailPage'), 'PurchaseOrderDetailPage'),
  },
  {
    key: 'purchase-receipt-entry',
    path: 'purchases/orders/:id/receipts',
    title: '采购收货',
    permission: AUTHORITIES.purchaseManage,
    group: null,
    lazy: page(() => import('../pages/purchase/PurchaseReceiptEntryPage'), 'PurchaseReceiptEntryPage'),
  },
  {
    key: 'purchase-receipts-detail',
    path: 'purchases/receipts/:id',
    title: '收货单详情',
    permission: AUTHORITIES.purchaseRead,
    group: null,
    lazy: page(() => import('../pages/purchase/PurchaseReceiptPage'), 'PurchaseReceiptPage'),
  },

  /* --------------------------------- 库房 --------------------------------- */
  {
    key: 'suppliers',
    path: 'warehouses/suppliers',
    title: '供应商',
    permission: AUTHORITIES.supplierRead,
    group: 'warehouses',
    lazy: page(() => import('../pages/supplier/SupplierPage'), 'SupplierPage'),
  },
  {
    key: 'warehouses',
    path: 'warehouses/settings',
    title: '仓库设置',
    permission: AUTHORITIES.supplierRead,
    group: 'warehouses',
    lazy: page(() => import('../pages/supplier/WarehousePage'), 'WarehousePage'),
  },
  {
    key: 'inventories',
    path: 'warehouses/inventories',
    title: '库存余额',
    permission: AUTHORITIES.inventoryRead,
    group: 'warehouses',
    lazy: page(() => import('../pages/inventory/InventoryPage'), 'InventoryPage'),
  },
  {
    key: 'inventory-movements',
    path: 'warehouses/inventory-movements',
    title: '库存流水',
    permission: AUTHORITIES.inventoryRead,
    group: 'warehouses',
    lazy: page(() => import('../pages/inventory/InventoryMovementPage'), 'InventoryMovementPage'),
  },

  /* --------------------------------- 客户 --------------------------------- */
  {
    key: 'customers',
    path: 'customers',
    title: '客户档案',
    permission: AUTHORITIES.customerRead,
    group: 'customers',
    lazy: page(() => import('../pages/customer/CustomerPage'), 'CustomerPage'),
  },
  {
    key: 'customer-types',
    path: 'customers/types',
    title: '客户类型',
    permission: AUTHORITIES.customerRead,
    group: 'customers',
    lazy: page(() => import('../pages/customer/CustomerTypePage'), 'CustomerTypePage'),
  },
  {
    key: 'customer-agreement-prices',
    path: 'customer-agreement-prices',
    title: '协议价',
    permission: AUTHORITIES.customerRead,
    group: 'customers',
    lazy: page(() => import('../pages/customer/AgreementPricePage'), 'AgreementPricePage'),
  },

  /* --------------------------------- 系统 --------------------------------- */
  {
    key: 'system-departments',
    path: 'system/departments',
    title: '部门管理',
    permission: AUTHORITIES.departmentList,
    group: 'system',
    lazy: page(() => import('../pages/system/DepartmentPage'), 'DepartmentPage'),
  },
  {
    key: 'system-users',
    path: 'system/users',
    title: '用户管理',
    permission: AUTHORITIES.userList,
    group: 'system',
    lazy: page(() => import('../pages/system/UserPage'), 'UserPage'),
  },
  {
    key: 'system-roles',
    path: 'system/roles',
    title: '角色管理',
    permission: AUTHORITIES.roleList,
    group: 'system',
    lazy: page(() => import('../pages/system/RolePage'), 'RolePage'),
  },
  {
    key: 'system-menus',
    path: 'system/menus',
    title: '菜单管理',
    permission: AUTHORITIES.menuList,
    group: 'system',
    lazy: page(() => import('../pages/system/MenuPage'), 'MenuPage'),
  },
  {
    key: 'system-permissions',
    path: 'system/permissions',
    title: '权限管理',
    permission: AUTHORITIES.permissionList,
    group: 'system',
    lazy: page(() => import('../pages/system/PermissionPage'), 'PermissionPage'),
  },
];
