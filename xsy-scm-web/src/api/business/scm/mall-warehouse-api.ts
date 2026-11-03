import {getRequest, request, type ApiResponse, type RequestOptions} from '/@/lib/axios';

export interface MallWarehouseRule {
  warehouseId: string | number;
  warehouseName: string;
  source: string;
  priority: number;
}

export interface MallDistrictRuleForm {
  districtCode: number;
  warehouseId: string | number;
  priority: number;
}

export const mallWarehouseAdminApi = {
  options: (options?: RequestOptions): Promise<ApiResponse<MallWarehouseRule[]>> =>
    getRequest<MallWarehouseRule[]>('/scm/mall-admin/warehouse/options', {}, options),
  districtRules: (districtCode: number, options?: RequestOptions): Promise<ApiResponse<MallWarehouseRule[]>> =>
    getRequest<MallWarehouseRule[]>('/scm/mall-admin/warehouse/district-rules', {districtCode}, options),
  saveDistrictRule: (form: MallDistrictRuleForm): Promise<ApiResponse<unknown>> =>
    request({url: '/scm/mall-admin/warehouse/district-rules', method: 'put', data: form}),
  removeDistrictRule: (districtCode: number, warehouseId: string | number): Promise<ApiResponse<unknown>> =>
    request({url: `/scm/mall-admin/warehouse/district-rules/${districtCode}/${warehouseId}`, method: 'delete'}),
};
