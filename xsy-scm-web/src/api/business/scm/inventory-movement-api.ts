/**
 * 库存流水接口（W6 新增文件）。
 *
 * 与后端 `InventoryMovementController` 逐端点对应（1 个，**只读**）：
 * `POST /scm/inventory/movement/query`。
 *
 * **append-only 在接口形状上的体现**：只有 query 一个端点。没有新增、没有编辑、没有删除 ——
 * 这不是「暂时没做」，而是 Q7 硬化后的契约：流水表在 DB 层有
 * `CHECK (deleted = FALSE)`，未来冲销必须**新增反向 movement**，
 * 因此永远不会出现「改历史流水」的 API。
 *
 * 权限：`scm:inventory:movement:query`（菜单 821）。
 */
import {postRequest} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
    InventoryMovement,
    InventoryMovementQuery,
} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryMovementApi = {
    query: (data: InventoryMovementQuery) =>
        postRequest('/scm/inventory/movement/query', data) as unknown as Promise<
            ScmResponse<ScmPage<InventoryMovement>>
        >,
};

export default inventoryMovementApi;
