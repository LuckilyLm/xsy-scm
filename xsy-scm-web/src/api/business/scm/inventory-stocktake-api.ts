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
    query: (data: InventoryStocktakeQuery, options?: RequestOptions) =>
        postRequest('/scm/inventory/stocktake/query', data, options) as unknown as Promise<
            ScmResponse<ScmPage<InventoryStocktake>>
        >,
    detail: (id: Id) =>
        getRequest(`/scm/inventory/stocktake/detail/${id}`, {}) as unknown as Promise<
            ScmResponse<InventoryStocktake>
        >,
    /** 新建草稿，返回新单 id。保存时会从余额行快照账面量。 */
    create: (data: InventoryStocktakeAdd) =>
        postRequest('/scm/inventory/stocktake/create', data) as unknown as Promise<ScmResponse<Id>>,
    /** 改草稿（仅 DRAFT 可改）；会重新快照账面量。 */
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
