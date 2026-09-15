/*
 * 供应商域错误码 → 用户可读文案
 *
 * 来源：**新写**（对齐 V2 W1 `product-errors.ts`）。
 *
 * 码值定义见后端 `SupplierErrorCode` / `ScmCommonErrorCode`。
 * 注意 40941 **已弃用**：legacy 用它表示「供应商 SKU 版本冲突」，V2 统一为 40921。
 */

export function supplierError(error: unknown): string {
  const response = error as { data?: { code?: number; msg?: string }; message?: string };
  const code = response?.data?.code;
  if (code === 40921) {
    return '数据已被其他人修改，请刷新后重试';
  }
  if (code === 40940) {
    return '供应商未启用，不能维护商品关联';
  }
  if (code === 40942) {
    return 'SKU 未启用或不存在（SPU 与 SKU 必须同时上架）';
  }
  if (code === 40943) {
    return '同一供应商下该商品规格已存在，或请求中的行重复';
  }
  if (code === 40944) {
    return '供应商编码已存在';
  }
  if (code === 40946) {
    return '该供应商与商品的关联已存在，请刷新后重试';
  }
  if (code === 40947) {
    return '该供应商已被商品关联引用，不能删除';
  }
  if (code === 40040) {
    return '默认采购员不存在，请重新选择';
  }
  if (code === 40442) {
    return '供应商商品配置不存在';
  }
  return response?.data?.msg || response?.message || '操作失败，请重试';
}
