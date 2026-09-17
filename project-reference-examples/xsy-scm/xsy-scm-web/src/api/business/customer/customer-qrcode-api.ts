/*
 * 客户收款码接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const customerQrcodeApi = {
  query: (param) => postRequest('/customer/qrcode/query', param),
  add: (param) => postRequest('/customer/qrcode/add', param),
  update: (param) => postRequest('/customer/qrcode/update', param),
  delete: (qrcodeId) => getRequest(`/customer/qrcode/delete/${qrcodeId}`),
  batchDelete: (idList) => postRequest('/customer/qrcode/batchDelete', idList),
};
