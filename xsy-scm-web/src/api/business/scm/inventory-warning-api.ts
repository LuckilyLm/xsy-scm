/**
 * 库存预警接口，与后端 `InventoryWarningController` 对应。
 *
 * 预警列表刻意没有「标记已读 / 已忽略」这类写操作 —— 预警不是一种状态，
 * 它只是 `(阈值, 可用量)` 的当前计算结果，加「已读」会让预警与真实库存脱钩。
 *
 * `query` 的 `status` 为空时后端只返回异常项（低于下限 / 高于上限）—— 这是默认语义，
 * 不是「全部」。`scan` 会给别人发站内信，因此单独用 `scm:inventory:warning:scan`
 * 授权；重复调用不会重复发信（同一次跃迁的 event_key 稳定）。
 */
import {postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
    InventoryWarning,
    InventoryWarningQuery,
    InventoryWarningScanResult,
} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryWarningApi = {
    /** 预警列表；`status` 为空 → 只看异常。 */
    query: (data: InventoryWarningQuery, options?: RequestOptions) =>
        postRequest('/scm/inventory/warning/query', data, options) as unknown as Promise<
            ScmResponse<ScmPage<InventoryWarning>>
        >,

    /**
     * 立即检查阈值跃迁并投递通知（不等下一轮定时扫描）。
     *
     * 只扫描调用者有授权的仓库；没有入参，范围由服务端解析。
     */
    scan: () =>
        postRequest('/scm/inventory/warning/scan', {}) as unknown as Promise<
            ScmResponse<InventoryWarningScanResult>
        >,
};

export default inventoryWarningApi;
