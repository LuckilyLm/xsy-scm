/*
 * 供应商厂商信息接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const supplierManufacturerApi = {
  query: (param) => postRequest('/supplier/manufacturer/query', param),
  add: (param) => postRequest('/supplier/manufacturer/add', param),
  update: (param) => postRequest('/supplier/manufacturer/update', param),
  delete: (manufacturerId) => getRequest(`/supplier/manufacturer/delete/${manufacturerId}`),
  batchDelete: (idList) => postRequest('/supplier/manufacturer/batchDelete', idList),
};
