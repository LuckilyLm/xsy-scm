import { Result, Skeleton } from 'antd';
import { Navigate, useLocation } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from './AuthProvider';
import { useAnyPermission } from './usePermission';

/** 未登录跳转登录页并记住原目标；网络异常保留会话并提供重试。 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { status, refresh } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return <Skeleton active loading style={{ padding: 24 }} />;
  }
  if (status === 'anonymous') {
    return <Navigate replace state={{ from: location.pathname + location.search }} to="/login" />;
  }
  if (status === 'error') {
    return (
      <Result
        status="warning"
        title="无法获取登录状态"
        subTitle="网络异常或服务不可用，请重试。当前会话未被清除。"
        extra={
          <button onClick={() => void refresh()} type="button">
            重试
          </button>
        }
      />
    );
  }
  return <>{children}</>;
}

interface RequirePermissionProps {
  authority?: string | null;
  anyOf?: string[];
  children: ReactNode;
}

/** 页面/路由级权限守卫：直达未授权 URL 时展示 403，而不是隐藏菜单即可绕过。 */
export function RequirePermission({ authority, anyOf, children }: RequirePermissionProps) {
  const permitted = useAnyPermission(anyOf ?? (authority ? [authority] : []));
  if (!permitted) {
    return <Result status={403} title="403" subTitle="你没有访问该功能的权限，请联系管理员。" />;
  }
  return <>{children}</>;
}
