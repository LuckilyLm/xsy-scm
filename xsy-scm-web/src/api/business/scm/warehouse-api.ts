/**
 * 仓库接口（新增文件）。
 *
 * 仓库是 W5 的**基础数据**（采购单必须有收货仓库），此前没有前端 API —— C 的采购页用
 * `a-input-number` 直接填 `supplierId` / 仓库 id，属于 A 源的建模缺口。
 *
 * 与后端 `WarehouseController` 逐端点对应（5 个）：
 * `list` / `query` / `detail/{id}` / `create` / `update`。
 *
 * **`status` 不在表单里**（`WarehouseAddForm` / `WarehouseUpdateForm` 均无此字段）：
 * 新建一律 `ENABLED`，且 W5 没有启停写入路径 —— 见验收报告 G1。
 */
import { getRequest, postRequest } from '/@/lib/axios';
import type { ScmPage, ScmResponse } from '/@/types/business/scm/customer';
import type {
  Id,
  Warehouse,
  WarehousePayload,
  WarehouseQuery,
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
};

export default warehouseApi;
