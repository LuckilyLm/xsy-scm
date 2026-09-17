/*
 * 供应商对账单接口
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const supplierStatementApi = {
  query: (param) => postRequest('/supplier/statement/query', param),
  add: (param) => postRequest('/supplier/statement/add', param),
  update: (param) => postRequest('/supplier/statement/update', param),
  confirm: (param) => postRequest('/supplier/statement/confirm', param),
  settle: (statementId) => postRequest(`/supplier/statement/settle/${statementId}`),
  delete: (statementId) => getRequest(`/supplier/statement/delete/${statementId}`),
  batchDelete: (idList) => postRequest('/supplier/statement/batchDelete', idList),
};
