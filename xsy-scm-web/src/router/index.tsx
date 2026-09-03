import { Navigate, createBrowserRouter } from 'react-router-dom';
import { AdminLayout } from '../layouts/AdminLayout';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AdminLayout />,
    children: [
      { index: true, element: <Navigate replace to="/products" /> },
      {
        path: 'products',
        lazy: async () => {
          const { ProductPage } = await import('../pages/product/ProductPage');
          return { Component: ProductPage };
        },
      },
    ],
  },
]);
