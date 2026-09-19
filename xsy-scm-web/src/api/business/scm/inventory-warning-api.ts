/**
 * 库存预警接口（阈值预警波次新增文件）。
 *
 * 与后端 `InventoryWarningController` 对应，**只有一个端点**：预警列表。
 *
 * 这里刻意**没有任何写操作** —— 预警不是一种可以「标记已读」的状态，它只是
 * `(阈值, 可用量)` 的当前计算结果。引入「已读 / 已忽略」会让预警与真实库存脱钩：
 * 货补上了那条「已读」记录还在，货又少了它却已经被忽略过。
 *
 * `query` 的 `status` 为空时后端只返回异常项（低于下限 / 高于上限）——
 * 这是预警列表的默认语义，不是「全部」。
 */
import { postRequest } from '/@/lib/axios';
import type { ScmPage, ScmResponse } from '/@/types/business/scm/customer';
import type {
  InventoryWarning,
  InventoryWarningQuery,
} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryWarningApi = {
  /** 预警列表；`status` 为空 → 只看异常。 */
  query: (data: InventoryWarningQuery) =>
    postRequest('/scm/inventory/warning/query', data) as unknown as Promise<
      ScmResponse<ScmPage<InventoryWarning>>
    >,
};

export default inventoryWarningApi;
