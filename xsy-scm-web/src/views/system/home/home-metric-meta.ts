/**
 * 首页工作台的展示元数据：后端只给业务标识，中文名、图标与语义色在这里映射。
 *
 * 放在前端是刻意的 —— 接口只回答「该不该给 / 多少 / 跳哪里」，
 * 把 label / icon / color 写进接口等于把界面设计固化进服务端。
 */
import type {ScmRankDimension, ScmTrendMetric, ScmTrendRange} from '/@/api/business/scm/dashboard-api';

/** 语义色：只表达业务含义，不表达装饰。 */
export type MetricTone = 'primary' | 'ok' | 'warn' | 'danger' | 'muted';

interface KpiMeta {
    label: string;
    iconName: string;
}

/** KPI 卡标识 → 展示信息。 */
export const KPI_META: Record<string, KpiMeta> = {
    'sales-amount': {label: '今日销售额', iconName: 'kpi-sales-amount'},
    'order-count': {label: '今日订单', iconName: 'kpi-order-count'},
    'purchase-amount': {label: '今日采购额', iconName: 'kpi-purchase-amount'},
    'receipt-count': {label: '今日收货', iconName: 'kpi-receipt-count'},
    'inventory-warning': {label: '库存预警', iconName: 'kpi-inventory-warning'},
};

/** 后端新增卡片时前端还没映射，退化成标识本身，不因为缺一项映射就整块不显示。 */
export function kpiMetaOf(key: string): KpiMeta {
    return KPI_META[key] ?? {label: key, iconName: 'kpi-order-count'};
}

/**
 * KPI 语义色。
 *
 * 库存预警按「有没有事」分色：有预警是待处理（橙），一条都没有是正常（绿）。
 * 0 也染成警示色会让人以为天天出事。
 */
export function kpiTone(key: string, value: number): MetricTone {
    if (key === 'inventory-warning') {
        return value > 0 ? 'warn' : 'ok';
    }
    return 'primary';
}

interface TrendMeta {
    /** 指标切换按钮上的名字。 */
    label: string;
    /** 主序列名（金额 / 数量）。 */
    primaryLabel: string;
    /** 次序列名（笔数 / 数量）。 */
    secondaryLabel: string;
    /** 两条序列量纲不同（金额 vs 笔数）时为真，需要左右双轴。 */
    dualAxis: boolean;
}

export const TREND_META: Record<ScmTrendMetric, TrendMeta> = {
    sales: {label: '销售额', primaryLabel: '销售额', secondaryLabel: '订单数', dualAxis: true},
    purchase: {label: '采购额', primaryLabel: '采购额', secondaryLabel: '采购单数', dualAxis: true},
    inventory: {label: '库存流转', primaryLabel: '入库量', secondaryLabel: '出库量', dualAxis: false},
};

export const TREND_RANGES: Array<{value: ScmTrendRange; label: string}> = [
    {value: '7d', label: '近 7 天'},
    {value: '30d', label: '近 30 天'},
];

export const RANK_META: Record<ScmRankDimension, {title: string; emptyText: string}> = {
    customer: {title: '客户销售 TOP5', emptyText: '今日暂无成交客户'},
    product: {title: '商品销售 TOP5', emptyText: '今日暂无成交商品'},
};

/** 库存健康度五档的展示定义。 */
export interface HealthBucket {
    key: string;
    label: string;
    /** 对应 `ScmDashboardInventoryHealth` 上的字段名。 */
    field: 'normal' | 'low' | 'high' | 'outOfStock' | 'unconfigured';
    tone: MetricTone;
}

/**
 * 五档互斥且之和等于参与评估总数，与顶部「库存预警」卡不是同一个指标，
 * 所以这里只表达各自占参与评估总数的比例，不做跨指标的加减。
 */
export const INVENTORY_HEALTH_BUCKETS: HealthBucket[] = [
    {key: 'normal', label: '正常', field: 'normal', tone: 'ok'},
    {key: 'low', label: '偏低', field: 'low', tone: 'warn'},
    {key: 'high', label: '偏高', field: 'high', tone: 'warn'},
    {key: 'outOfStock', label: '缺货', field: 'outOfStock', tone: 'danger'},
    {key: 'unconfigured', label: '未配置', field: 'unconfigured', tone: 'muted'},
];
