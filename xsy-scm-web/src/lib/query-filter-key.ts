/*
 * 查询条件本地记忆的存储键构造器（纯函数，零运行时依赖，便于单测）。
 *
 * 沿用仓库既有约定（订单草稿 `xsy-scm:order-draft:${employeeId}`、导出列
 * `xsy-scm:export-columns:${employeeId}:${scene}`）：前缀 + 登录用户 + 页面标识，
 * 保证不同用户、不同页面互不串味。第一版只放浏览器本地，不落业务表。
 */

export const QUERY_FILTER_KEY_PREFIX = 'xsy-scm:query-filter';

export function queryFilterStorageKey(employeeId: string | number, pageKey: string): string {
  return `${QUERY_FILTER_KEY_PREFIX}:${employeeId}:${pageKey}`;
}
