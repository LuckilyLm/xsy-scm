import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {createContext, useCallback, useContext, useEffect, useMemo} from 'react';
import type {ReactNode} from 'react';
import {changePassword, fetchCsrf, fetchCurrentUser, login as loginRequest, logout as logoutRequest} from '../api/auth';
import {ApiError, resetUnauthorizedSignal, setUnauthorizedListener} from '../api/http';
import type {AuthStatus, ChangePasswordPayload, CurrentUser, LoginPayload} from '../types/auth';

export const AUTH_QUERY_KEY = ['auth', 'me'] as const;

interface AuthContextValue {
    status: AuthStatus;
    user: CurrentUser | null;
    permissions: string[];
    isAdministrator: boolean;
    hasPermission: (authority?: string | null) => boolean;
    hasAnyPermission: (authorities: string[]) => boolean;
    login: (payload: LoginPayload) => Promise<CurrentUser>;
    logout: () => Promise<void>;
    changeOwnPassword: (payload: ChangePasswordPayload) => Promise<void>;
    refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function isUnauthorized(error: unknown): boolean {
    return error instanceof ApiError && error.status === 401;
}

export function AuthProvider({children}: { children: ReactNode }) {
    const queryClient = useQueryClient();

    const query = useQuery({
        queryKey: AUTH_QUERY_KEY,
        queryFn: fetchCurrentUser,
        retry: false,
        staleTime: 5 * 60_000,
        refetchOnWindowFocus: false,
    });

    // 应用启动先建立 CSRF cookie，保证后续写请求能带上 X-XSRF-TOKEN。
    useEffect(() => {
        void fetchCsrf().catch(() => undefined);
    }, []);

    useEffect(() => {
        setUnauthorizedListener(() => {
            // 会话失效后只清理内存状态，由 RequireAuth 决定是否跳转登录。
            queryClient.setQueryData(AUTH_QUERY_KEY, null);
        });
        return () => setUnauthorizedListener(null);
    }, [queryClient]);

    const clearSession = useCallback(() => {
        queryClient.setQueryData(AUTH_QUERY_KEY, null);
    }, [queryClient]);

    const loginMutation = useMutation({
        mutationFn: (payload: LoginPayload) => loginRequest(payload),
        onSuccess: (user) => {
            resetUnauthorizedSignal();
            queryClient.setQueryData(AUTH_QUERY_KEY, user);
        },
    });

    const logoutMutation = useMutation({
        mutationFn: () => logoutRequest(),
        onSettled: () => {
            clearSession();
            resetUnauthorizedSignal();
            void fetchCsrf().catch(() => undefined);
        },
    });

    const changePasswordMutation = useMutation({
        mutationFn: (payload: ChangePasswordPayload) => changePassword(payload),
    });

    const value = useMemo<AuthContextValue>(() => {
        const user = query.data ?? null;
        const permissions = user?.permissions ?? [];
        const isAdministrator = user?.administrator === true;
        const status: AuthStatus = query.isPending
            ? 'loading'
            : user
                ? 'authenticated'
                : query.error && !isUnauthorized(query.error)
                    ? 'error'
                    : 'anonymous';

        return {
            status,
            user,
            permissions,
            isAdministrator,
            // 超级管理员跳过前端裁剪；后端 @PreAuthorize 仍是唯一安全边界。
            hasPermission: (authority) => !authority || isAdministrator || permissions.includes(authority),
            hasAnyPermission: (authorities) =>
                authorities.length === 0 || isAdministrator || authorities.some((item) => permissions.includes(item)),
            login: loginMutation.mutateAsync,
            logout: logoutMutation.mutateAsync,
            changeOwnPassword: changePasswordMutation.mutateAsync,
            refresh: async () => {
                await query.refetch();
            },
        };
    }, [
        changePasswordMutation.mutateAsync,
        loginMutation.mutateAsync,
        logoutMutation.mutateAsync,
        query,
    ]);

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth 必须在 AuthProvider 内使用');
    }
    return context;
}
