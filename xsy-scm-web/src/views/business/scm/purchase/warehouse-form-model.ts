/* 仓库写请求体构造。表单里的 warehouseCode 只用于回显，编码由服务端生成，不提交。 */
import type {WarehouseFormModel, WarehousePayload} from './purchase-types';

/** 表单模型 → 写请求体：摘掉只读回显的编码，其余原样。 */
export function toWarehousePayload(form: WarehouseFormModel): WarehousePayload {
  const {warehouseCode, ...payload} = form;
  return payload;
}
