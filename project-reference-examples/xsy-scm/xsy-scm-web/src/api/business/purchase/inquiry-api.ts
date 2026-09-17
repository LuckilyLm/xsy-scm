/*
 * 采购询价报价接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const inquiryApi = {
  query: (param) => postRequest('/inquiry/query', param),
  detail: (inquiryId) => getRequest(`/inquiry/get/${inquiryId}`),
  compare: (inquiryId) => postRequest(`/inquiry/compare/${inquiryId}`),
  add: (param) => postRequest('/inquiry/add', param),
  update: (param) => postRequest('/inquiry/update', param),
  quote: (param) => postRequest('/inquiry/quote', param),
  changeStatus: (param) => postRequest('/inquiry/changeStatus', param),
  delete: (inquiryId) => getRequest(`/inquiry/delete/${inquiryId}`),
  batchDelete: (idList) => postRequest('/inquiry/batchDelete', idList),
};
