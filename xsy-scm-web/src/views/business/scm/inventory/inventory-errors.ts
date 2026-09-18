/**
 * W6 库存域错误码 → 可执行的中文提示（新增文件，仿 W5 `purchase-errors.ts`）。
 *
 * 取向与 W4 / W5 一致：**提示要说「下一步做什么」，不是复述错误码名字**。
 * 库存域只有 4 个新码，但每一个都对应一个用户可采取的动作：
 *
 * - `41001` 不是「单位不一致」，而是「这个仓库+SKU 已经按 X 记账了，请用同一个单位入库」——
 *   因为 Q13 明确不做单位换算，用户唯一的出路是改采购单位（或换仓库）。
 * - `41002` 不是「重复入库」，而是「这张收货单已经入过库了，请刷新」——
 *   它属于**不可能发生的数据异常**，看到它就说明需要人工排查，而不是重试。
 *
 * 未登记的码回落到后端的 `msg`，再回落到通用文案；**不吞掉后端消息**。
 * 跨域复用的码（40485 仓库不存在、40921 版本冲突、40000 参数不合法）也登记在这里，
 * 因为库存页确实会遇到它们 —— 让用户看到一个裸的错误码是更糟的选择。
 */
type BusinessError = { code?: number; msg?: string; message?: string };

const MESSAGES: Record<number, string> = {
  // ---- 库存域（W6 新增的 4 个码） ----
  40486: '库存余额不存在或已被删除，请刷新后重试',
  41001: '该仓库与 SKU 的库存记账单位与本次入库单位不一致，库存不做自动换算：请改用一致的采购单位，或改入其它仓库',
  41002: '该来源单据已入库，不能重复入库。若确认这是异常，请联系管理员核对库存流水',
  41003: '库存入库事实不合法（数量、单位、发生时刻或操作者缺失），请联系管理员',

  // ---- 跨域复用的既有码（库存页会遇到） ----
  40485: '仓库不存在或已被删除',
  40921: '数据已被其它操作修改，请刷新后重试',
  40000: '请求参数不正确，请检查筛选条件后重试',
};

export function inventoryError(error: unknown): string {
  const raw = error as BusinessError & { data?: BusinessError; response?: { data?: BusinessError } };
  const e = raw?.response?.data ?? raw?.data ?? raw;
  const code = e?.code;
  if (typeof code === 'number' && MESSAGES[code]) {
    return MESSAGES[code];
  }
  return e?.msg ?? e?.message ?? '操作失败，请重试';
}

export default inventoryError;
