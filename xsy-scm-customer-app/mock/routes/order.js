/*
 * Mock 契约层 · 结算预览与下单
 *
 * 与后端 /scm/mall/orders/** 同构：预览返回明细 + 价格指纹；提交必须回传同一指纹，否则拒绝。
 * 仅用于开发环境（VITE_APP_USE_MOCK=true）。
 */
import { currentCustomer, fail, ok } from '../helpers';
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
  const wanted = new Map((form?.items || []).map((item) => [Number(item.skuId), String(item.quantity)]));
  return mockCartItems(session)
    .filter((item) => wanted.has(item.skuId))
    .map((item) => ({ ...item, quantity: wanted.get(item.skuId) }));
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

export const orderRoutes = [
  {
    method: 'POST',
    path: '/scm/mall/orders/preview',
    handler: async ({ data, session }) => guard(session) || ok(buildPreview(session, data)),
  },
  {
    method: 'POST',
    path: '/scm/mall/orders',
    handler: async ({ data, session }) => {
      const denied = guard(session);
      if (denied) return denied;
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
      mockCartRemove(session, preview.items.map((item) => item.skuId));
      return ok({
        orderId: orderSeq,
        orderNo,
        status: 'CONFIRMED',
        orderSource: 'MALL',
        orderedTotalAmount: preview.estimatedAmount,
        createdAt: new Date().toISOString(),
      });
    },
  },
];
