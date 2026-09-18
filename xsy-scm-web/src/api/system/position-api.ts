/**
 * 职务表 api 封装
 *
 */
import { postRequest, getRequest } from '/@/lib/axios';

export const positionApi = {

  /**
   * 分页查询
   */
  queryPage : (param) => {
    return postRequest('/position/queryPage', param);
  },

  /**
   * 增加
   */
  add: (param) => {
      return postRequest('/position/add', param);
  },

  /**
   * 修改
   */
  update: (param) => {
      return postRequest('/position/update', param);
  },


  /**
   * 删除
   */
  delete: (id) => {
      return getRequest(`/position/delete/${id}`);
  },

  /**
   * 批量删除
   */
  batchDelete: (idList) => {
    return postRequest('/position/batchDelete', idList);
  },

  /**
   * 查询列表
   */
  queryList: () => {
    return getRequest('/position/queryList');
  },

};
