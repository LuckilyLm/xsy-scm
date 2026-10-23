/** SCM 金额和数量由前端数字控件转为四位小数字符串。公共工具不依赖 Vue 或网络，方便独立测试。 */

/** 前端数字控件值提交前转为四位小数字符串。空值由调用方按必填或可选字段处理，不发送非法空字符串。 */
export function fixed4(value: number | null | undefined): string | undefined {
  return value === null || value === undefined ? undefined : value.toFixed(4);
}
