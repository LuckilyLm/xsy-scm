import Decimal from 'decimal.js';
import type {Address, Id, Item, LastPurchasePrice, Order, RecentPrice} from './order-types.ts';

/** 本模块会被 node 直接加载，不能使用相对路径的值导入，精度归一在这里就地实现。 */
function roundScale(value: string | number | null | undefined, scale: number): string {
  if (value === null || value === undefined || value === '') return '';
  try {
    return new Decimal(value).toDecimalPlaces(scale, Decimal.ROUND_HALF_UP).toFixed(scale);
  } catch {
    return '';
  }
}

function normalizeMoneyInput(value: string | number | null | undefined): string {
  return roundScale(value, 2);
}

/** 人工售价允许两位新录入值，也放行历史四位值；三位和五位以上都拒绝。 */
function isSalesMoney(value: string | null | undefined): boolean {
  if (value === null || value === undefined || value === '') return false;
  try {
    const parsed = new Decimal(value);
    const places = parsed.decimalPlaces();
    return parsed.isFinite() && !parsed.isNegative() && (places <= 2 || places === 4);
  } catch {
    return false;
  }
}

function moneyPayload(value: string | number | null | undefined): string {
  const normalized = normalizeMoneyInput(value);
  return normalized === '' ? '' : roundScale(normalized, 4);
}

function groupMoney(value: string): string {
  const negative = value.startsWith('-');
  const unsigned = negative ? value.slice(1) : value;
  const [whole, fraction] = unsigned.split('.');
  return `${negative ? '-' : ''}${whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${fraction}`;
}

/** 现场按“当天录单、次日配送”作业；默认次日 08:00，仍可手改。 */
function defaultDeliveryTime(): string {
  const date = new Date();
  date.setDate(date.getDate() + 1);
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} 08:00:00`;
}

export function newOrder(): Order {
  return {
    orderSource: 'ADMIN',
    deliveryWarehouseId: null,
    expectDeliveryTime: defaultDeliveryTime(),
    items: [],
    address: {receiverName: '', receiverPhone: '', address: ''},
  };
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

/** 录单区合计数量：只累加正在录入的下单数量，非法录入值跳过，保存时由校验统一报错。 */
export function sumOrderedQuantity(items: {orderedQuantity?: string | number | null}[]): string {
  let total = new Decimal(0);
  for (const item of items) {
    if (item.orderedQuantity === null || item.orderedQuantity === undefined || item.orderedQuantity === '') continue;
    try {
      total = total.plus(new Decimal(item.orderedQuantity));
    } catch {
      // 录入中的非法数量不参与合计
    }
  }
  return total.toDecimalPlaces(4, Decimal.ROUND_HALF_UP).toFixed(4);
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
    if (i.manualPriceOverride && (!i.overrideReason?.trim() || !isSalesMoney(i.unitPrice))) {
      return '人工改价需要非负价格和原因';
    }
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
      unitPrice: i.manualPriceOverride && i.unitPrice != null ? moneyPayload(i.unitPrice) : null,
      overrideReason: i.manualPriceOverride ? i.overrideReason : null,
      sortOrder: index,
    })),
  };
}

export function amount(value: string | null | undefined, unpriced = false): string {
  if (value == null || value === '') return unpriced ? '未定价' : '—';
  const fixed = roundScale(value, 2);
  return fixed === '' ? '—' : `¥ ${groupMoney(fixed)}`;
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

/** 最近采购价缓存。键只用商品规格，不掺客户或行号：同一规格在不同客户下是同一个采购事实。 */
export interface LastPurchasePriceCache {
  loadingKey: string | null;
  rowsBySkuId: Record<string, LastPurchasePrice | null>;
}

export function createLastPurchasePriceCache(): LastPurchasePriceCache {
  return {loadingKey: null, rowsBySkuId: {}};
}

export function lastPurchasePriceKey(skuId: Id | null | undefined): string | null {
  if (skuId == null || skuId === '') {
    return null;
  }
  return String(skuId);
}

/** 查过但没有采购记录也算已查过（缓存值是 null），用来区分空数据和未加载。 */
export function hasLastPurchasePrice(cache: LastPurchasePriceCache, skuId: Id | null | undefined): boolean {
  const key = lastPurchasePriceKey(skuId);
  return key != null && key in cache.rowsBySkuId;
}

export function lastPurchasePrice(cache: LastPurchasePriceCache, skuId: Id | null | undefined): LastPurchasePrice | null {
  const key = lastPurchasePriceKey(skuId);
  return (key != null && cache.rowsBySkuId[key]) || null;
}

export function isLoadingLastPurchasePrice(cache: LastPurchasePriceCache, skuId: Id | null | undefined): boolean {
  const key = lastPurchasePriceKey(skuId);
  return key != null && cache.loadingKey === key;
}

/** 取最近采购价：有缓存直接返回；请求失败会清掉加载态，下次还能重试。 */
export async function loadLastPurchasePrice(
  cache: LastPurchasePriceCache,
  skuId: Id | null | undefined,
  fetcher: () => Promise<LastPurchasePrice | null>
): Promise<boolean> {
  const key = lastPurchasePriceKey(skuId);
  if (!key || key in cache.rowsBySkuId || cache.loadingKey === key) {
    return key != null && key in cache.rowsBySkuId;
  }
  cache.loadingKey = key;
  try {
    cache.rowsBySkuId[key] = (await fetcher()) ?? null;
    return true;
  } finally {
    if (cache.loadingKey === key) {
      cache.loadingKey = null;
    }
  }
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
