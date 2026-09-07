import { Navigate, createBrowserRouter } from 'react-router-dom';
import { AdminLayout } from '../layouts/AdminLayout';

export const router = createBrowserRouter([
  { path: '/', element: <AdminLayout />, children: [
    { index: true, element: <Navigate replace to="/products" /> },
    { path: 'products', hydrateFallbackElement: <div aria-busy="true">商品档案加载中…</div>, lazy: async () => { const { ProductPage } = await import('../pages/product/ProductPage'); return { Component: ProductPage }; } },
    { path: 'customers', hydrateFallbackElement: <div aria-busy="true">客户档案加载中…</div>, lazy: async () => { const { CustomerPage } = await import('../pages/customer/CustomerPage'); return { Component: CustomerPage }; } },
    { path: 'customer-agreement-prices', hydrateFallbackElement: <div aria-busy="true">客户协议价加载中…</div>, lazy: async () => { const { AgreementPricePage } = await import('../pages/customer/AgreementPricePage'); return { Component: AgreementPricePage }; } },
    { path: 'orders', hydrateFallbackElement: <div aria-busy="true">销售订单加载中…</div>, lazy: async () => { const { OrderListPage } = await import('../pages/order/OrderListPage'); return { Component: OrderListPage }; } },
    { path: 'orders/new', lazy: async () => { const { OrderEditorPage } = await import('../pages/order/OrderEditorPage'); return { Component: OrderEditorPage }; } },
    { path: 'orders/:id/edit', lazy: async () => { const { OrderEditorPage } = await import('../pages/order/OrderEditorPage'); return { Component: OrderEditorPage }; } },
    { path: 'orders/:id', lazy: async () => { const { OrderDetailPage } = await import('../pages/order/OrderDetailPage'); return { Component: OrderDetailPage }; } },
    { path: 'order-returns', lazy: async () => { const { ReturnListPage } = await import('../pages/order/ReturnListPage'); return { Component: ReturnListPage }; } },
    { path: 'order-returns/new', lazy: async () => { const { ReturnCreatePage } = await import('../pages/order/ReturnCreatePage'); return { Component: ReturnCreatePage }; } },
    { path: 'order-returns/:id', lazy: async () => { const { ReturnDetailPage } = await import('../pages/order/ReturnDetailPage'); return { Component: ReturnDetailPage }; } },
    { path: 'order-refunds', lazy: async () => { const { RefundListPage } = await import('../pages/order/RefundListPage'); return { Component: RefundListPage }; } },
    { path: 'order-refunds/:id', lazy: async () => { const { RefundDetailPage } = await import('../pages/order/RefundDetailPage'); return { Component: RefundDetailPage }; } },
    { path: 'purchases', element: <Navigate replace to="/purchases/demands" /> },
    { path: 'purchases/demands', lazy: async () => { const { PurchaseDemandPage } = await import('../pages/purchase/PurchaseDemandPage'); return { Component: PurchaseDemandPage }; } },
    { path: 'purchases/orders', lazy: async () => { const { PurchaseOrderListPage } = await import('../pages/purchase/PurchaseOrderListPage'); return { Component: PurchaseOrderListPage }; } },
    { path: 'purchases/orders/new', lazy: async () => { const { PurchaseOrderEditorPage } = await import('../pages/purchase/PurchaseOrderEditorPage'); return { Component: PurchaseOrderEditorPage }; } },
    { path: 'purchases/orders/:id/edit', lazy: async () => { const { PurchaseOrderEditorPage } = await import('../pages/purchase/PurchaseOrderEditorPage'); return { Component: PurchaseOrderEditorPage }; } },
    { path: 'purchases/orders/:id', lazy: async () => { const { PurchaseOrderDetailPage } = await import('../pages/purchase/PurchaseOrderDetailPage'); return { Component: PurchaseOrderDetailPage }; } },
    { path: 'purchases/orders/:id/receipts', lazy: async () => { const { PurchaseReceiptEntryPage } = await import('../pages/purchase/PurchaseReceiptEntryPage'); return { Component: PurchaseReceiptEntryPage }; } },
    { path: 'purchases/receipts', lazy: async () => { const { PurchaseReceiptListPage } = await import('../pages/purchase/PurchaseReceiptListPage'); return { Component: PurchaseReceiptListPage }; } },
    { path: 'purchases/receipts/:id', lazy: async () => { const { PurchaseReceiptPage } = await import('../pages/purchase/PurchaseReceiptPage'); return { Component: PurchaseReceiptPage }; } },
    { path: 'warehouses', element: <Navigate replace to="/warehouses/inventories" /> },
    { path: 'warehouses/suppliers', lazy: async () => { const { SupplierPage } = await import('../pages/supplier/SupplierPage'); return { Component: SupplierPage }; } },
    { path: 'warehouses/settings', lazy: async () => { const { WarehousePage } = await import('../pages/supplier/WarehousePage'); return { Component: WarehousePage }; } },
    { path: 'warehouses/inventories', lazy: async () => { const { InventoryPage } = await import('../pages/inventory/InventoryPage'); return { Component: InventoryPage }; } },
    { path: 'warehouses/inventory-movements', lazy: async () => { const { InventoryMovementPage } = await import('../pages/inventory/InventoryMovementPage'); return { Component: InventoryMovementPage }; } },
  ] },
]);
