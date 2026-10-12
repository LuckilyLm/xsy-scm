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
