import { getRequest } from '/@/lib/axios';

/**
 * 数据大屏只读聚合接口
 */
export const screenApi = {
  /** 经营数据 */
  getBusinessData: () => {
    return getRequest('/scm/screen/data/business', {});
  },
  /** 库存数据 */
  getInventoryData: () => {
    return getRequest('/scm/screen/data/inventory', {});
  },
  /** 采购数据 */
  getPurchaseData: () => {
    return getRequest('/scm/screen/data/purchase', {});
  },
};
