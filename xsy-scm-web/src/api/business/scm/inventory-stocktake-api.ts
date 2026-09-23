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
import {getRequest, postRequest, request, getDownload} from '/@/lib/axios';
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
    const result = (await request({
        url: '/scm/inventory/stocktake/import',
        method: 'post',
        data,
        headers: {'Idempotency-Key': key},
    })) as unknown as ScmResponse<StocktakeImportResult>;
    if (result.code === 0) {
        importKeys.delete(file);
    }
    return result;
}

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
    /**
     * 导出某仓库当前余额的实时快照模板（带签名凭证的 xlsx）。
     * 用户只填实盘数与备注，来源行由凭证锁定，不得增删替换。
     */
    downloadImportTemplate: (warehouseId: Id) =>
        getDownload('/scm/inventory/stocktake/import/template', {warehouseId}),
    /** 导入填好的 Excel，签名快照校验通过后创建新 DRAFT（不写库存）。 */
    importStocktake,
};

export default inventoryStocktakeApi;
