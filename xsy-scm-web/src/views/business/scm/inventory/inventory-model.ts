/**
 * 库存展示和表单处理的公共函数。
 * 不依赖 Vue 或接口调用，方便单独测试。
 */
import type {Id} from './inventory-types.ts';

/** 只用到仓库 id。 */
export interface WarehouseLike {
  id: Id;
}

/**
 * 只有一个启用仓库时返回它的 id，否则返回 undefined。
 * 多仓或零仓时不自动选中，避免用户误以为看到的是全部库存。
 *
 * @param warehouses 启用仓库列表
 */
export function singleWarehouseDefault(warehouses: WarehouseLike[] | null | undefined): Id | undefined {
  if (!warehouses || warehouses.length !== 1) {
    return undefined;
  }
  return warehouses[0].id;
}

/** 空值显示为“—”，其余原样显示（后端已是 4 位定点数，前端不再格式化）。 */
export function quantityText(value: string | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  return value;
}

/** 金额为空显示“—”，0 仍显示 0。 */
export function moneyText(value: string | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  return value;
}

/** 规格值转成可读文本；无值或空对象显示“—”。规格损坏时不报错，避免整页崩溃。 */
export function specText(specValues: Record<string, unknown> | null | undefined): string {
  if (!specValues) {
    return '—';
  }
  const pairs = Object.entries(specValues).map(([key, value]) => `${key}：${String(value)}`);
  return pairs.length ? pairs.join('，') : '—';
}

/**
 * 规格名称：优先显示规格值组合，没有时显示规格名称。
 * 注意 `specText` 无值返回“—”，不能用 `||` 回落。
 */
export function skuMainText(specValues: Record<string, unknown> | null | undefined, skuName?: string | null): string {
  const spec = specText(specValues);
  if (spec !== '—') {
    return spec;
  }
  return skuName || '—';
}

/** 枚举文案表的最小结构。 */
export type EnumLabels = Record<string, {desc?: string} | undefined>;
/** 流水类型名称；未知类型显示原值，便于发现后端新增的类型。 */
export function movementTypeText(value: string | null | undefined, labels: EnumLabels): string {
  if (!value) {
    return '—';
  }
  return labels[value]?.desc || value;
}

/** 复制盘点时每页查询的商品规格数量。 */
export const BALANCE_QUERY_PAGE_SIZE = 100;

/** 余额行里只取编码和单位，不读账面量。 */
export interface BalanceUnitRow {
  skuId?: string | number | null;
  unit?: string | null;
}

export interface StocktakeCopyUnits {
  /** 来源 SKU → 当前记账单位；未找到的不在这里。 */
  unitBySku: Map<string, string>;
  /** 翻完所有页仍找不到余额行的来源 SKU（已去重）。 */
  missingSkuIds: string[];
}

export async function resolveStocktakeCopyUnits(
  sourceSkuIds: Array<string | number | null | undefined>,
  fetchPage: (pageNum: number, pageSize: number) => Promise<BalanceUnitRow[] | undefined>
): Promise<StocktakeCopyUnits> {
  const pending = new Set<string>();
  for (const skuId of sourceSkuIds) {
    // 空 skuId 不参与解析，由调用方逐行校验
    if (skuId !== null && skuId !== undefined && skuId !== '') {
      pending.add(String(skuId));
    }
  }
  const unitBySku = new Map<string, string>();
  let pageNum = 1;
  for (;;) {
    const rows = (await fetchPage(pageNum, BALANCE_QUERY_PAGE_SIZE)) ?? [];
    for (const row of rows) {
      if (row.skuId === null || row.skuId === undefined) {
        continue;
      }
      const key = String(row.skuId);
      if (pending.delete(key)) {
        unitBySku.set(key, row.unit ?? '');
      }
    }
    // 全部找到，或已到最后一页
    if (pending.size === 0 || rows.length < BALANCE_QUERY_PAGE_SIZE) {
      break;
    }
    pageNum += 1;
  }
  return {unitBySku, missingSkuIds: [...pending]};
}
