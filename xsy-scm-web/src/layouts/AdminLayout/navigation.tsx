import {
  AppstoreOutlined, BarChartOutlined, DollarOutlined, HomeOutlined, InboxOutlined,
  ShopOutlined, ShoppingCartOutlined, TeamOutlined,
} from '@ant-design/icons';
import type { ReactNode } from 'react';

export interface NavigationItem { label: string; path: string; icon: ReactNode; }
export interface SecondaryNavigationItem { label: string; path: string; }
export const primaryNavigation: NavigationItem[] = [
  { label: '首页', path: '/', icon: <HomeOutlined /> }, { label: '商品', path: '/products', icon: <ShopOutlined /> },
  { label: '订单', path: '/orders', icon: <InboxOutlined /> }, { label: '采购', path: '/purchases', icon: <ShoppingCartOutlined /> },
  { label: '库房', path: '/warehouses', icon: <AppstoreOutlined /> }, { label: '客户', path: '/customers', icon: <TeamOutlined /> },
  { label: '财务', path: '/finance', icon: <DollarOutlined /> }, { label: '报表', path: '/reports', icon: <BarChartOutlined /> },
];
export const secondaryNavigation: Record<string, SecondaryNavigationItem[]> = {
  products: [{ label: '商品档案', path: '/products' }, { label: '商品分类', path: '/product-categories' }],
  customers: [{ label: '客户档案', path: '/customers' }, { label: '协议价', path: '/customer-agreement-prices' }],
  orders: [{ label: '销售订单', path: '/orders' }, { label: '退货申请', path: '/order-returns' }, { label: '退款记录', path: '/order-refunds' }],
  purchases: [{ label: '采购需求', path: '/purchases/demands' }, { label: '采购订单', path: '/purchases/orders' }, { label: '收货管理', path: '/purchases/receipts' }],
  warehouses: [{ label: '供应商', path: '/warehouses/suppliers' }, { label: '仓库设置', path: '/warehouses/settings' }, { label: '库存余额', path: '/warehouses/inventories' }, { label: '库存流水', path: '/warehouses/inventory-movements' }],
};
