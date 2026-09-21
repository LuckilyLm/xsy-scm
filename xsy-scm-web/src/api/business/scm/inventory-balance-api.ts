/**
 * 库存余额接口（W6 新增文件）。
 *
 * 与后端 `InventoryBalanceController` 逐端点对应（2 个，**全部只读**）：
 * `POST /scm/inventory/balance/query` / `GET /scm/inventory/balance/detail/{id}`。
 *
 * **没有写端点**：库存余额不是可以被直接赋值的状态，它只能是流水的净和。
 * W6-1 唯一的写入路径是「收货确认 → `PURCHASE_IN`」，发生在采购侧的同事务内，
 * 前端不参与。因此这里刻意不提供 `create` / `update` / `delete` —— 一个不存在的函数
 * 比一个会返回 404/405 的函数更能说明问题。
 *
 * 权限：两个端点共用 `scm:inventory:balance:query`（菜单 811）。
 */
import {getRequest, postRequest} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
    Id,
    InventoryBalance,
    InventoryBalanceQuery,
} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryBalanceApi = {
    query: (data: InventoryBalanceQuery) =>
        postRequest('/scm/inventory/balance/query', data) as unknown as Promise<
            ScmResponse<ScmPage<InventoryBalance>>
        >,
    detail: (id: Id) =>
        getRequest(`/scm/inventory/balance/detail/${id}`, {}) as unknown as Promise<
            ScmResponse<InventoryBalance>
        >,
};

export default inventoryBalanceApi;
