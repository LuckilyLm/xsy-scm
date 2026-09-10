/**
 * 去掉空值，避免把 undefined/null 序列化成 "undefined" 传给后端。
 *
 * 用泛型而不是 `Record<string, unknown>` 收参：业务查询入参是接口而非索引签名类型，
 * 直接要求 `Record<string, unknown>` 会被 TS 拒绝（缺少索引签名）。
 */
export function toQueryParams<T extends object>(
  params: T,
): Record<string, string | number | boolean> {
  const result: Record<string, string | number | boolean> = {};
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') {
      continue;
    }
    result[key] = value as string | number | boolean;
  }
  return result;
}
