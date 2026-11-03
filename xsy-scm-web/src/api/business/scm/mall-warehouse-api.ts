import {getRequest, request} from '/@/lib/axios';
export interface MallWarehouseRule {
  warehouseId: string | number;
  warehouseName: string;
  source: string;
  priority: number;
}
export const mallWarehouseAdminApi = {
  options: () => getRequest<MallWarehouseRule[]>('/scm/mall-admin/warehouse/options', {}),
  districtRules: (districtCode: number) => getRequest<MallWarehouseRule[]>('/scm/mall-admin/warehouse/district-rules', {districtCode}),
  saveDistrictRule: (form: {districtCode: number; warehouseId: string | number; priority: number}) =>
    request({url: '/scm/mall-admin/warehouse/district-rules', method: 'put', data: form}),
  removeDistrictRule: (districtCode: number, warehouseId: string | number) =>
    request({url: `/scm/mall-admin/warehouse/district-rules/${districtCode}/${warehouseId}`, method: 'delete'}),
};
