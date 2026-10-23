/* 查询条件本地记忆的存储键：前缀 + 用户 + 页面标识。 */

export const QUERY_FILTER_KEY_PREFIX = 'xsy-scm:query-filter';

export function queryFilterStorageKey(employeeId: string | number, pageKey: string): string {
  return `${QUERY_FILTER_KEY_PREFIX}:${employeeId}:${pageKey}`;
}
