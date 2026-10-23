/** 首页指标只读，按权限返回；无权数据不能当作零。 */
import {getRequest} from '/@/lib/axios';
/** 一张待办卡片：key 稳定标识，route 为已带查询条件的目标列表页路径。 */
export interface ScmTodo {
  key: string;
  label: string;
  count: number;
  route: string;
}
/** 一张 KPI 卡：后端给标识、数值、单位与跳转，中文名和配色由前端映射。 */
export interface ScmDashboardCard {
  key: string;
  /** 后端小数一律是 string（金额保留 4 位定点），展示前统一走格式化函数。 */
  value: string;
  /** `CNY` 是金额（两位小数 + 千分位），`COUNT` 是笔数 / 件数。 */
  unit: 'CNY' | 'COUNT';
  route: string;
}
/** 趋势的指标族：销售额 / 采购额 / 库存流转。 */
export type ScmTrendMetric = 'sales' | 'purchase' | 'inventory';
/** 趋势区间。 */
export type ScmTrendRange = '7d' | '30d';
/** 趋势图：两条序列共用一根日期轴；量纲不同时由前端决定是否用双轴。 */
export interface ScmDashboardTrend {
  metric: ScmTrendMetric;
  range: ScmTrendRange;
  dates: string[];
  primarySeries: string[];
  secondarySeries: string[];
}
/** 排行维度：客户或商品。 */
export type ScmRankDimension = 'customer' | 'product';
/** 排行项：同一对象的金额由服务端合并。 */
export interface ScmRankItem {
  name: string;
  amount: string;
}
/** 库存健康度五档：五档互斥，合计等于 total；与顶部「库存预警」卡不是同一指标。 */
export interface ScmDashboardInventoryHealth {
  total: number;
  normal: number;
  low: number;
  high: number;
  unconfigured: number;
  outOfStock: number;
}
/** 各区块自行显示失败原因并重试，不走全局提示，避免一块失败打断别处。 */
const REGION_REQUEST_OPTIONS = {suppressGlobalErrorMessage: true};
export const scmDashboardApi = {
  /** 业务待办卡片；无权限的卡片后端不返回。 */
  todo: () => getRequest<ScmTodo[]>('/scm/dashboard/todo', {}, REGION_REQUEST_OPTIONS),
  /** 当前人可见的 KPI 卡。 */
  overview: () => getRequest<ScmDashboardCard[]>('/scm/dashboard/overview', {}, REGION_REQUEST_OPTIONS),
  /** 单指标趋势；无权限时后端拒绝，调用方先判断权限。 */
  trend: (metric: ScmTrendMetric, range: ScmTrendRange) =>
    getRequest<ScmDashboardTrend>('/scm/dashboard/trend', {metric, range}, REGION_REQUEST_OPTIONS),
  /** 销售排行（客户 / 商品），limit 默认 5。 */
  ranking: (dimension: ScmRankDimension, limit = 5) =>
    getRequest<ScmRankItem[]>('/scm/dashboard/ranking', {dimension, limit}, REGION_REQUEST_OPTIONS),
  /** 库存健康度五档；另需库存预警权限。 */
  inventoryHealth: () => getRequest<ScmDashboardInventoryHealth>('/scm/dashboard/inventory-health', {}, REGION_REQUEST_OPTIONS),
};
export default scmDashboardApi;
