/**
 * 仓库接口（新增文件）。
 *
 * 仓库是 W5 的**基础数据**（采购单必须有收货仓库），此前没有前端 API —— C 的采购页用
 * `a-input-number` 直接填 `supplierId` / 仓库 id，属于 A 源的建模缺口。
 *
 * 与后端 `WarehouseController` 逐端点对应：查询与基础信息写入沿用 W5 契约，
 * B1 通过独立的 `enable` / `disable` 命令管理状态。`status` 不进入新增或编辑表单，
 * 新建仓库仍一律为 `ENABLED`。
 */
import {getRequest, postRequest} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
    Id,
    Warehouse,
    WarehousePayload,
    WarehouseQuery,
    WarehouseStatusPayload,
} from '/@/views/business/scm/purchase/purchase-types';

export const warehouseApi = {
    /** 全量启用仓库（选择器用）。 */
    list: () => getRequest('/scm/warehouse/list', {}) as unknown as Promise<ScmResponse<Warehouse[]>>,
    query: (data: WarehouseQuery) =>
        postRequest('/scm/warehouse/query', data) as unknown as Promise<ScmResponse<ScmPage<Warehouse>>>,
    detail: (id: Id) =>
        getRequest(`/scm/warehouse/detail/${id}`, {}) as unknown as Promise<ScmResponse<Warehouse>>,
    create: (data: WarehousePayload) =>
        postRequest('/scm/warehouse/create', data) as unknown as Promise<ScmResponse<Id>>,
    update: (data: WarehousePayload) =>
        postRequest('/scm/warehouse/update', data) as unknown as Promise<ScmResponse<string>>,
    /** 启用仓库（B1，HD-B1-01）。 */
    enable: (data: WarehouseStatusPayload) =>
        postRequest('/scm/warehouse/enable', data) as unknown as Promise<ScmResponse<string>>,
    /** 停用仓库（B1，HD-B1-01 严格模式）。 */
    disable: (data: WarehouseStatusPayload) =>
        postRequest('/scm/warehouse/disable', data) as unknown as Promise<ScmResponse<string>>,
};

export default warehouseApi;
