/*
 * 操作日志脱敏：递归遍历请求参数 / 返回结果，把命中的敏感字段值替换为占位符。
 *
 * 详情弹窗直接渲染后端回传的原始 JSON，历史数据里可能含 password / token 等明文，
 * 展示前必须脱敏。这里只做纯函数处理，不改写后端存储。
 */

const MASKED_VALUE = '******';

const SECRET_KEY_PATTERN =
  /(password|passwd|pwd|token|secret|credential|authorization|accessToken|refreshToken|sessionToken|apiKey|api_key)/i;

export function maskSensitive<T>(input: T): T {
  if (Array.isArray(input)) {
    return input.map((item) => maskSensitive(item)) as unknown as T;
  }
  if (input !== null && typeof input === 'object') {
    const result: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(input as Record<string, unknown>)) {
      result[key] = SECRET_KEY_PATTERN.test(key) ? MASKED_VALUE : maskSensitive(value);
    }
    return result as T;
  }
  return input;
}
