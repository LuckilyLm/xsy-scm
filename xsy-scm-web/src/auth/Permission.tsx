import type { ReactNode } from 'react';
import { useAnyPermission } from './usePermission';

interface PermissionProps {
  /** 单个权限编码；留空表示所有已登录用户可见。 */
  authority?: string | null;
  /** 多个权限取“或”关系。 */
  anyOf?: string[];
  children: ReactNode;
  /** 无权限时的占位内容，默认不渲染。 */
  fallback?: ReactNode;
}

/**
 * 按钮/操作级权限容器。无权限时默认不渲染危险操作，
 * 需要解释原因时可传入 fallback（例如 disabled + tooltip），但仍不能调用 API。
 */
export function Permission({ authority, anyOf, children, fallback = null }: PermissionProps) {
  const permitted = useAnyPermission(anyOf ?? (authority ? [authority] : []));
  return <>{permitted ? children : fallback}</>;
}
