/** 与后端 `com.xianshuyuan.scm.auth.dto.CurrentUserResponse` 对齐。 */
export interface CurrentUser {
    id: number;
    username: string;
    displayName: string;
    administrator: boolean;
    mustChangePassword: boolean;
    roles: string[];
    permissions: string[];
    version: number;
}

/** 与后端 `GET /api/auth/csrf` 响应对齐。 */
export interface CsrfInfo {
    headerName: string;
    parameterName: string;
}

export interface LoginPayload {
    username: string;
    password: string;
}

export interface ChangePasswordPayload {
    currentPassword: string;
    newPassword: string;
    version: number;
}

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous' | 'error';
