import Decimal from 'decimal.js';
import type {ResolvedPrice} from '/@/types/business/scm/pricing';
import type {Item, Order} from './order-types';

export type PriceCheckState = 'UP' | 'DOWN' | 'SAME' | 'NEW' | 'MANUAL' | 'BLOCKED';
export interface PriceCheckLine {
  itemId?: string | number;
  skuId?: string | number;
  productName: string;
  skuCode: string;
  quantity: string;
  oldPrice: string | null;
  currentPrice: string | null;
  priceSource: string | null;
  state: PriceCheckState;
  reason: string;
}
export interface PriceCheckResult {
  orderId: string | number;
  orderNo: string;
  version: number;
  customerName: string;
  lines: PriceCheckLine[];
  changed: number;
  blocked: number;
}

/** 只读核价：来源单价、人工改价和旧订单对象都不修改。 */
export function compareOrderPrices(order: Order, preview: ResolvedPrice[]): PriceCheckResult {
  if (!order.orderId || order.status !== 'DRAFT' || order.version == null) {
    throw new Error('仅可预览有效草稿订单');
  }
  const resolved = new Map(preview.map((item) => [String(item.skuId), item]));
  const lines = (order.items ?? []).map((item: Item): PriceCheckLine => {
    const price = item.skuId == null ? undefined : resolved.get(String(item.skuId));
    const original = item.draftUnitPrice ?? null;
    const base = {
      itemId: item.itemId,
      skuId: item.skuId,
      productName: item.productNameSnapshot || price?.productName || '未命名商品',
      skuCode: item.skuCodeSnapshot || price?.skuCode || '—',
      quantity: item.orderedQuantity,
      oldPrice: original,
      currentPrice: price?.unitPrice ?? null,
      priceSource: price?.priceSource ?? null,
    };
    if (item.manualPriceOverride) {
      return {...base, currentPrice: original, state: 'MANUAL', reason: '人工改价，保持原价'};
    }
    if (!price || !price.sellable || price.priceStatus !== 'PRICED' || price.unitPrice == null) {
      return {...base, state: 'BLOCKED', reason: price?.unavailableReason || price?.unpricedReason || '当前无可用价格'};
    }
    if (original == null) return {...base, state: 'NEW', reason: '原单未定价'};
    const difference = new Decimal(price.unitPrice).comparedTo(new Decimal(original));
    return {...base, state: difference > 0 ? 'UP' : difference < 0 ? 'DOWN' : 'SAME', reason: ''};
  });
  return {
    orderId: order.orderId,
    orderNo: order.orderNo || String(order.orderId),
    version: order.version,
    customerName: order.customerNameSnapshot || '—',
    lines,
    changed: lines.filter((line) => ['UP', 'DOWN', 'NEW'].includes(line.state)).length,
    blocked: lines.filter((line) => line.state === 'BLOCKED').length,
  };
}

export function canApplyPriceCheck(result: PriceCheckResult): boolean {
  return result.changed > 0 && result.blocked === 0;
}
