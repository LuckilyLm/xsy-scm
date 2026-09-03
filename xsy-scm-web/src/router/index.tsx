import { Navigate, createBrowserRouter } from 'react-router-dom';
import { AdminLayout } from '../layouts/AdminLayout';

function ProductPlaceholder() {
  return <div>商品档案</div>;
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AdminLayout />,
    children: [
      { index: true, element: <Navigate replace to="/products" /> },
      { path: 'products', element: <ProductPlaceholder /> },
    ],
  },
]);
