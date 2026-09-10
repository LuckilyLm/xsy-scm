import {ApiError} from '../../api/http';
import type {DepartmentTreeNode, MenuNode} from '../../types/system';

/** 状态选项。`Radio.Group`/`Select` 的 `options` 需要可变数组，故此处不使用 `as const`。 */
export const STATUS_OPTIONS: { value: 'ENABLED' | 'DISABLED'; label: string }[] = [
    {value: 'ENABLED', label: '启用'},
    {value: 'DISABLED', label: '停用'},
];

/** ProTable 查询下拉用的枚举，直接给明确的索引类型，避免推导成联合数组。 */
export const STATUS_ENUM: Record<string, { text: string }> = {
    ENABLED: {text: '启用'},
    DISABLED: {text: '停用'},
};

export const PERMISSION_TYPE_ENUM: Record<string, { text: string }> = {
    API: {text: 'API'},
    PAGE: {text: '页面'},
    ACTION: {text: '按钮'},
};

/** 树选择选项，供部门/菜单的 TreeSelect 使用。 */
export interface TreeOption {
    value: number;
    title: string;
    children?: TreeOption[];
}

/** 统一取错误文案：优先后端业务消息，网络异常给出可操作提示。 */
export function describeError(error: unknown, fallback: string): string {
    if (error instanceof ApiError) {
        return error.message || fallback;
    }
    return fallback;
}

/** 部门树转 TreeSelect/Tree 数据源；`excludeIds` 用于编辑时排除自身及后代，防止成环。 */
export function toDepartmentTreeData(
    nodes: DepartmentTreeNode[],
    excludeIds?: Set<number>,
): TreeOption[] {
    return nodes
        .filter((node) => !excludeIds?.has(node.id))
        .map((node) => ({
            value: node.id,
            title: node.status === 'DISABLED' ? `${node.name}（停用）` : node.name,
            children: toDepartmentTreeData(node.children ?? [], excludeIds),
        }));
}

/** 收集某个节点及其全部后代 ID。 */
export function collectDescendantIds(node: DepartmentTreeNode): number[] {
    const result: number[] = [node.id];
    for (const child of node.children ?? []) {
        result.push(...collectDescendantIds(child));
    }
    return result;
}

/** 菜单树转 TreeSelect 数据源；同样支持排除自身及后代。 */
export function toMenuTreeData(
    nodes: MenuNode[],
    excludeIds?: Set<number>,
): TreeOption[] {
    return nodes
        .filter((node) => !excludeIds?.has(node.id))
        .map((node) => ({
            value: node.id,
            title: node.status === 'DISABLED' ? `${node.name}（停用）` : node.name,
            children: toMenuTreeData(node.children ?? [], excludeIds),
        }));
}

export function collectMenuIds(node: MenuNode): number[] {
    const result: number[] = [node.id];
    for (const child of node.children ?? []) {
        result.push(...collectMenuIds(child));
    }
    return result;
}
