/*
 * 产品分类api
 *
 * @author xsy-scm
 */
import { postRequest } from '/@/lib/axios';

export const productCategoryApi = {
  // 查询产品分类层级树
  queryTree: (param) => {
    return postRequest('/product/category/tree', param);
  },
};
