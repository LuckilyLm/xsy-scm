/** 库存预警按可用量和阈值实时计算，只读展示。预警没有已读、忽略等可修改状态，不提供标记处理接口。 */
import {postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {InventoryWarning, InventoryWarningQuery, InventoryWarningScanResult} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryWarningApi = {
  /** 预警列表；`status` 为空 → 只看异常。 */
  query: (data: InventoryWarningQuery, options?: RequestOptions) =>
    postRequest('/scm/inventory/warning/query', data, options) as unknown as Promise<ScmResponse<ScmPage<InventoryWarning>>>,

  /** 立即检查阈值并投递通知，只扫描有授权的仓库。 */
  scan: () => postRequest('/scm/inventory/warning/scan', {}) as unknown as Promise<ScmResponse<InventoryWarningScanResult>>,
};

export default inventoryWarningApi;
