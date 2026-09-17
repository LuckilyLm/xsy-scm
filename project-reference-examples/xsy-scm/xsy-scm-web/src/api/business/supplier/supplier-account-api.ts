/*
 * 供应商账号接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const supplierAccountApi = {
  query: (param) => postRequest('/supplier/account/query', param),
  add: (param) => postRequest('/supplier/account/add', param),
  update: (param) => postRequest('/supplier/account/update', param),
  delete: (accountId) => getRequest(`/supplier/account/delete/${accountId}`),
  batchDelete: (idList) => postRequest('/supplier/account/batchDelete', idList),
};
