/* 
  * OA发票信息
  * 
  */
import {postRequest, getRequest} from '/@/lib/axios';

export const invoiceApi = {

    // 新建发票信息
    create: (param) => {
        return postRequest('/oa/invoice/create', param);
    },

    // 删除发票信息
    delete: (bankId) => {
        return getRequest(`/oa/invoice/delete/${bankId}`);
    },

    // 查询发票信息详情
    detail: (bankId) => {
        return getRequest(`//oa/invoice/get/${bankId}`);
    },

    // 分页查询发票信息
    pageQuery: (param) => {
        return postRequest('/oa/invoice/page/query', param);
    },

    // 编辑发票信息
    update: (param) => {
        return postRequest('/oa/invoice/update', param);
    },

    // 查询发票列表
    queryList: (enterpriseId) => {
        return getRequest(`/oa/invoice/query/list/${enterpriseId}`);
    },

};
