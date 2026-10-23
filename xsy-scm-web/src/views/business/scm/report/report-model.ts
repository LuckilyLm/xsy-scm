/** 报表的日期筛选、金额显示和成本权限处理。纯函数，不请求接口。 */
import dayjs from 'dayjs';

/** 闭区间日期 [开始, 结束]，格式 yyyy-MM-dd。 */
export type DateRange = [string, string];

/** 报表中心的快捷日期。 */
export type DatePresetKey = 'YESTERDAY' | 'THIS_WEEK' | 'LAST_WEEK' | 'THIS_MONTH' | 'LAST_MONTH';

/** 快捷日期的中文名，同时作为日期选择器的 presets 标签。 */
export const DATE_PRESET_LABELS: Record<DatePresetKey, string> = {
  YESTERDAY: '昨日',
  THIS_WEEK: '本周',
  LAST_WEEK: '上周',
  THIS_MONTH: '本月',
  LAST_MONTH: '上月',
};

/** 默认区间：本月。 */
export const DEFAULT_DATE_PRESET: DatePresetKey = 'THIS_MONTH';

/** 单次查询最大跨度（天，含首尾）。 */
export const MAX_REPORT_RANGE_DAYS = 366;

const DATE_PATTERN = 'YYYY-MM-DD';

function day(value?: Date | string): dayjs.Dayjs {
  return value === undefined ? dayjs() : dayjs(value);
}

/**
 * 快捷区间，返回闭区间 [start, end]。
 *
 * 周以周一为起点；本周和本月只到今天，避免趋势图出现零值尾巴。
 * @param today 基准日，单测可传定值
 */
export function presetRange(preset: DatePresetKey, today?: Date | string): DateRange {
  const base = day(today);
  switch (preset) {
    case 'YESTERDAY': {
      const y = base.subtract(1, 'day');
      return [y.format(DATE_PATTERN), y.format(DATE_PATTERN)];
    }
    case 'THIS_WEEK': {
      // dayjs 的 day()：0=周日 … 6=周六；换算成「距本周周一的天数」
      const weekday = base.day();
      const sinceMonday = weekday === 0 ? 6 : weekday - 1;
      const monday = base.subtract(sinceMonday, 'day');
      return [monday.format(DATE_PATTERN), base.format(DATE_PATTERN)];
    }
    case 'LAST_WEEK': {
      const weekday = base.day();
      const sinceMonday = weekday === 0 ? 6 : weekday - 1;
      const lastMonday = base.subtract(sinceMonday + 7, 'day');
      return [lastMonday.format(DATE_PATTERN), lastMonday.add(6, 'day').format(DATE_PATTERN)];
    }
    case 'THIS_MONTH':
      return [base.startOf('month').format(DATE_PATTERN), base.format(DATE_PATTERN)];
    case 'LAST_MONTH': {
      const lastMonth = base.subtract(1, 'month');
      return [lastMonth.startOf('month').format(DATE_PATTERN), lastMonth.endOf('month').format(DATE_PATTERN)];
    }
    default:
      return defaultDateRange(today);
  }
}

/** 页面初始区间：本月 1 日 ~ 今天。 */
export function defaultDateRange(today?: Date | string): DateRange {
  return presetRange(DEFAULT_DATE_PRESET, today);
}

/** 日期选择器的快捷选项。 */
export function datePresets(today?: Date | string): Array<{label: string; value: DateRange}> {
  return (Object.keys(DATE_PRESET_LABELS) as DatePresetKey[]).map((key) => ({
    label: DATE_PRESET_LABELS[key],
    value: presetRange(key, today),
  }));
}

/** 区间是否超过最大天数；超限返回提示文案，区间不完整返回空串。 */
export function rangeOverLimitError(range: DateRange | undefined | null, maxDays = MAX_REPORT_RANGE_DAYS): string {
  if (!range || !range[0] || !range[1]) {
    return '';
  }
  const days = day(range[1]).diff(day(range[0]), 'day') + 1;
  return days > maxDays ? `查询跨度最多 ${maxDays} 天，当前 ${days} 天，请缩小日期区间` : '';
}

/** 空值显示 —；数字字符串原样返回，不做二次格式化。 */
export function textOrDash(value: string | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  return value;
}

/** 计数的展示文本：null 显示 —，0 照常显示。 */
export function countText(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—';
  }
  return String(value);
}

/** 图表取数：定点数字符串转 number，非法值返回 null（不当 0，免得画成零值柱）。 */
export function chartValue(value: string | number | null | undefined): number | null {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  const num = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(num) ? num : null;
}

/** 成本列文本：没有成本权限时显示 —。后端也会把成本字段置空，这里再拦一道。 */
export function costText(value: string | null | undefined, canSeeCost: boolean): string {
  if (!canSeeCost) {
    return '—';
  }
  return textOrDash(value);
}

/** 有成本流水被跳过时，合计只是部分和，需要提示。 */
export function incompleteCostHint(missingCount: number | null | undefined, label: string): string {
  if (!missingCount || missingCount <= 0) {
    return '';
  }
  return `${label}已跳过 ${missingCount} 行无成本流水，不是全部${label}`;
}

/** 没有成本权限时移除成本列，而不是显示空列。 */
export function filterCostColumns<T extends object>(
  columns: T[],
  costIndexes: readonly string[],
  canSeeCost: boolean,
  dataIndexOf: (column: T) => unknown = (column) => (column as {dataIndex?: unknown}).dataIndex
): T[] {
  if (canSeeCost) {
    return columns;
  }
  return columns.filter((column) => {
    const index = dataIndexOf(column);
    return !(typeof index === 'string' && costIndexes.includes(index));
  });
}

/**
 * 由流水类型判断方向。
 * @param inboundTypes 「入」方向类型集合，与后端 getInbound() 同源
 */
export function movementDirection(movementType: string | null | undefined, inboundTypes: readonly string[]): 'IN' | 'OUT' | null {
  if (!movementType) {
    return null;
  }
  return inboundTypes.includes(movementType) ? 'IN' : 'OUT';
}

/** 方向文案；未知返回 —。 */
export function directionText(direction: 'IN' | 'OUT' | null): string {
  if (direction === 'IN') {
    return '入';
  }
  if (direction === 'OUT') {
    return '出';
  }
  return '—';
}

/** 枚举文案表的最小结构。 */
export type EnumLabels = Record<string, {desc?: string} | undefined>;

/** 枚举的中文描述；不认识的枚举保留原值，方便发现新增类型。 */
export function enumDescText(value: string | null | undefined, labels: EnumLabels): string {
  if (!value) {
    return '—';
  }
  return labels[value]?.desc || value;
}

/** 从 {value, label} 选项表取文案；找不到就保留原值。 */
export function optionLabel(options: ReadonlyArray<{value: string; label: string}>, value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  return options.find((option) => option.value === value)?.label ?? value;
}

/** 是/否文案；null 显示 —，和「否」区分开。 */
export function yesNoText(value: boolean | null | undefined): string {
  if (value === null || value === undefined) {
    return '—';
  }
  return value ? '是' : '否';
}

/** 每个标签页各自的分页、数据和状态；通用筛选由页面共享。 */
export interface TabView<T> {
  pageNum: number;
  pageSize: number;
  rows: T[];
  total: number;
  loading: boolean;
  error: string;
  /** 导出是否进行中；每个标签页各一份，避免整排按钮一起转 */
  exporting: boolean;
}

/** 新建标签页状态；rows 初始为空数组，模板不用判空。 */
export function createTabView<T>(pageSize = 20): TabView<T> {
  return {pageNum: 1, pageSize, rows: [], total: 0, loading: false, error: '', exporting: false};
}

/** 进入标签页：页码归 1，不动 pageSize 和其它标签页。 */
export function enterTab<T>(view: TabView<T>): TabView<T> {
  view.pageNum = 1;
  return view;
}

/** 查询体里 `undefined` / `null` / `''` 的字段都被省略。 */
type LooseFilters = Record<string, unknown>;

function omitEmpty(source: LooseFilters): LooseFilters {
  const result: LooseFilters = {};
  for (const [key, value] of Object.entries(source)) {
    if (value === undefined || value === null || value === '') {
      continue;
    }
    result[key] = value;
  }
  return result;
}

/** 组装查询体：日期 + 共享筛选，空值不发送；导出时不传分页。返回类型由调用方声明，函数本身不做形状校验。 */
export function buildReportQuery<T extends object>(
  range: DateRange | undefined | null,
  shared: LooseFilters,
  tab?: {pageNum: number; pageSize: number}
): T {
  // 日期区间最后写入：共享筛选里若混进同名字段（页面类型上不该出现），也不许盖掉区间
  const payload: LooseFilters = {...omitEmpty(shared)};
  if (range?.[0] && range?.[1]) {
    payload.startDate = range[0];
    payload.endDate = range[1];
  }
  if (tab) {
    payload.pageNum = tab.pageNum;
    payload.pageSize = tab.pageSize;
  }
  return payload as unknown as T;
}

/** 路由 query 的子集。 */
export type RouteQuery = Record<string, string | (string | null)[] | null | undefined>;

function firstQueryValue(raw: RouteQuery[string]): string {
  const value = Array.isArray(raw) ? raw.find((item) => item !== null) : raw;
  return typeof value === 'string' ? value.trim() : '';
}

/** 从路由 query 还原日期区间；两个日期都合法才生效，否则按未提供处理。 */
export function rangeFromQuery(query: RouteQuery | undefined | null): DateRange | undefined {
  const startDate = firstQueryValue(query?.startDate);
  const endDate = firstQueryValue(query?.endDate);
  const valid = /^\d{4}-\d{2}-\d{2}$/.test(startDate) && /^\d{4}-\d{2}-\d{2}$/.test(endDate);
  return valid ? [startDate, endDate] : undefined;
}

/** 某一天的区间：起止同一天。 */
export function dayRange(bizDate: string): DateRange {
  const normalized = /^\d{4}-\d{2}-\d{2}$/.test(bizDate) ? bizDate : defaultDateRange()[0];
  return [normalized, normalized];
}
