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
    { path: 'orders', hydrateFallbackElement: <div aria-busy="true">销售订单加载中…</div>, lazy: async () => { const { OrderListPage } = await import('../pages/order/OrderListPage'); return { Component: OrderListPage }; } },
    { path: 'orders/new', lazy: async () => { const { OrderEditorPage } = await import('../pages/order/OrderEditorPage'); return { Component: OrderEditorPage }; } },
    { path: 'orders/:id/edit', lazy: async () => { const { OrderEditorPage } = await import('../pages/order/OrderEditorPage'); return { Component: OrderEditorPage }; } },
    { path: 'orders/:id', lazy: async () => { const { OrderDetailPage } = await import('../pages/order/OrderDetailPage'); return { Component: OrderDetailPage }; } },
    { path: 'order-returns', lazy: async () => { const { ReturnListPage } = await import('../pages/order/ReturnListPage'); return { Component: ReturnListPage }; } },
    { path: 'order-returns/new', lazy: async () => { const { ReturnCreatePage } = await import('../pages/order/ReturnCreatePage'); return { Component: ReturnCreatePage }; } },
    { path: 'order-returns/:id', lazy: async () => { const { ReturnDetailPage } = await import('../pages/order/ReturnDetailPage'); return { Component: ReturnDetailPage }; } },
    { path: 'order-refunds', lazy: async () => { const { RefundListPage } = await import('../pages/order/RefundListPage'); return { Component: RefundListPage }; } },
    { path: 'order-refunds/:id', lazy: async () => { const { RefundDetailPage } = await import('../pages/order/RefundDetailPage'); return { Component: RefundDetailPage }; } },
  ] },
]);
