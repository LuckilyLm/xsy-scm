import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { AuthProvider, AUTH_QUERY_KEY } from '../auth/AuthProvider';
import type { CurrentUser } from '../types/auth';

/** 测试用当前用户，默认拥有全部业务管理权限。 */
export const TEST_USER: CurrentUser = {
  id: 1,
  username: 'tester',
  displayName: '测试账号',
  administrator: false,
  mustChangePassword: false,
  roles: ['tester'],
  permissions: [],
  version: 0,
};

interface RenderOptions {
  /** 覆盖当前用户权限列表，用于验证按钮级裁剪。 */
  permissions?: string[];
  /** 是否按超管处理（跳过前端裁剪）。 */
  administrator?: boolean;
}

/**
 * 渲染带 QueryClient + AuthProvider 的页面。
 *
 * 权限数据直接写入 QueryClient 缓存，`AuthProvider` 因此不会真的请求
 * `/api/auth/me`，`Permission` 在首帧即可判定，避免测试用例等待鉴权请求。
 */
export function renderWithProviders(ui: ReactElement, options: RenderOptions = {}) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  client.setQueryData(AUTH_QUERY_KEY, {
    ...TEST_USER,
    permissions: options.permissions ?? TEST_USER.permissions,
    administrator: options.administrator ?? false,
  } satisfies CurrentUser);

  return {
    client,
    ...render(
      <QueryClientProvider client={client}>
        <AuthProvider>{ui}</AuthProvider>
      </QueryClientProvider>,
    ),
  };
}

/** 业务页面常用权限集合，写测试时按需取用。 */
export const ALL_BUSINESS_PERMISSIONS = [
  'product.read',
  'product.manage',
  'customer.read',
  'customer.manage',
  'order.read',
  'order.manage',
  'supplier.read',
  'supplier.manage',
  'purchase.read',
  'purchase.manage',
  'inventory.read',
];
