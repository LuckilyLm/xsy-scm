/**
 * 库存盘点单接口（盘点波次新增文件）。
 *
 * 与后端 `InventoryStocktakeController` 逐端点对应（7 个）：
 * query / detail / create / update / confirm / cancel / delete。
 *
 * **权限分两档**：录实盘数与改草稿是日常操作（`add` / `update`），
 * **`confirm` 是独立权限** —— 它会真实调整库存并写不可逆流水，
 * 允许仓管录数、由主管确认是完全合理的分工，因此不复用 `update`。与出库单同一取向。
 *
 * `confirm` 失败时整单回滚，不存在「盘一半」；重复确认会被状态机拒绝（41020）。
 */
import { getRequest, postRequest } from '/@/lib/axios';
import type { ScmPage, ScmResponse } from '/@/types/business/scm/customer';
import type {
  Id,
  InventoryStocktake,
  InventoryStocktakeAdd,
  InventoryStocktakeQuery,
} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryStocktakeApi = {
  query: (data: InventoryStocktakeQuery) =>
    postRequest('/scm/inventory/stocktake/query', data) as unknown as Promise<
      ScmResponse<ScmPage<InventoryStocktake>>
    >,
  detail: (id: Id) =>
    getRequest(`/scm/inventory/stocktake/detail/${id}`, {}) as unknown as Promise<
      ScmResponse<InventoryStocktake>
    >,
  /** 新建草稿，返回新单 id。保存时会从余额行快照账面量。 */
  create: (data: InventoryStocktakeAdd) =>
    postRequest('/scm/inventory/stocktake/create', data) as unknown as Promise<ScmResponse<Id>>,
  /** 改草稿（仅 DRAFT 可改）；会**重新快照账面量**。 */
  update: (id: Id, data: InventoryStocktakeAdd) =>
    postRequest(`/scm/inventory/stocktake/update/${id}`, data) as unknown as Promise<ScmResponse<string>>,
  /** 确认盘点：差异转盘盈 / 盘亏流水并调整余额。 */
  confirm: (id: Id) =>
    postRequest(`/scm/inventory/stocktake/confirm/${id}`, {}) as unknown as Promise<ScmResponse<string>>,
  /** 取消草稿（无库存影响）。 */
  cancel: (id: Id) =>
    postRequest(`/scm/inventory/stocktake/cancel/${id}`, {}) as unknown as Promise<ScmResponse<string>>,
  /** 删除草稿（逻辑删）。 */
  delete: (id: Id) =>
    postRequest(`/scm/inventory/stocktake/delete/${id}`, {}) as unknown as Promise<ScmResponse<string>>,
};

export default inventoryStocktakeApi;
