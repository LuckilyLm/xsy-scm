/*
 * 供应商商品提报接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const supplierProductApplyApi = {
  query: (param) => postRequest('/supplier/productApply/query', param),
  add: (param) => postRequest('/supplier/productApply/add', param),
  update: (param) => postRequest('/supplier/productApply/update', param),
  audit: (param) => postRequest('/supplier/productApply/audit', param),
  delete: (applyId) => getRequest(`/supplier/productApply/delete/${applyId}`),
  batchDelete: (idList) => postRequest('/supplier/productApply/batchDelete', idList),
};
