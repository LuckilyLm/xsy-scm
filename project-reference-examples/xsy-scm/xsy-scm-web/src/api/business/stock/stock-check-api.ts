/*
 * 库存盘点单接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const stockCheckApi = {
  // 分页查询
  query: (param) => {
    return postRequest('/stock/check/query', param);
  },
  // 新增
  add: (param) => {
    return postRequest('/stock/check/add', param);
  },
  // 更新
  update: (param) => {
    return postRequest('/stock/check/update', param);
  },
  // 删除
  delete: (checkId) => {
    return getRequest(`/stock/check/delete/${checkId}`);
  },
  // 批量删除
  batchDelete: (checkIdList) => {
    return postRequest('/stock/check/batchDelete', checkIdList);
  },
  // 完成盘点
  complete: (checkId) => {
    return postRequest(`/stock/check/complete/${checkId}`);
  },
};
