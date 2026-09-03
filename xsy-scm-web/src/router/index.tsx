import { Navigate, createBrowserRouter } from 'react-router-dom';
import { AdminLayout } from '../layouts/AdminLayout';
import { PlaceholderPage } from '../pages/PlaceholderPage';

const placeholder = (title: string) => ({ Component: () => <PlaceholderPage title={title} /> });
export const router = createBrowserRouter([
  { path: '/', element: <AdminLayout />, children: [
    { index: true, element: <Navigate replace to="/products" /> },
    { path: 'products', hydrateFallbackElement: <div aria-busy="true">商品档案加载中…</div>, lazy: async () => { const { ProductPage } = await import('../pages/product/ProductPage'); return { Component: ProductPage }; } },
    { path: 'customers', lazy: async () => placeholder('客户档案') },
    { path: 'customer-agreement-prices', lazy: async () => placeholder('客户协议价') },
    { path: 'orders', lazy: async () => placeholder('销售订单') },
    { path: 'order-returns', lazy: async () => placeholder('退货申请') },
    { path: 'order-refunds', lazy: async () => placeholder('退款记录') },
  ] },
]);
