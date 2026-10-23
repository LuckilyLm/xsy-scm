import {getRequest, request} from '/@/lib/axios';
export interface MallWarehouseRule {
  warehouseId: string | number;
  warehouseName: string;
  source: string;
  priority: number;
}
export const mallWarehouseAdminApi = {
  options: () => getRequest<MallWarehouseRule[]>('/scm/mall/warehouse/admin/options', {}),
  districtRules: (districtCode: number) => getRequest<MallWarehouseRule[]>('/scm/mall/warehouse/admin/district-rules', {districtCode}),
  saveDistrictRule: (form: {districtCode: number; warehouseId: string | number; priority: number}) =>
    request({url: '/scm/mall/warehouse/admin/district-rules', method: 'put', data: form}),
  removeDistrictRule: (districtCode: number, warehouseId: string | number) =>
    request({url: `/scm/mall/warehouse/admin/district-rules/${districtCode}/${warehouseId}`, method: 'delete'}),
};
