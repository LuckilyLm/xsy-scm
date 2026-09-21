/**
 * 库存规格转换接口（规格转换波次新增文件）。
 *
 * 与后端 `InventoryConversionController` 逐端点对应（7 个）：
 * query / detail / create / update / approve / reject / delete。
 *
 * **跨 SKU、同仓库**：跨仓搬运是调拨（`inventory-transfer-api`），不是转换。
 *
 * **审批必须带 `version`**（乐观锁）：审批人必须批准自己读到的内容。
 * 若在「打开单据 → 点审批」之间折算关系被改过（那是金额相关的改动），
 * 后端返回 40921 并要求刷新 —— 因此前端**不得**在打开审批弹窗时重新拉取单据。
 */
import {getRequest, postRequest} from '/@/lib/axios';
import type {ScmPage, ScmResponse} from '/@/types/business/scm/customer';
import type {
    Id,
    InventoryConversion,
    InventoryConversionAdd,
    InventoryConversionAudit,
    InventoryConversionQuery,
} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryConversionApi = {
    query: (data: InventoryConversionQuery) =>
        postRequest('/scm/inventory/conversion/query', data) as unknown as Promise<
            ScmResponse<ScmPage<InventoryConversion>>
        >,
    detail: (id: Id) =>
        getRequest(`/scm/inventory/conversion/detail/${id}`, {}) as unknown as Promise<
            ScmResponse<InventoryConversion>
        >,
    /** 新建（创建即待审核），返回新单 id。 */
    create: (data: InventoryConversionAdd) =>
        postRequest('/scm/inventory/conversion/create', data) as unknown as Promise<ScmResponse<Id>>,
    /** 改待审核单据（仅 PENDING 可改）。 */
    update: (id: Id, data: InventoryConversionAdd) =>
        postRequest(`/scm/inventory/conversion/update/${id}`, data) as unknown as Promise<ScmResponse<string>>,
    /** 审批通过：写 CONVERT_OUT + CONVERT_IN 流水并调整两边余额。 */
    approve: (id: Id, data: InventoryConversionAudit) =>
        postRequest(`/scm/inventory/conversion/approve/${id}`, data) as unknown as Promise<ScmResponse<string>>,
    /** 驳回（不产生库存影响）；审核意见必填。 */
    reject: (id: Id, data: InventoryConversionAudit) =>
        postRequest(`/scm/inventory/conversion/reject/${id}`, data) as unknown as Promise<ScmResponse<string>>,
    /** 删除待审核单据（逻辑删）。 */
    delete: (id: Id) =>
        postRequest(`/scm/inventory/conversion/delete/${id}`, {}) as unknown as Promise<ScmResponse<string>>,
};

export default inventoryConversionApi;
