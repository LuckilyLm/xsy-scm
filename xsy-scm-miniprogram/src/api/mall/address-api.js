/*
 * 商城收货地址接口
 *
 * 归属由服务端从登录身份派生，客户端提交的 customerId 一律被忽略。
 * 所有写操作返回刷新后的地址列表。
 */
import { deleteRequest, getRequest, postRequest, putRequest } from '@/lib/smart-request';

export const mallAddressApi = {
  /** 地址列表（默认地址在前） */
  getList: () => getRequest('/scm/mall/addresses'),

  /** 地址详情 */
  getDetail: (id) => getRequest(`/scm/mall/addresses/${id}`),

  /**
   * 新增地址
   * @param {{receiverName: string, receiverPhone: string, detailAddress: string,
   *          provinceCode?: number, provinceName?: string, cityCode?: number, cityName?: string,
   *          districtCode: number, districtName: string, longitude: number, latitude: number,
   *          isDefault?: boolean}} param
   */
  create: (param) => postRequest('/scm/mall/addresses', param),

  /** 修改地址 */
  update: (id, param) => putRequest(`/scm/mall/addresses/${id}`, param),

  /** 设为默认地址 */
  setDefault: (id) => postRequest(`/scm/mall/addresses/${id}/default`),

  /** 删除地址 */
  remove: (id) => deleteRequest(`/scm/mall/addresses/${id}`),
};
