/**
 * 金额展示：销售与商城金额统一两位小数。
 *
 * 服务端按 NUMERIC(18,4) 下发四位小数字符串，计算与提交仍保持四位；
 * 这里只做展示层舍入，按 HALF_UP 处理第三位小数，避免 Number() 的浮点误差。
 * 财务账本（钱包余额、应收应付、核销）不走这个函数，仍按四位原样展示。
 */
export function displayMoney(amount) {
  if (amount === null || amount === undefined || amount === '') return '—';
  const text = String(amount).trim();
  if (!/^-?\d+(\.\d+)?$/.test(text)) return text;
  const negative = text.startsWith('-');
  const [intPart, fracPart = ''] = text.replace(/^-/, '').split('.');
  // 超过 13 位整数已到 JS 安全整数边缘，宁可原样返回也不截断金额
  if (intPart.length > 13) return text;
  const digits = (fracPart + '0000').slice(0, 4);
  let cents = Number(intPart) * 100 + Number(digits.slice(0, 2));
  if (Number(digits.slice(2)) >= 50) cents += 1;
  const sign = negative && cents !== 0 ? '-' : '';
  return `${sign}${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, '0')}`;
}
