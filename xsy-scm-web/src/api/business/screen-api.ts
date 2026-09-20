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
  /**
   * 趋势数据（近 7 / 30 天）。
   *
   * 一次返回 8 条序列，供底部三张趋势图共用。**不要按图拆成多个接口**：
   * 那会让三张图的日期轴各自计算「今天」，边界上可能错开一天。
   */
  getTrendData: (range: '7d' | '30d' = '7d') => {
    return getRequest('/scm/screen/data/trend', { range });
  },
};
