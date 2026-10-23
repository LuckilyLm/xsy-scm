/** 首页指标接口只读，后端按权限返回可见卡片；前端应按领域权限决定请求哪些指标，不能把无权数据当作零。 */
import {getRequest} from '/@/lib/axios';
import type {ScmResponse} from '/@/types/business/scm/customer';

/** 一张待办卡片：key 稳定标识，route 为已带查询条件的目标列表页路径。 */
export interface ScmTodo {
  key: string;
  label: string;
  count: number;
  route: string;
}

/**
 * 一张 KPI 卡：后端只给业务语义（标识 / 数值 / 单位 / 跳转），
 * 中文名、图标与配色由前端按 key 映射。
 */
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

/**
 * 趋势图：两条序列共用同一根日期轴。
 *
 * 两条序列可能不同量纲（金额 vs 笔数），由前端决定要不要用双轴；
 * 库存流转的入库量与出库量同量纲，共用一根轴即可。
 */
export interface ScmDashboardTrend {
  metric: ScmTrendMetric;
  range: ScmTrendRange;
  dates: string[];
  primarySeries: string[];
  secondarySeries: string[];
}

/** 排行维度：两个维度都来自销售事实。 */
export type ScmRankDimension = 'customer' | 'product';

/** 排行项：金额按业务对象 ID 合并，名称由服务端补当前主数据名称或对象编号。 */
export interface ScmRankItem {
  name: string;
  amount: string;
}

/**
 * 库存健康度五档：五档互斥且之和恒等于 `total`。
 *
 * 与顶部「库存预警」卡不是同一个指标：预警列表只收低于下限 / 高于上限，
 * 这里还含缺货与未配置阈值，所以两个数字不应该互相对齐。
 */
export interface ScmDashboardInventoryHealth {
  total: number;
  normal: number;
  low: number;
  high: number;
  unconfigured: number;
  outOfStock: number;
}

/**
 * 首页每个区块各自显示失败原因并提供重试，因此不走全局提示 ——
 * 首页有多个互不相关的数据源，一块失败不该用一条 toast 把用户从别处打断。
 */
const REGION_REQUEST_OPTIONS = {suppressGlobalErrorMessage: true};

export const scmDashboardApi = {
  /** 当前人的业务待办卡片（无权限的卡片后端直接省略，不返回 0）。 */
  todo: () => getRequest('/scm/dashboard/todo', {}, REGION_REQUEST_OPTIONS) as unknown as Promise<ScmResponse<ScmTodo[]>>,

  /** 当前人可见的 KPI 卡：入口权限与各卡领域权限两层裁剪都由后端完成。 */
  overview: () => getRequest('/scm/dashboard/overview', {}, REGION_REQUEST_OPTIONS) as unknown as Promise<ScmResponse<ScmDashboardCard[]>>,

  /** 单指标趋势；缺该指标的领域权限时后端拒绝，调用方应先用权限决定是否请求。 */
  trend: (metric: ScmTrendMetric, range: ScmTrendRange) =>
    getRequest('/scm/dashboard/trend', {metric, range}, REGION_REQUEST_OPTIONS) as unknown as Promise<ScmResponse<ScmDashboardTrend>>,

  /** 销售排行（客户 / 商品共用），limit 默认 5、后端上限为已取回的条数。 */
  ranking: (dimension: ScmRankDimension, limit = 5) =>
    getRequest('/scm/dashboard/ranking', {dimension, limit}, REGION_REQUEST_OPTIONS) as unknown as Promise<ScmResponse<ScmRankItem[]>>,

  /** 库存健康度五档；除入口权限外还要求库存预警权。 */
  inventoryHealth: () =>
    getRequest('/scm/dashboard/inventory-health', {}, REGION_REQUEST_OPTIONS) as unknown as Promise<ScmResponse<ScmDashboardInventoryHealth>>,
};

export default scmDashboardApi;
