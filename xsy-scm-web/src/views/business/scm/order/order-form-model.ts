import Decimal from 'decimal.js';
import type {Address, Id, Item, Order, RecentPrice} from './order-types.ts';

export function newOrder(): Order {
  return {orderSource: 'ADMIN', deliveryWarehouseId: null, items: [], address: {receiverName: '', receiverPhone: '', address: ''}};
}

/** 连续录单只沿用客户和仓库，其余内容一律重新填写。 */
export function nextOrderForCustomer(previous: Order): Order {
  const next = newOrder();
  next.customerId = previous.customerId;
  next.deliveryWarehouseId = previous.deliveryWarehouseId ?? previous.mallDeliveryWarehouseId ?? null;
  return next;
}

export function fixed(value: string | number): string {
  return new Decimal(value).toDecimalPlaces(4, Decimal.ROUND_HALF_UP).toFixed(4);
}

/** 草稿明细行：只存用户输入的内容。 */
export interface DraftItem {
  skuId?: string | number;
  orderedQuantity: string;
  manualPriceOverride: boolean;
  unitPrice?: string | null;
  overrideReason?: string | null;
}

/** 本地草稿（存在 localStorage）。不含价格解析结果、库存、版本等，恢复后重新向服务端获取。 */
export interface Draft {
  customerId?: string | number;
  deliveryWarehouseId?: string | number | null;
  orderSource: string;
  expectDeliveryTime?: string | null;
  remark?: string | null;
  supplementReason?: string | null;
  originalOrderId?: string | number | null;
  items: DraftItem[];
}

/** 草稿存储键按登录用户隔离，避免不同操作员的草稿互串。 */
export function draftKey(employeeId: string | number): string {
  return `xsy-scm:order-draft:${employeeId}`;
}

/** 把新建表单转成草稿，只挑用户输入字段，不存解析价和版本。 */
export function serializeDraft(form: Order): Draft {
  return {
    customerId: form.customerId,
    deliveryWarehouseId: form.deliveryWarehouseId ?? null,
    orderSource: form.orderSource,
    expectDeliveryTime: form.expectDeliveryTime ?? null,
    remark: form.remark ?? null,
    supplementReason: form.orderSource === 'SUPPLEMENT' ? form.supplementReason ?? null : null,
    originalOrderId: form.orderSource === 'SUPPLEMENT' ? form.originalOrderId ?? null : null,
    items: form.items
      .filter((i) => i.skuId != null || (i.orderedQuantity && i.orderedQuantity !== ''))
      .map((i) => ({
        skuId: i.skuId,
        orderedQuantity: i.orderedQuantity,
        manualPriceOverride: !!i.manualPriceOverride,
        unitPrice: i.manualPriceOverride ? i.unitPrice ?? null : null,
        overrideReason: i.manualPriceOverride ? i.overrideReason ?? null : null,
      })),
  };
}

/** 从草稿还原表单，只回填用户输入；价格和地址由调用方重新获取。 */
export function applyDraft(draft: Draft): Order {
  const form = newOrder();
  form.customerId = draft.customerId;
  form.deliveryWarehouseId = draft.deliveryWarehouseId ?? null;
  form.orderSource = draft.orderSource || 'ADMIN';
  form.expectDeliveryTime = draft.expectDeliveryTime ?? null;
  form.remark = draft.remark ?? null;
  form.supplementReason = draft.supplementReason ?? null;
  form.originalOrderId = draft.originalOrderId ?? null;
  form.items = (draft.items ?? []).map((i) => ({
    skuId: i.skuId,
    orderedQuantity: i.orderedQuantity,
    manualPriceOverride: !!i.manualPriceOverride,
    unitPrice: i.manualPriceOverride ? i.unitPrice ?? null : null,
    overrideReason: i.manualPriceOverride ? i.overrideReason ?? null : null,
  }));
  return form;
}

/** 复制历史订单只生成新草稿，不带单号、状态、原价格和库存预留。 */
export function fromHistory(detail: Order): Order {
  const form = newOrder();
  form.customerId = detail.customerId;
  form.deliveryWarehouseId = detail.mallDeliveryWarehouseId ?? null;
  form.expectDeliveryTime = detail.expectDeliveryTime ?? null;
  form.remark = detail.remark ?? null;
  const addr: Address = detail.address
    ? {
        receiverName: detail.address.receiverName ?? '',
        receiverPhone: detail.address.receiverPhone ?? '',
        address: detail.address.address ?? '',
      }
    : form.address;
  form.address = addr;
  form.items = (detail.items ?? []).map(
    (i): Item => ({
      skuId: i.skuId,
      orderedQuantity: i.orderedQuantity,
      manualPriceOverride: false,
      unitPrice: null,
      overrideReason: null,
    })
  );
  return form;
}

export function validateOrder(f: Order): string | undefined {
  if (!f.customerId) return '请选择客户';
  if (!f.items.length) return '请添加商品';
  if (!f.address.receiverName || !f.address.receiverPhone || !f.address.address) return '请填写完整收货信息';
  if (f.orderSource === 'SUPPLEMENT' && !f.supplementReason?.trim()) return '请填写补单原因';
  const seen = new Set<string>();
  for (const i of f.items) {
    if (!i.skuId) return '请选择商品规格';
    if (seen.has(String(i.skuId))) return '同一商品规格不能重复';
    seen.add(String(i.skuId));
    if (!/^\d{1,14}\.\d{4}$/.test(i.orderedQuantity) || new Decimal(i.orderedQuantity).lte(0)) return '数量必须为正的四位定点数';
    if (i.manualPriceOverride && (!i.overrideReason?.trim() || i.unitPrice == null || !/^\d{1,14}\.\d{4}$/.test(i.unitPrice)))
      return '人工改价需要有效价格和原因';
  }
  return undefined;
}

export function payload(f: Order): Order {
  return {
    ...f,
    items: f.items.map((i, index) => ({
      itemId: i.itemId,
      version: i.version,
      skuId: i.skuId,
      orderedQuantity: fixed(i.orderedQuantity),
      manualPriceOverride: i.manualPriceOverride,
      unitPrice: i.manualPriceOverride && i.unitPrice != null ? fixed(i.unitPrice) : null,
      overrideReason: i.manualPriceOverride ? i.overrideReason : null,
      sortOrder: index,
    })),
  };
}

export function amount(value: string | null | undefined, unpriced = false): string {
  return value == null ? (unpriced ? '未定价' : '—') : '¥ ' + fixed(value);
}

/** 历史价缓存。缓存键只用客户 + 规格，不能带行号，否则换商品或删行后会显示错价格。 */
export interface RecentPriceCache {
  loadingKey: string | null;
  rowsByKey: Record<string, RecentPrice[]>;
}

export function createRecentPriceCache(): RecentPriceCache {
  return {loadingKey: null, rowsByKey: {}};
}

/** 缓存键 = 客户 + SKU；任一缺失即无键可查，返回 null 让调用方不发起请求。 */
export function recentPriceKey(customerId: Id | null | undefined, skuId: Id | null | undefined): string | null {
  if (customerId == null || customerId === '' || skuId == null || skuId === '') {
    return null;
  }
  return `${customerId}:${skuId}`;
}

/** 是否已查过（查过但没有结果也算），用于区分空数据和未加载。 */
export function hasRecentPrices(cache: RecentPriceCache, customerId: Id | null | undefined, skuId: Id | null | undefined): boolean {
  const key = recentPriceKey(customerId, skuId);
  return key != null && cache.rowsByKey[key] != null;
}

export function recentPriceRows(cache: RecentPriceCache, customerId: Id | null | undefined, skuId: Id | null | undefined): RecentPrice[] {
  const key = recentPriceKey(customerId, skuId);
  return (key != null && cache.rowsByKey[key]) || [];
}

export function isLoadingRecentPrice(cache: RecentPriceCache, customerId: Id | null | undefined, skuId: Id | null | undefined): boolean {
  const key = recentPriceKey(customerId, skuId);
  return key != null && cache.loadingKey === key;
}

/** 取历史价：有缓存直接返回；请求失败会清掉加载态，下次还能重试。 */
export async function loadRecentPrices(
  cache: RecentPriceCache,
  customerId: Id | null | undefined,
  skuId: Id | null | undefined,
  fetcher: () => Promise<RecentPrice[]>
): Promise<boolean> {
  const key = recentPriceKey(customerId, skuId);
  if (!key) {
    return false;
  }
  if (cache.rowsByKey[key]) {
    return true;
  }
  cache.loadingKey = key;
  try {
    cache.rowsByKey[key] = await fetcher();
    return true;
  } finally {
    if (cache.loadingKey === key) {
      cache.loadingKey = null;
    }
  }
}
