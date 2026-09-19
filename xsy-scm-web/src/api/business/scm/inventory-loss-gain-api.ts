/**
 * 库存报损报溢单接口（报损报溢波次新增文件）。
 *
 * 与后端 `InventoryLossGainController` 逐端点对应（7 个）：
 * query / detail / create / update / approve / reject / delete。
 *
 * **权限分三档**：录单与改待审核是日常操作（`add` / `update`），
 * **`approve` 与 `reject` 是两个独立权限** —— 允许主管审批、由另一角色驳回是常见分工。
 * 「新建」与「审批」必须分开：报损是把货从账上抹掉的动作，由同一人录单并批准就失去了制衡。
 *
 * **审批必须带 `version`**（乐观锁）：审批人必须批准自己读到的内容。
 * 若在「打开单据 → 点审批」之间单据被改过，后端返回 40921 并要求刷新。
 */
import { getRequest, postRequest } from '/@/lib/axios';
import type { ScmPage, ScmResponse } from '/@/types/business/scm/customer';
import type {
  Id,
  InventoryLossGain,
  InventoryLossGainAdd,
  InventoryLossGainAudit,
  InventoryLossGainQuery,
} from '/@/views/business/scm/inventory/inventory-types';

export const inventoryLossGainApi = {
  query: (data: InventoryLossGainQuery) =>
    postRequest('/scm/inventory/loss-gain/query', data) as unknown as Promise<
      ScmResponse<ScmPage<InventoryLossGain>>
    >,
  detail: (id: Id) =>
    getRequest(`/scm/inventory/loss-gain/detail/${id}`, {}) as unknown as Promise<
      ScmResponse<InventoryLossGain>
    >,
  /** 新建（创建即待审核），返回新单 id。 */
  create: (data: InventoryLossGainAdd) =>
    postRequest('/scm/inventory/loss-gain/create', data) as unknown as Promise<ScmResponse<Id>>,
  /** 改待审核单据（仅 PENDING 可改）。 */
  update: (id: Id, data: InventoryLossGainAdd) =>
    postRequest(`/scm/inventory/loss-gain/update/${id}`, data) as unknown as Promise<ScmResponse<string>>,
  /** 审批通过：写 LOSS_REPORT / GAIN_REPORT 流水并调整余额。 */
  approve: (id: Id, data: InventoryLossGainAudit) =>
    postRequest(`/scm/inventory/loss-gain/approve/${id}`, data) as unknown as Promise<ScmResponse<string>>,
  /** 驳回（不产生库存影响）；审核意见必填。 */
  reject: (id: Id, data: InventoryLossGainAudit) =>
    postRequest(`/scm/inventory/loss-gain/reject/${id}`, data) as unknown as Promise<ScmResponse<string>>,
  /** 删除待审核单据（逻辑删）。 */
  delete: (id: Id) =>
    postRequest(`/scm/inventory/loss-gain/delete/${id}`, {}) as unknown as Promise<ScmResponse<string>>,
};

export default inventoryLossGainApi;
