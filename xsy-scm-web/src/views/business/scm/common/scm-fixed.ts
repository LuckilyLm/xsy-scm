/**
 * SCM 域的**定点数字符串**公共工具（跨模块共享）。
 *
 * 为什么单独抽出来：库存、定价、营销等模块的表单里都有「数量 / 金额 / 比率」字段，
 * 而它们在后端**统一**由 `ScmStrictDecimalStringDeserializer` 接收 ——
 * 该反序列化器**拒绝 JSON 数字**，也**拒绝空串**。
 * 因此每个模块的 `*-form-model.ts` 都躲不开「InputNumber 的 number → 4 位定点字符串」这一步；
 * 放在任一个模块里都会让其它模块反向依赖（例如定价为了一个 `toFixed(4)` 去 import 库存域）。
 * 与 `scm-display.ts` 抽出来的理由完全相同，因此收敛到这里。
 *
 * **本模块不依赖 Vue、不发请求、不 import 任何东西**，因此可以被 `node --test` 直接加载。
 */

/**
 * 表单里的 `number` → 后端要求的 **4 位定点字符串**；不设该值时返回 `undefined`。
 *
 * `toFixed(4)` 对 `:precision="4"` 的控件是精确的：输入已经被限制在 4 位小数以内，
 * 不存在需要四舍五入的第五位。
 *
 * 调用方负责把 `undefined` 收口成后端要的形状：
 * - 可空字段（阈值上下限、结束时间）→ `?? null`；
 * - 必填字段（数量）→ 先判 `undefined` 再拦下，不要发空串。
 */
export function fixed4(value: number | null | undefined): string | undefined {
    return value === null || value === undefined ? undefined : value.toFixed(4);
}
