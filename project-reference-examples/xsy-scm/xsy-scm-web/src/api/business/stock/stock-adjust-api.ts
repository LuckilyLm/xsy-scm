/*
 * 库存调整单（报损报溢）接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const stockAdjustApi = {
  // 分页查询
  query: (param) => {
    return postRequest('/stock/adjust/query', param);
  },
  // 新增
  add: (param) => {
    return postRequest('/stock/adjust/add', param);
  },
  // 更新
  update: (param) => {
    return postRequest('/stock/adjust/update', param);
  },
  // 删除
  delete: (adjustId) => {
    return getRequest(`/stock/adjust/delete/${adjustId}`);
  },
  // 批量删除
  batchDelete: (adjustIdList) => {
    return postRequest('/stock/adjust/batchDelete', adjustIdList);
  },
  // 审核通过
  approve: (param) => {
    return postRequest('/stock/adjust/approve', param);
  },
  // 驳回
  reject: (param) => {
    return postRequest('/stock/adjust/reject', param);
  },
};
