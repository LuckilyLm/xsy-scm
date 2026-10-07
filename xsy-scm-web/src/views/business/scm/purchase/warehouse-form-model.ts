/*
 * 仓库写请求体构造（纯函数，可被 `node --test` 直接单测）。
 *
 * UI 表单模型带只读的 `warehouseCode` 回显，写请求不该带它 —— 编码由服务端生成，
 * 后端 `WarehouseAddForm` / `WarehouseUpdateForm` 也都没有这个字段。
 */
import type {WarehouseFormModel, WarehousePayload} from './purchase-types';

/** 表单模型 → 写请求体：摘掉只读回显的编码，其余原样。 */
export function toWarehousePayload(form: WarehouseFormModel): WarehousePayload {
    const {warehouseCode, ...payload} = form;
    return payload;
}
