/**
 * 错误提示文本的兜底。
 *
 * 后端未处理异常在非生产环境会把 `java.lang.NullPointerException: …` 塞进 `msg`；
 * 这类原文只该进日志，界面统一换成可读提示。
 */
const FALLBACK_ERROR_TEXT = '系统开小差了，请稍后重试';
const RAW_EXCEPTION_TEXT = /^(?:java|javax|jakarta|sun|org|com)\.[\w.$]*(?:Exception|Error)\b/;

export function errorText(msg?: string | null): string {
  const text = typeof msg === 'string' ? msg.trim() : '';
  return !text || RAW_EXCEPTION_TEXT.test(text) ? FALLBACK_ERROR_TEXT : text;
}
