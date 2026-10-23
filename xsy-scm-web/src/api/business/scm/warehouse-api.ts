/** 仓库接口：查询与编辑沿用既有接口，状态通过启用 / 停用命令管理。 */
import {getRequest, postRequest, type RequestOptions} from '/@/lib/axios';
import type {ScmPage} from '/@/types/business/scm/customer';
import type {
  Id,
  Warehouse,
  WarehousePayload,
  WarehouseQuery,
  WarehouseScopeEmployee,
  WarehouseScopeUpdatePayload,
  WarehouseScopeWarehouse,
  WarehouseStatusPayload,
} from '/@/views/business/scm/purchase/purchase-types';
export const warehouseApi = {
  /** 全量启用仓库（选择器用）。 */
  list: (options?: RequestOptions) => getRequest<Warehouse[]>('/scm/warehouse/list', {}, options),
  query: (data: WarehouseQuery, options?: RequestOptions) => postRequest<ScmPage<Warehouse>>('/scm/warehouse/query', data, options),
  detail: (id: Id) => getRequest<Warehouse>(`/scm/warehouse/detail/${id}`, {}),
  create: (data: WarehousePayload) => postRequest<Id>('/scm/warehouse/create', data),
  update: (data: WarehousePayload) => postRequest<string>('/scm/warehouse/update', data),
  /** 启用仓库。 */
  enable: (data: WarehouseStatusPayload) => postRequest<string>('/scm/warehouse/enable', data),
  /** 停用仓库（严格模式）。 */
  disable: (data: WarehouseStatusPayload) => postRequest<string>('/scm/warehouse/disable', data),
  // 员工—仓库授权维护（WarehouseScopeController）：与仓库主数据是两个独立权限点。
  /** 某仓库下被授权的员工（只读，scm:warehouse:scope:query）。 */
  scopeEmployees: (warehouseId: Id, options?: RequestOptions) =>
    getRequest<WarehouseScopeEmployee[]>('/scm/warehouse/scope/employees', {warehouseId}, options),
  /** 某员工被授权的仓库（只读，scm:warehouse:scope:query）。 */
  scopeWarehouses: (employeeId: Id, options?: RequestOptions) =>
    getRequest<WarehouseScopeWarehouse[]>('/scm/warehouse/scope/warehouses', {employeeId}, options),
  /** 整体替换某员工的活动授权（scm:warehouse:scope:update）；空清单即全部回收。 */
  scopeUpdate: (data: WarehouseScopeUpdatePayload) => postRequest<string>('/scm/warehouse/scope/update', data),
};
export default warehouseApi;
