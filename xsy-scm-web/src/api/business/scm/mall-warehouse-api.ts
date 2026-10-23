import {getRequest, request} from '/@/lib/axios';
import type {ScmResponse} from '/@/types/business/scm/customer';

export interface MallWarehouseRule {
  warehouseId: string | number;
  warehouseName: string;
  source: string;
  priority: number;
}

export const mallWarehouseAdminApi = {
  options: () => getRequest('/scm/mall/warehouse/admin/options', {}) as unknown as Promise<ScmResponse<MallWarehouseRule[]>>,
  districtRules: (districtCode: number) =>
    getRequest('/scm/mall/warehouse/admin/district-rules', {districtCode}) as unknown as Promise<ScmResponse<MallWarehouseRule[]>>,
  saveDistrictRule: (form: {districtCode: number; warehouseId: string | number; priority: number}) =>
    request({url: '/scm/mall/warehouse/admin/district-rules', method: 'put', data: form}),
  removeDistrictRule: (districtCode: number, warehouseId: string | number) =>
    request({url: `/scm/mall/warehouse/admin/district-rules/${districtCode}/${warehouseId}`, method: 'delete'}),
};
