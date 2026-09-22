/*
 * 商城收货地址接口
 *
 * ⚠️ 后端尚未实现，路径与字段以后端冻结契约为准。
 *
 * 约束（规划文档 §32 + 地理列约定）：
 * - 归属由服务端从登录身份派生，客户端提交的 customerId 一律被忽略；
 * - 地址需含省市区编码与名称快照六列 + longitude / latitude，
 *   形态与 V40 给 customer / warehouse / supplier 加的地理列保持一致
 *   （配送排线与商城地址共用同一份前置，形态不一致会在后续再付一次改表代价）；
 * - 「一个账号一个默认地址」由服务端约束保证。
 */
import { deleteRequest, getRequest, postRequest, putRequest } from '@/lib/smart-request';

export const mallAddressApi = {
  /** 地址列表 */
  getList: () => getRequest('/scm/mall/addresses'),

  /** 地址详情 */
  getDetail: (id) => getRequest(`/scm/mall/addresses/${id}`),

  /**
   * 新增地址
   * @param {object} param {
   *   receiverName, phone, detailAddress,
   *   provinceCode, cityCode, districtCode,
   *   provinceName, cityName, districtName,
   *   longitude?, latitude?, isDefault?
   * }
   */
  create: (param) => postRequest('/scm/mall/addresses', param),

  /** 修改地址 */
  update: (id, param) => putRequest(`/scm/mall/addresses/${id}`, param),

  /** 设为默认地址 */
  setDefault: (id) => postRequest(`/scm/mall/addresses/${id}/default`),

  /** 删除地址 */
  remove: (id) => deleteRequest(`/scm/mall/addresses/${id}`),
};
