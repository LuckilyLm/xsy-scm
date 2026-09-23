/*
 * 待办卡片 / 站内消息 deep-link 的 URL 查询参数解析（纯函数，零运行时依赖，便于单测）。
 *
 * URL 是可由用户手改、可被收藏转发的输入，因此取值必须逐键过白名单：白名单外的值按
 * 「未提供」处理，绝不把 `?status=任意串` 透传给后端查询接口。
 *
 * 解析结果对**每个声明的键都给出值**（命中为白名单值，否则 `undefined`），调用方据此整体
 * 覆盖筛选表单 —— 这样才能满足「从普通菜单进入时不残留上一次 deep-link 条件」：
 * 菜单跳转没有 query，于是所有键回落 `undefined`，即页面默认（未筛选）。
 */

export type DeepLinkQuery = Record<string, string | (string | null)[] | null | undefined>;

/** 键 → 允许取值列表；`null` 表示自由文本（如单号），只做 trim 与长度约束，不校验取值。 */
export type DeepLinkSpec<T extends string> = Record<string, readonly T[] | null>;

/** `?k=a&k=b` 会解析成数组：只取第一个非空值，不做「多值 OR」语义。 */
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

/**
 * deep-link 里的业务主键：只接受纯数字串，其他值（含空、负数、`1e5`、超长串）按未提供处理。
 *
 * 返回字符串而不是 `Number`：主键参与的是 URL 与接口路径，转成数字会让超出安全整数范围的 id
 * 静默失真并跳到另一张单据。
 */
export function deepLinkId(query: DeepLinkQuery, key = 'id'): string | undefined {
  const value = firstValue(query[key]);
  return /^\d{1,19}$/.test(value) ? value : undefined;
}
