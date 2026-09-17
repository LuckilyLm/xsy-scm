/*
 * 采购收货接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const purchaseReceiveApi = {
  query: (param) => postRequest('/purchase/receive/query', param),
  add: (param) => postRequest('/purchase/receive/add', param),
  update: (param) => postRequest('/purchase/receive/update', param),
  delete: (receiveId) => getRequest(`/purchase/receive/delete/${receiveId}`),
  batchDelete: (idList) => postRequest('/purchase/receive/batchDelete', idList),
  confirmInbound: (receiveId) => postRequest(`/purchase/receive/confirmInbound/${receiveId}`),
};
