/*
 * Mock 契约层 · 结算、下单与订单中心
 *
 * 与后端 /scm/mall/orders/** 同构：预览返回明细 + 价格指纹；提交必须回传同一指纹；
 * 含非标品时订单停留在 PENDING（与后端 createAndProgress 一致），全标品才自动确认。
 * 仅用于开发环境（VITE_APP_USE_MOCK=true）。
 */
import { fail, ok, paginate } from '../helpers';
import { mockCartItems, mockCartRemove, mockCartSet } from './cart';
import { mockAddressList } from './address';
import { findProduct } from '../fixtures';
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

/** 与后端 MallOrderDisplayStatusEnum 同构的展示分类（mock 无配送事实，一律未发车） */
function displayStatusOf(status) {
  if (status === 'CANCELLED') return 'CANCELLED';
  if (status === 'DRAFT' || status === 'PENDING') return 'PENDING';
  return 'PREPARING';
}

function selectedItems(session, form) {
  const cartBySku = new Map(mockCartItems(session).map((item) => [item.skuId, item]));
  return (form?.items || []).map((requested) => {
    const skuId = Number(requested.skuId);
    const item = cartBySku.get(skuId);
    return item
      ? {
          ...item,
          quantity: String(requested.quantity),
          lineAmount: item.unitPrice == null ? null : (Number(item.unitPrice) * Number(requested.quantity)).toFixed(4),
        }
      : { skuId, quantity: String(requested.quantity), availability: 'UNAVAILABLE', unavailableReason: 'NOT_VISIBLE' };
  });
}

function buildPreview(session, form) {
  const items = selectedItems(session, form);
  const available = items.filter((item) => item.availability === 'AVAILABLE');
  const estimated = available.reduce((sum, item) => sum + Number(item.lineAmount), 0);
  const address = form?.addressId ? mockAddressList(session).find((item) => item.addressId === Number(form.addressId)) || null : null;
  return {
    items,
    address,
    estimatedAmount: estimated.toFixed(4),
    availableCount: available.length,
    totalCount: items.length,
    unavailableSkuIds: items.filter((item) => item.availability !== 'AVAILABLE').map((item) => item.skuId),
    priceFingerprint: fingerprint(items),
    // 与后端 MallSettlementOptionVO 同形的结算方式可用性；mock 不接真实钱包与授信，金额仅供页面开发
    settlementOptions: [
      {
        method: 'CREDIT',
        label: '账期结算',
        amountVisible: true,
        availableAmount: '1000.0000',
        enabled: true,
        note: 'mock：账期按优惠前商品金额占用',
      },
      {
        method: 'WALLET',
        label: '余额支付',
        amountVisible: true,
        availableAmount: '500.0000',
        enabled: true,
        note: 'mock：余额在结算主体钱包内扣减',
      },
      { method: 'COD', label: '货到付款', amountVisible: true, availableAmount: null, enabled: false, disabledReason: '当前客户未开通货到付款' },
    ],
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

function saveOrders(session, rows) {
  ORDERS.set(accountKey(session), rows);
}

/** 配送进度节点：与后端 MallOrderQueryService.progressNodes 同形；done 只按事实是否存在判定 */
function progressOf(order) {
  const nodes = [{ code: 'SUBMITTED', text: '已提交订单', detail: null, time: order.submittedAt || order.createdAt, done: true }];
  if (order.status === 'CANCELLED') {
    // 取消之后不会再发车，只保留两格
    nodes.push({
      code: 'CANCELLED',
      text: '订单已取消',
      detail: order.cancelReason || null,
      time: order.cancelledAt || null,
      done: true,
    });
    return nodes;
  }
  nodes.push({
    code: 'CONFIRMED',
    text: '后台已接单',
    detail: null,
    time: order.confirmedAt || null,
    done: !!order.confirmedAt,
  });
  nodes.push({ code: 'DISPATCHED', text: '已发车配送', detail: null, time: null, done: false });
  nodes.push({
    code: 'SIGNED',
    text: order.fulfillmentStatus === 'EXCEPTION' ? '配送异常' : '已签收',
    detail: order.signReason || null,
    time: order.signedAt || null,
    done: !!order.signedAt,
  });
  return nodes;
}

/** 与后端 MallOrderVO 同构的资金投影：仅已确认订单下发应付/已付；mock 不建模优惠与实重，应付取订货金额 */
function fundsOf(order) {
  if (order.status !== 'CONFIRMED') return {};
  const payable = order.payableAmount || order.orderedTotalAmount;
  const paid = order.paidAmount || '0.0000';
  return {
    payableAmount: payable,
    paidAmount: paid,
    excessPaidAmount: (Math.max(Math.round(Number(paid) * 10000) - Math.round(Number(payable) * 10000), 0) / 10000).toFixed(4),
  };
}

/** 读取时补齐派生字段，保证列表与详情字段一致（与后端 MallOrderVO 同形） */
function decorate(order) {
  const items = order.items || [];
  return {
    ...order,
    ...fundsOf(order),
    displayStatus: displayStatusOf(order.status),
    cancelable: displayStatusOf(order.status) === 'PENDING',
    itemCount: items.length,
    itemThumbnails: items
      .map((item) => item.primaryImageUrl)
      .filter(Boolean)
      .slice(0, 3),
    timeline: order.timeline || [],
    delivery: order.delivery || null,
    progress: progressOf(order),
  };
}

const IDEMPOTENT_ORDERS = new Map();
const IDEMPOTENT_CANCELS = new Map();
const IDEMPOTENT_PAYMENTS = new Map();

export const orderRoutes = [
  {
    method: 'GET',
    path: '/scm/mall/orders',
    handler: async ({ data, session }) => {
      const denied = guard(session);
      if (denied) return denied;
      const wanted = data?.displayStatus;
      const rows = accountOrders(session)
        .map(decorate)
        .filter((order) => !wanted || order.displayStatus === wanted);
      return ok(paginate(rows, data));
    },
  },
  {
    method: 'GET',
    path: '/scm/mall/orders/:orderId',
    handler: async ({ params, session }) => {
      const denied = guard(session);
      if (denied) return denied;
      const order = accountOrders(session).find((item) => item.orderId === Number(params.orderId));
      return order ? ok(decorate(order)) : fail(40489, '订单不存在');
    },
  },
  {
    method: 'POST',
    path: '/scm/mall/orders/:orderId/cancel',
    handler: async ({ params, data, session, header }) => {
      const denied = guard(session);
      if (denied) return denied;
      const orderId = Number(params.orderId);
      const reason = String(data?.reason || '').trim();
      if (!reason) return fail(400, '请选择或填写取消原因');
      const replayKey = `${accountKey(session)}:${orderId}:${header?.['Idempotency-Key'] || ''}`;
      if (IDEMPOTENT_CANCELS.has(replayKey)) return ok(decorate(IDEMPOTENT_CANCELS.get(replayKey)));
      const rows = accountOrders(session);
      const order = rows.find((item) => item.orderId === orderId);
      if (!order) return fail(40489, '订单不存在');
      if (!['DRAFT', 'PENDING'].includes(order.status)) return fail(40986, '当前订单状态不允许取消，请联系业务员处理');
      const cancelled = {
        ...order,
        status: 'CANCELLED',
        cancelReason: reason,
        cancelledAt: new Date().toISOString(),
        timeline: [
          ...(order.timeline || []),
          { logId: Date.now(), operationType: 'CANCEL', operatorName: '客户', reason, createdAt: new Date().toISOString() },
        ],
      };
      saveOrders(
        session,
        rows.map((item) => (item.orderId === orderId ? cancelled : item))
      );
      IDEMPOTENT_CANCELS.set(replayKey, cancelled);
      return ok(decorate(cancelled));
    },
  },
  {
    method: 'POST',
    path: '/scm/mall/orders/:orderId/pay',
    handler: async ({ params, session, header }) => {
      const denied = guard(session);
      if (denied) return denied;
      const orderId = Number(params.orderId);
      const rows = accountOrders(session);
      const order = rows.find((item) => item.orderId === orderId);
      if (!order) return fail(40489, '订单不存在');
      const idempotencyKey = header?.['Idempotency-Key'];
      if (!idempotencyKey) return fail(40086, '缺少幂等键，无法安全支付');
      const replayKey = `${accountKey(session)}:${orderId}:${idempotencyKey}`;
      if (IDEMPOTENT_PAYMENTS.has(replayKey)) return ok(decorate(IDEMPOTENT_PAYMENTS.get(replayKey)));
      // 与后端 MallPaymentService 同构：仅已确认 + 余额结算 + 待付大于零；金额由服务端算，客户端不传
      if (order.status !== 'CONFIRMED') return fail(40990, '当前订单状态不允许支付');
      if (order.settlementMethod !== 'WALLET') return fail(40993, '该订单未选择余额结算');
      const payable = order.payableAmount || order.orderedTotalAmount;
      const paid = order.paidAmount || '0.0000';
      if (Number(payable) - Number(paid) <= 0) return fail(40992, '订单已支付，请勿重复付款');
      const paidOrder = {
        ...order,
        paidAmount: payable,
        timeline: [
          ...(order.timeline || []),
          { logId: Date.now(), operationType: 'PAY', operatorName: '客户', reason: '余额支付', createdAt: new Date().toISOString() },
        ],
      };
      saveOrders(
        session,
        rows.map((item) => (item.orderId === orderId ? paidOrder : item))
      );
      IDEMPOTENT_PAYMENTS.set(replayKey, paidOrder);
      return ok(decorate(paidOrder));
    },
  },
  {
    method: 'POST',
    path: '/scm/mall/orders/:orderId/reorder',
    handler: async ({ params, session }) => {
      const denied = guard(session);
      if (denied) return denied;
      const order = accountOrders(session).find((item) => item.orderId === Number(params.orderId));
      if (!order) return fail(40489, '订单不存在');
      const skipped = [];
      let addedCount = 0;
      (order.items || []).forEach((item) => {
        const product = findProduct(item.skuId);
        if (!product) {
          skipped.push({ skuId: item.skuId, productName: item.productName, reason: '商品已下架或对当前客户不可见' });
          return;
        }
        if (product.unitPrice == null) {
          skipped.push({ skuId: item.skuId, productName: item.productName, reason: '暂无客户价，请联系业务员询价' });
          return;
        }
        mockCartSet(session, item.skuId, item.orderedQuantity);
        addedCount += 1;
      });
      return ok({ addedCount, skipped });
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
        return previous.requestSignature === requestSignature ? ok(previous.order) : fail(40966, '幂等键已用于不同的下单内容');
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
      const address = preview.address;
      const items = preview.items.map((item, index) => ({
        itemId: index + 1,
        skuId: item.skuId,
        productName: item.productName,
        specName: item.specName,
        saleUnit: item.saleUnit,
        productType: item.nonStandard ? 'NON_STANDARD' : 'STANDARD',
        nonStandard: item.nonStandard === true,
        primaryImageUrl: item.primaryImageUrl || null,
        orderedQuantity: item.quantity,
        actualQuantity: null,
        unitPrice: item.unitPrice,
        orderedLineAmount: item.lineAmount,
        settlementLineAmount: null,
      }));
      // 与后端 createAndProgress 一致：含非标品需要实重，停在待处理；全标品自动确认
      const allStandard = items.every((item) => item.productType === 'STANDARD');
      const createdAt = new Date().toISOString();
      const order = {
        orderId: (orderSeq += 1),
        orderNo: `MO${Date.now()}${orderSeq}`,
        status: allStandard ? 'CONFIRMED' : 'PENDING',
        orderSource: 'MALL',
        orderedTotalAmount: preview.estimatedAmount,
        settlementTotalAmount: null,
        settlementMethod: data?.settlementMethod || null,
        paidAmount: null,
        expectDeliveryTime: data?.expectDeliveryDate ? `${data.expectDeliveryDate}T00:00:00+08:00` : null,
        remark: data?.remark || null,
        createdAt,
        submittedAt: createdAt,
        confirmedAt: allStandard ? createdAt : null,
        cancelledAt: null,
        cancelReason: null,
        signedAt: null,
        signReason: null,
        receiverName: address.receiverName,
        receiverPhone: address.receiverPhone,
        address: [address.provinceName, address.cityName, address.districtName, address.detailAddress].filter(Boolean).join(' '),
        items,
        timeline: [{ logId: 1, operationType: 'CREATE', operatorName: '客户', reason: null, createdAt }],
      };
      saveOrders(session, [order, ...accountOrders(session)]);
      IDEMPOTENT_ORDERS.set(replayKey, { requestSignature, order });
      mockCartRemove(
        session,
        preview.items.map((item) => item.skuId)
      );
      return ok(order);
    },
  },
];
