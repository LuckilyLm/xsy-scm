/** 库存盘点支持草稿查询、修改、确认和取消。盘点确认会生成库存流水，前端不得用普通修改代替确认。 */
import {getRequest, postRequest, request, getDownload, type RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
  Id,
  InventoryStocktake,
  InventoryStocktakeAdd,
  InventoryStocktakeQuery,
  StocktakeImportResult,
} from '/@/views/business/scm/inventory/inventory-types';

// 同一份 Excel 文件重试复用同一幂等键；换文件即新命令。失败保留键允许重试，成功后清除。
const importKeys = new WeakMap<File, string>();

async function importStocktake(file: File): Promise<ScmResponse<StocktakeImportResult>> {
  const data = new FormData();
  data.append('file', file);
  let key = importKeys.get(file);
  if (!key) {
    key = crypto.randomUUID();
    importKeys.set(file, key);
  }
  const result = await request<StocktakeImportResult>({
    url: '/scm/inventory/stocktake/import',
    method: 'post',
    data,
    headers: {'Idempotency-Key': key},
  });
  if (result.code === 0) {
    importKeys.delete(file);
  }
  return result;
}

export const inventoryStocktakeApi = {
  query: (data: InventoryStocktakeQuery, options?: RequestOptions) =>
    postRequest<ScmPage<InventoryStocktake>>('/scm/inventory/stocktake/query', data, options),
  detail: (id: Id) => getRequest<InventoryStocktake>(`/scm/inventory/stocktake/detail/${id}`, {}),
  /** 新建草稿，返回新单 id。保存时记录当前账面量。 */
  create: (data: InventoryStocktakeAdd) => postRequest<Id>('/scm/inventory/stocktake/create', data),
  /** 改草稿（仅 DRAFT 可改）；会重新记录当前账面量。 */
  update: (id: Id, data: InventoryStocktakeAdd) => postRequest<string>(`/scm/inventory/stocktake/update/${id}`, data),
  /** 确认盘点：差异转盘盈 / 盘亏流水并调整余额。 */
  confirm: (id: Id) => postRequest<string>(`/scm/inventory/stocktake/confirm/${id}`, {}),
  /** 取消草稿（无库存影响）。 */
  cancel: (id: Id) => postRequest<string>(`/scm/inventory/stocktake/cancel/${id}`, {}),
  /** 删除草稿（逻辑删）。 */
  delete: (id: Id) => postRequest<string>(`/scm/inventory/stocktake/delete/${id}`, {}),
  /** 导出当前余额的盘点模板（带签名凭证）；用户只填实盘数，原行不能增删替换。 */
  downloadImportTemplate: (warehouseId: Id) => getDownload('/scm/inventory/stocktake/import/template', {warehouseId}),
  /** 导入 Excel，凭证校验通过后新建草稿（不写库存）。 */
  importStocktake,
};

export default inventoryStocktakeApi;
