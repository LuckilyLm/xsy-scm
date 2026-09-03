import { Navigate, createBrowserRouter } from 'react-router-dom';
import { AdminLayout } from '../layouts/AdminLayout';
import { ProductPage } from '../pages/product/ProductPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AdminLayout />,
    children: [
      { index: true, element: <Navigate replace to="/products" /> },
      { path: 'products', element: <ProductPage /> },
    ],
  },
]);
