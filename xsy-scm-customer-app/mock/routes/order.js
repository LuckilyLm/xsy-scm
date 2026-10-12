/*
 * Mock 契约层 · 结算预览与下单
 *
 * 与后端 /scm/mall/orders/** 同构：预览返回明细 + 价格指纹；提交必须回传同一指纹，否则拒绝。
 * 仅用于开发环境（VITE_APP_USE_MOCK=true）。
 */
import { fail, ok, paginate } from '../helpers';
import { mockCartItems, mockCartRemove } from './cart';
import { mockAddressList } from './address';
import { PLATFORM_ERROR_CODE } from '@/constants/error-code-const';

/** 价格指纹：与后端同构的「按 skuId 排序的 skuId:quantity:unitPrice 摘要」 */
function fingerprint(items) {
  return items
    .slice()
    .sort((a, b) => a.skuId - b.skuId)
    .map((item) => `${item.skuId}:${item.quantity}:${item.unitPrice == null ? 'null' : item.unitPrice}`)
    .join(';');
}

function guard(session) {
  return session ? null : fail(PLATFORM_ERROR_CODE.LOGIN_STATE_INVALID, '登录已失效，请重新登录');
}

function selectedItems(session, form) {
  const cartBySku = new Map(mockCartItems(session).map((item) => [item.skuId, item]));
  return (form?.items || []).map((requested) => {
    const skuId = Number(requested.skuId);
    const item = cartBySku.get(skuId);
    return item
      ? { ...item, quantity: String(requested.quantity), lineAmount: item.unitPrice == null ? null : (Number(item.unitPrice) * Number(requested.quantity)).toFixed(4) }
      : { skuId, quantity: String(requested.quantity), availability: 'UNAVAILABLE', unavailableReason: 'NOT_VISIBLE' };
  });
}

function buildPreview(session, form) {
  const items = selectedItems(session, form);
  const available = items.filter((item) => item.availability === 'AVAILABLE');
  const estimated = available.reduce((sum, item) => sum + Number(item.lineAmount), 0);
  const address = form?.addressId
    ? mockAddressList(session).find((item) => item.addressId === Number(form.addressId)) || null
    : null;
  return {
    items,
    address,
    estimatedAmount: estimated.toFixed(4),
    availableCount: available.length,
    totalCount: items.length,
    unavailableSkuIds: items.filter((item) => item.availability !== 'AVAILABLE').map((item) => item.skuId),
    priceFingerprint: fingerprint(items),
  };
}

let orderSeq = 0;
const ORDERS = new Map();

function accountKey(session) {
  return String(session.accountId || session.loginPhone || session.customerId);
}

function accountOrders(session) {
  return ORDERS.get(accountKey(session)) || [];
}

const IDEMPOTENT_ORDERS = new Map();

export const orderRoutes = [
  {
    method: 'GET',
    path: '/scm/mall/orders',
    handler: async ({ data, session }) => guard(session) || ok(paginate(accountOrders(session), data)),
  },
  {
    method: 'GET',
    path: '/scm/mall/orders/:orderId',
    handler: async ({ params, session }) => {
      const denied = guard(session);
      if (denied) return denied;
      const order = accountOrders(session).find((item) => item.orderId === Number(params.orderId));
      return order ? ok(order) : fail(40489, '订单不存在');
    },
  },
  {
    method: 'POST',
    path: '/scm/mall/orders/preview',
    handler: async ({ data, session }) => guard(session) || ok(buildPreview(session, data)),
  },
  {
    method: 'POST',
    path: '/scm/mall/orders',
    handler: async ({ data, session, header }) => {
      const denied = guard(session);
      if (denied) return denied;
      const idempotencyKey = header?.['Idempotency-Key'];
      if (!idempotencyKey) return fail(40086, '缺少幂等键，无法安全提交订单');
      const replayKey = `${accountKey(session)}:${idempotencyKey || ''}`;
      const requestSignature = JSON.stringify(data || {});
      const previous = IDEMPOTENT_ORDERS.get(replayKey);
      if (previous) {
        return previous.requestSignature === requestSignature
          ? ok(previous.order)
          : fail(40966, '幂等键已用于不同的下单内容');
      }
      const preview = buildPreview(session, data);
      if (!preview.address) {
        return fail(40085, '请选择收货地址');
      }
      if (preview.unavailableSkuIds.length) {
        return fail(40984, '所选商品中有不可售项，请返回购物车调整');
      }
      if (preview.priceFingerprint !== String(data?.priceFingerprint || '')) {
        return fail(40985, '商品价格已变化，请重新确认结算金额');
      }
      const orderNo = `MO${Date.now()}${(orderSeq += 1)}`;
      const address = preview.address;
      const order = {
        orderId: orderSeq,
        orderNo,
        status: 'CONFIRMED',
        orderSource: 'MALL',
        orderedTotalAmount: preview.estimatedAmount,
        settlementTotalAmount: null,
        expectDeliveryTime: data?.expectDeliveryDate ? `${data.expectDeliveryDate}T00:00:00+08:00` : null,
        remark: data?.remark || null,
        createdAt: new Date().toISOString(),
        receiverName: address.receiverName,
        receiverPhone: address.receiverPhone,
        address: [address.provinceName, address.cityName, address.districtName, address.detailAddress].filter(Boolean).join(' '),
        items: preview.items.map((item, index) => ({
          itemId: index + 1,
          skuId: item.skuId,
          productName: item.productName,
          specName: item.specName,
          saleUnit: item.saleUnit,
          productType: item.nonStandard ? 'NON_STANDARD' : 'STANDARD',
          orderedQuantity: item.quantity,
          actualQuantity: null,
          unitPrice: item.unitPrice,
          orderedLineAmount: item.lineAmount,
        })),
      };
      ORDERS.set(accountKey(session), [order, ...accountOrders(session)]);
      IDEMPOTENT_ORDERS.set(replayKey, { requestSignature, order });
      mockCartRemove(session, preview.items.map((item) => item.skuId));
      return ok(order);
    },
  },
];
