/*
 * Mock 契约层 · 购物车
 *
 * 与后端 /scm/mall/cart/** 同构：服务端购物车、数量为字符串定点、失效行保留并带 availability。
 * 仅用于开发环境（VITE_APP_USE_MOCK=true）；关闭后走真实后端。
 */
import { currentCustomer, fail, ok } from '../helpers';
import { findProduct } from '../fixtures';
import { PLATFORM_ERROR_CODE } from '@/constants/error-code-const';

/** 内存购物车：accountKey -> Map<skuId, quantity(string)> */
const CARTS = new Map();

function accountKey(session) {
  return String(session.loginPhone || session.customerId || 'anon');
}

function cartOf(session) {
  const key = accountKey(session);
  if (!CARTS.has(key)) {
    CARTS.set(key, new Map());
  }
  return CARTS.get(key);
}

function toItem(skuId, quantity) {
  const product = findProduct(skuId);
  if (!product) {
    return { skuId, quantity, availability: 'UNAVAILABLE', unavailableReason: 'NOT_VISIBLE' };
  }
  const priced = product.unitPrice !== null && product.unitPrice !== undefined && product.unitPrice !== '';
  return {
    skuId,
    productName: product.productName,
    skuCode: product.skuCode,
    specName: product.specName,
    saleUnit: product.saleUnit,
    primaryImageUrl: product.primaryImageUrl,
    unitPrice: product.unitPrice,
    priceStatus: product.priceStatus,
    priceSource: product.priceSource,
    quantity,
    lineAmount: priced ? (Number(product.unitPrice) * Number(quantity)).toFixed(4) : null,
    availability: priced ? 'AVAILABLE' : 'NO_PRICE',
    unavailableReason: priced ? null : 'NO_PRICE_SOURCE',
  };
}

function snapshot(session) {
  const items = [...cartOf(session).entries()].map(([skuId, quantity]) => toItem(skuId, quantity));
  const available = items.filter((item) => item.availability === 'AVAILABLE');
  const estimated = available.reduce((sum, item) => sum + Number(item.lineAmount), 0);
  return {
    items,
    estimatedAmount: estimated.toFixed(4),
    availableCount: available.length,
    totalCount: items.length,
  };
}

function guard(session) {
  return session ? null : fail(PLATFORM_ERROR_CODE.LOGIN_STATE_INVALID, '登录已失效，请重新登录');
}

/** 数量校验与后端一致：大于 0、最多 4 位小数 */
function validQuantity(raw) {
  return typeof raw === 'string' && /^\d{1,10}(\.\d{1,4})?$/.test(raw) && Number(raw) > 0;
}

export const cartRoutes = [
  {
    method: 'GET',
    path: '/scm/mall/cart',
    handler: async ({ session }) => guard(session) || ok(snapshot(session)),
  },
  {
    method: 'POST',
    path: '/scm/mall/cart/items',
    handler: async ({ data, session }) => {
      const denied = guard(session);
      if (denied) return denied;
      const skuId = Number(data?.skuId);
      const quantity = String(data?.quantity ?? '');
      if (!validQuantity(quantity)) {
        return fail(40082, '数量必须为大于0、最多4位小数的数字');
      }
      const cart = cartOf(session);
      const current = Number(cart.get(skuId) || 0);
      cart.set(skuId, (current + Number(quantity)).toFixed(4));
      return ok(snapshot(session));
    },
  },
  {
    method: 'PUT',
    path: '/scm/mall/cart/items/:skuId',
    handler: async ({ params, data, session }) => {
      const denied = guard(session);
      if (denied) return denied;
      const skuId = Number(params.skuId);
      const quantity = String(data?.quantity ?? '');
      if (!validQuantity(quantity)) {
        return fail(40082, '数量必须为大于0、最多4位小数的数字');
      }
      const cart = cartOf(session);
      if (!cart.has(skuId)) {
        return fail(40483, '购物车中没有该商品');
      }
      cart.set(skuId, Number(quantity).toFixed(4));
      return ok(snapshot(session));
    },
  },
  {
    method: 'DELETE',
    path: '/scm/mall/cart/items/:skuId',
    handler: async ({ params, session }) => {
      const denied = guard(session);
      if (denied) return denied;
      cartOf(session).delete(Number(params.skuId));
      return ok(snapshot(session));
    },
  },
  {
    method: 'POST',
    path: '/scm/mall/cart/items/remove',
    handler: async ({ data, session }) => {
      const denied = guard(session);
      if (denied) return denied;
      const cart = cartOf(session);
      (data?.skuIds || []).forEach((skuId) => cart.delete(Number(skuId)));
      return ok(snapshot(session));
    },
  },
  {
    method: 'DELETE',
    path: '/scm/mall/cart',
    handler: async ({ session }) => {
      const denied = guard(session);
      if (denied) return denied;
      cartOf(session).clear();
      return ok(snapshot(session));
    },
  },
];
