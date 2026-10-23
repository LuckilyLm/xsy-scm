/* deep-link 查询参数解析（纯函数，便于单测）。URL 可被手改，取值必须逐键过允许列表。 */

export type DeepLinkQuery = Record<string, string | (string | null)[] | null | undefined>;

/** 键 → 允许取值列表；`null` 表示自由文本（如单号），只做 trim 与长度约束，不校验取值。 */
export type DeepLinkSpec = Record<string, readonly string[] | null>;

/** 数组键取元素联合，`null` 自由文本键保留 string。 */
type DeepLinkValue<S> = S extends readonly (infer U extends string)[] ? U : string;

/** 同键多值时只取第一个非空值。 */
function firstValue(raw: DeepLinkQuery[string]): string {
  const value = Array.isArray(raw) ? raw.find((item) => item !== null) : raw;
  return typeof value === 'string' ? value.trim() : '';
}

export function deepLinkFilters<S extends DeepLinkSpec>(query: DeepLinkQuery, spec: S): {[K in keyof S]: DeepLinkValue<S[K]> | undefined} {
  const filters: Record<string, string | undefined> = {};
  for (const [key, allowed] of Object.entries(spec)) {
    const value = firstValue(query[key]);
    const accepted = value !== '' && (allowed === null || (allowed as readonly string[]).includes(value));
    filters[key] = accepted ? value : undefined;
  }
  return filters as {[K in keyof S]: DeepLinkValue<S[K]> | undefined};
}

/** 业务主键只接受纯数字串；返回字符串，避免大整数失真。 */
export function deepLinkId(query: DeepLinkQuery, key = 'id'): string | undefined {
  const value = firstValue(query[key]);
  return /^\d{1,19}$/.test(value) ? value : undefined;
}
