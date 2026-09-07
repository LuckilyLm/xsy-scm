import type { OrderItem, SalesOrder, SalesOrderPayload, SkuSaleOption, OrderSource, PriceSource } from '../../types/sales';

export interface OrderFormItem extends OrderItem { key: string; }
export interface OrderForm {
  id: number | null;
  version: number | null;
  customerId: number | null;
  customerName: string;
  source: OrderSource;
  supplementReason: string;
  originalOrderId: number | null;
  items: OrderFormItem[];
}

let nextKey = 0;
const key = () => `order-item-${++nextKey}`;
export const decimalPattern = /^\d+(\.\d{1,4})?$/;

export function createEmptyOrderForm(source: OrderSource = 'NORMAL'): OrderForm {
  return { id: null, version: null, customerId: null, customerName: '', source, supplementReason: '', originalOrderId: null, items: [] };
}

export function orderDetailToForm(order: SalesOrder): OrderForm {
  return {
    id: order.id, version: order.version, customerId: order.customerId, customerName: order.customerName,
    source: order.source, supplementReason: order.supplementReason ?? '', originalOrderId: order.originalOrderId,
    items: order.items.map((item) => ({ ...item, specValues: { ...item.specValues }, key: item.id === null ? key() : `persisted-${item.id}` })),
  };
}

export function addOrderItem(form: OrderForm, sku: SkuSaleOption): OrderForm {
  if (form.items.some((item) => item.skuId === sku.skuId)) return form;
  const item: OrderFormItem = {
    key: key(), id: null, version: null, skuId: sku.skuId, productId: 0, skuCode: sku.skuCode,
    productName: sku.productName, specName: '', specValues: {}, saleUnit: sku.saleUnit,
    productType: sku.productType, orderedQuantity: '1.0000', actualQuantity: null,
    unitPrice: sku.marketPrice, lockedUnitPrice: null, priceSource: 'MARKET', priceSourceId: null,
    amount: null, overrideReason: null,
  };
  return { ...form, items: [...form.items, item] };
}

export function updateOrderItem(form: OrderForm, itemKey: string, patch: Partial<OrderFormItem>): OrderForm {
  return { ...form, items: form.items.map((item) => item.key === itemKey ? { ...item, ...patch } : item) };
}

export function setManualPrice(form: OrderForm, itemKey: string, unitPrice: string, reason: string): OrderForm {
  return updateOrderItem(form, itemKey, { unitPrice, priceSource: 'OVERRIDE' as PriceSource, priceSourceId: null, overrideReason: reason });
}

export function resetResolvedPrice(form: OrderForm, itemKey: string, unitPrice: string): OrderForm {
  return updateOrderItem(form, itemKey, { unitPrice, priceSource: 'MARKET', priceSourceId: null, overrideReason: null });
}

export function removeOrderItem(form: OrderForm, itemKey: string): OrderForm {
  return { ...form, items: form.items.filter((item) => item.key !== itemKey) };
}

export function validateOrderForm(form: OrderForm): string | null {
  if (form.customerId === null) return '请选择客户';
  if (form.source === 'SUPPLEMENT' && !form.supplementReason.trim()) return '补单必须填写补单原因';
  if (!form.items.length) return '请至少添加一个商品';
  for (const item of form.items) {
    if (!decimalPattern.test(item.orderedQuantity) || Number(item.orderedQuantity) <= 0) return '订购数量应为大于零的数字，最多四位小数';
    if (!decimalPattern.test(item.unitPrice)) return '单价应为非负数字，最多四位小数';
    if (item.priceSource === 'OVERRIDE' && !item.overrideReason?.trim()) return '人工改价必须填写原因';
  }
  return null;
}

export function toOrderPayload(form: OrderForm): SalesOrderPayload {
  return {
    version: form.version, customerId: form.customerId!, source: form.source,
    supplementReason: form.source === 'SUPPLEMENT' ? form.supplementReason.trim() : null,
    originalOrderId: form.source === 'SUPPLEMENT' ? form.originalOrderId : null,
    items: form.items.map((item) => ({
      id: item.id,
      version: item.version,
      skuId: item.skuId,
      orderedQuantity: item.orderedQuantity.trim(),
      unitPrice: item.priceSource === 'OVERRIDE' ? item.unitPrice.trim() : null,
      manualPriceOverride: item.priceSource === 'OVERRIDE',
      overrideReason: item.priceSource === 'OVERRIDE' ? item.overrideReason?.trim() || null : null,
    })),
  };
}
