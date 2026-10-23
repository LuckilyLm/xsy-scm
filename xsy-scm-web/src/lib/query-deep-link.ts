/* deep-link 查询参数解析（纯函数，便于单测）。URL 可被手改，取值必须逐键过允许列表。 */

export type DeepLinkQuery = Record<string, string | (string | null)[] | null | undefined>;

/** 键 → 允许取值列表；`null` 表示自由文本（如单号），只做 trim 与长度约束，不校验取值。 */
export type DeepLinkSpec<T extends string> = Record<string, readonly T[] | null>;

/** 同键多值时只取第一个非空值。 */
function firstValue(raw: DeepLinkQuery[string]): string {
  const value = Array.isArray(raw) ? raw.find((item) => item !== null) : raw;
  return typeof value === 'string' ? value.trim() : '';
}

export function deepLinkFilters<T extends string>(query: DeepLinkQuery, spec: DeepLinkSpec<T>): Record<string, T | undefined> {
  const filters: Record<string, T | undefined> = {};
  for (const [key, allowed] of Object.entries(spec)) {
    const value = firstValue(query[key]);
    const accepted = value !== '' && (allowed === null || (allowed as readonly string[]).includes(value));
    filters[key] = accepted ? (value as T) : undefined;
  }
  return filters;
}

/** 业务主键只接受纯数字串；返回字符串，避免大整数失真。 */
export function deepLinkId(query: DeepLinkQuery, key = 'id'): string | undefined {
  const value = firstValue(query[key]);
  return /^\d{1,19}$/.test(value) ? value : undefined;
}
