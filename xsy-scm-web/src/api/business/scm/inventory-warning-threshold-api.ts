/**
 * 库存预警阈值配置接口（阈值预警波次新增文件）。
 *
 * 与后端 `InventoryWarningThresholdController` 逐端点对应（5 个）：
 * query / detail / create / update / delete。
 *
 * **本模块不改变库存**：阈值是配置，余额是派生状态，两者的写路径完全分开 ——
 * 配置路径不会、也不应该创建余额行（否则就会造出「没有任何流水支撑的余额行」）。
 * 因此这里没有「确认 / 审批」这类动作，改配置立即生效（预警是读时计算的，天然实时）。
 */
import { getRequest, postRequest } from '/@/lib/axios';
import type { ScmPage, ScmResponse } from '/@/types/business/scm/customer';
import type {
  Id,
  InventoryWarningThreshold,
  InventoryWarningThresholdAdd,
  InventoryWarningThresholdQuery,
} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryWarningThresholdApi = {
  query: (data: InventoryWarningThresholdQuery) =>
    postRequest('/scm/inventory/threshold/query', data) as unknown as Promise<
      ScmResponse<ScmPage<InventoryWarningThreshold>>
    >,
  detail: (id: Id) =>
    getRequest(`/scm/inventory/threshold/detail/${id}`, {}) as unknown as Promise<
      ScmResponse<InventoryWarningThreshold>
    >,
  /** 新建；同一 (仓库, SKU) 只允许一条（后端 41050）。 */
  create: (data: InventoryWarningThresholdAdd) =>
    postRequest('/scm/inventory/threshold/create', data) as unknown as Promise<ScmResponse<Id>>,
  /** 编辑；可把某个边界清空（传 null 即清空）。 */
  update: (id: Id, data: InventoryWarningThresholdAdd) =>
    postRequest(`/scm/inventory/threshold/update/${id}`, data) as unknown as Promise<ScmResponse<string>>,
  /** 删除（逻辑删）；删除后该 (仓库, SKU) 不再产生预警。 */
  delete: (id: Id) =>
    postRequest(`/scm/inventory/threshold/delete/${id}`, {}) as unknown as Promise<ScmResponse<string>>,
};

export default inventoryWarningThresholdApi;
