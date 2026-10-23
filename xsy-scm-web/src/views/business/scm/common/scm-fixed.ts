/** 把数字控件的值转成四位小数字符串；空值返回 undefined。 */
export function fixed4(value: number | null | undefined): string | undefined {
  return value === null || value === undefined ? undefined : value.toFixed(4);
}
