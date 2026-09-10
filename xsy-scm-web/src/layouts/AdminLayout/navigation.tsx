import {useMemo} from 'react';
import type {ReactNode} from 'react';
import {useAuth} from '../../auth/AuthProvider';
import {NAVIGATION_GROUPS, ROUTE_REGISTRY} from '../../router/routeRegistry';
import type {RouteDefinition} from '../../router/routeRegistry';

export interface SecondaryNavigationItem {
    label: string;
    path: string;
}

export interface PrimaryNavigationItem {
    key: string;
    label: string;
    /** 一级入口默认落到本组第一个可访问页面。 */
    path: string;
    icon: ReactNode;
    children: SecondaryNavigationItem[];
}

/**
 * 由受控注册表按当前用户权限派生双级导航。
 *
 * 菜单为空时不回退显示全部静态菜单，而是返回空数组，由布局展示“暂无可访问功能”。
 * 超级管理员不做前端裁剪，直接看到注册表全部页面。
 */
export function buildNavigation(
    routes: RouteDefinition[],
    hasPermission: (authority: string | null) => boolean,
): PrimaryNavigationItem[] {
    const result: PrimaryNavigationItem[] = [];
    for (const group of NAVIGATION_GROUPS) {
        const children = routes
            .filter((route) => route.group === group.key && hasPermission(route.permission))
            .map<SecondaryNavigationItem>((route) => ({
                label: route.title,
                path: `/${route.path}`,
            }));
        if (children.length === 0) {
            continue;
        }
        result.push({
            key: group.key,
            label: group.label,
            path: children[0].path,
            icon: group.icon,
            children,
        });
    }
    return result;
}

/** 按“最长子路径前缀”判定当前一级分组，避免 /orders 与 /order-returns 互相误判。 */
export function resolveActiveGroup(
    navigation: PrimaryNavigationItem[],
    pathname: string,
): PrimaryNavigationItem | null {
    let best: PrimaryNavigationItem | null = null;
    let bestScore = -1;
    for (const item of navigation) {
        for (const child of item.children) {
            const matched =
                pathname === child.path || pathname.startsWith(`${child.path}/`);
            if (matched && child.path.length > bestScore) {
                best = item;
                bestScore = child.path.length;
            }
        }
    }
    return best;
}

export function useNavigation() {
    const {hasPermission} = useAuth();
    return useMemo(() => buildNavigation(ROUTE_REGISTRY, hasPermission), [hasPermission]);
}
