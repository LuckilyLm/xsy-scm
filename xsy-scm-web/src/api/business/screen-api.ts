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
  /**
   * 地理分布（地图 M1）。
   *
   * 市级气泡与省级着色来自**同一份**返回，前端不做二次聚合：省界数值由后端从市级
   * 事实上卷，两边永远对得上。
   */
  getGeoData: () => {
    return getRequest('/scm/screen/data/geo', {});
  },
};
