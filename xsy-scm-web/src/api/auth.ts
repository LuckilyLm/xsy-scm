import type { ChangePasswordPayload, CsrfInfo, CurrentUser, LoginPayload } from '../types/auth';
import { apiClient, resetUnauthorizedSignal } from './http';

/** 触发后端生成 CSRF token 并写入 `XSRF-TOKEN` cookie；应用启动与登录后调用。 */
export async function fetchCsrf(): Promise<CsrfInfo> {
  return (await apiClient.get<CsrfInfo>('/auth/csrf')).data;
}

export async function login(payload: LoginPayload): Promise<CurrentUser> {
  const user = (await apiClient.post<CurrentUser>('/auth/login', payload)).data;
  // 登录成功后会话轮换，CSRF token 也随之轮换。
  resetUnauthorizedSignal();
  await refreshCsrfToken();
  return user;
}

export async function fetchCurrentUser(): Promise<CurrentUser> {
  return (await apiClient.get<CurrentUser>('/auth/me')).data;
}

export async function logout(): Promise<void> {
  await apiClient.post<void>('/auth/logout');
  await refreshCsrfToken();
}

export async function changePassword(payload: ChangePasswordPayload): Promise<void> {
  await apiClient.post<void>('/auth/change-password', payload);
}

/** CSRF 刷新失败不阻断当前流程，后续写操作会由后端返回明确错误。 */
async function refreshCsrfToken(): Promise<void> {
  try {
    await fetchCsrf();
  } catch {
    // 忽略：由具体请求的 CSRF 错误负责提示。
  }
}
