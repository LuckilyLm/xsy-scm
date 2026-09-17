/*
 * 商品转换单接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const productConvertApi = {
  query: (param) => postRequest('/productConvert/query', param),
  detail: (convertId) => getRequest(`/productConvert/get/${convertId}`),
  add: (param) => postRequest('/productConvert/add', param),
  update: (param) => postRequest('/productConvert/update', param),
  approve: (param) => postRequest('/productConvert/approve', param),
  reject: (param) => postRequest('/productConvert/reject', param),
  delete: (convertId) => getRequest(`/productConvert/delete/${convertId}`),
  batchDelete: (idList) => postRequest('/productConvert/batchDelete', idList),
};
