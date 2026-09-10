import {useAuth} from './AuthProvider';

/**
 * 判断当前用户是否拥有某个权限。
 * 仅用于界面裁剪，后端 @PreAuthorize 才是安全边界。
 */
export function usePermission(authority?: string | null): boolean {
    const {hasPermission} = useAuth();
    return hasPermission(authority);
}

/** 任一权限满足即可，常用于“查看或管理”组合。 */
export function useAnyPermission(authorities: string[]): boolean {
    const {hasAnyPermission} = useAuth();
    return hasAnyPermission(authorities);
}
