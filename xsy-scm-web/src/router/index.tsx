import { Result, Skeleton } from 'antd';
import { Navigate, createBrowserRouter } from 'react-router-dom';
import { RequireAuth, RequirePermission } from '../auth/RequireAuth';
import { AdminLayout } from '../layouts/AdminLayout';
import { useNavigation } from '../layouts/AdminLayout/navigation';
import { LoginPage } from '../pages/auth/LoginPage';
import { NotFoundPage } from '../pages/error/NotFoundPage';
import { ROUTE_REGISTRY } from './routeRegistry';

/** 懒加载占位，避免路由切换时出现空白。 */
function RouteLoading() {
  return <Skeleton active loading style={{ padding: 24 }} />;
}

/** 登录后落到第一个可访问页面；没有任何授权菜单时给出明确状态，不回退显示全量菜单。 */
function HomeRedirect() {
  const navigation = useNavigation();
  if (navigation.length === 0) {
    return (
      <Result
        status="info"
        title="暂无可访问功能"
        subTitle="当前账号没有被授予任何后台菜单，请联系管理员分配角色或菜单权限。"
      />
    );
  }
  return <Navigate replace to={navigation[0].path} />;
}

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AdminLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <HomeRedirect /> },
      ...ROUTE_REGISTRY.map((route) => ({
        path: route.path,
        hydrateFallbackElement: <RouteLoading />,
        lazy: async () => {
          const { Component } = await route.lazy();
          return {
            element: (
              <RequirePermission authority={route.permission}>
                <Component />
              </RequirePermission>
            ),
          };
        },
      })),
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
