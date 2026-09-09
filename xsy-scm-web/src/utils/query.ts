/** 去掉空值，避免把 undefined/null 序列化成 "undefined" 传给后端。 */
export function toQueryParams(params: Record<string, unknown>): Record<string, string | number | boolean> {
  const result: Record<string, string | number | boolean> = {};
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') {
      continue;
    }
    result[key] = value as string | number | boolean;
  }
  return result;
}
