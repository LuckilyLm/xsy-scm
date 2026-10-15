/*
 * 商城日期口径：一律按 Asia/Shanghai 计算，不跟随设备时区。
 *
 * 为什么不用 `new Date().toISOString().slice(0, 10)`：那取的是 **UTC** 日期。
 * 客户在中国时间凌晨（00:00–08:00）打开页面时，UTC 还停在前一天，
 * 日期选择器的「最小可选日期」会少一天，客户能选到昨天。
 * 小程序对 Intl 的支持不完整，所以这里用固定 +8 偏移自己算，不依赖 Intl。
 */

const SHANGHAI_OFFSET_MINUTES = 8 * 60;

/** 返回 Asia/Shanghai 的 yyyy-MM-dd */
export function shanghaiDate(offsetDays = 0) {
  const now = new Date();
  // getTimezoneOffset() 是「本地比 UTC 慢多少分钟」，东八区为 -480。
  // 先换算到 UTC，再加固定的 +8，得到与设备时区无关的上海时间。
  const shifted = new Date(now.getTime() + (SHANGHAI_OFFSET_MINUTES + now.getTimezoneOffset()) * 60 * 1000);
  shifted.setDate(shifted.getDate() + offsetDays);
  const year = shifted.getFullYear();
  const month = String(shifted.getMonth() + 1).padStart(2, '0');
  const day = String(shifted.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

const INSTANT_PATTERN = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?(Z|[+-]\d{2}:?\d{2})?$/;

/** 后端时间可能带 UTC 等不同时区偏移；商城统一换算为北京时间显示，不能直接截取时间字符串。 */
export function shanghaiDateTime(value) {
  if (!value) return '';
  const text = String(value);
  const match = text.match(INSTANT_PATTERN);
  // 认不出来（或只是纯日期）就原样截断，至少不把整串 ISO 丢给客户
  if (!match) return text.replace('T', ' ').slice(0, 16);
  const [, year, month, day, hour, minute, second, offset] = match;
  // 没有时区信息：按字面时间展示，不猜偏移
  if (!offset) return `${year}-${month}-${day} ${hour}:${minute}`;
  const literalAsUtc = Date.UTC(+year, +month - 1, +day, +hour, +minute, +(second || 0));
  const shanghai = new Date(literalAsUtc - offsetMinutesOf(offset) * 60 * 1000 + SHANGHAI_OFFSET_MINUTES * 60 * 1000);
  return `${shanghai.getUTCFullYear()}-${pad(shanghai.getUTCMonth() + 1)}-${pad(shanghai.getUTCDate())} ${pad(
    shanghai.getUTCHours()
  )}:${pad(shanghai.getUTCMinutes())}`;
}

/** {@code Z} 或 {@code ±HH:mm} / {@code ±HHmm} → 分钟数 */
function offsetMinutesOf(offset) {
  if (offset === 'Z') return 0;
  const sign = offset.startsWith('-') ? -1 : 1;
  const digits = offset.slice(1).replace(':', '');
  return sign * (Number(digits.slice(0, 2)) * 60 + Number(digits.slice(2, 4)));
}

function pad(value) {
  return String(value).padStart(2, '0');
}
