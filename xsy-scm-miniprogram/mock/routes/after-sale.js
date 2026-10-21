/*
 * Mock 契约层 · 售后中心
 *
 * 与后端 /scm/mall/after-sales/** 同构：申请走正式退货建单口径（仅已确认订单、
 * 累计申请不超过可申请数量），撤销仅限待审核；详情含退款结果与原路退回明细。
 * mock 不跨路由读订单事实（避免循环依赖），申请时把订单行快照带入售后单；
 * 订单状态与可退数量的权威判定仍在真实后端，mock 只保证信封形状一致。
 * 仅用于开发环境（VITE_APP_USE_MOCK=true）。
 */
import { fail, ok, paginate } from '../helpers';
import { PLATFORM_ERROR_CODE } from '@/constants/error-code-const';

let returnSeq = 0;
const AFTERSALES = new Map();
const IDEMPOTENT_APPLIES = new Map();

function accountKey(session) {
  return String(session.accountId || session.loginPhone || session.customerId);
}

function accountAfterSales(session) {
  return AFTERSALES.get(accountKey(session)) || [];
}

function saveAfterSales(session, rows) {
  AFTERSALES.set(accountKey(session), rows);
}

function guard(session) {
  return session ? null : fail(PLATFORM_ERROR_CODE.LOGIN_STATE_INVALID, '登录已失效，请重新登录');
}

/** 与后端 MallAfterSaleDisplayStatusEnum 同构的展示分类 */
function displayStatusOf(record) {
  if (record.status === 'REJECTED') return 'REJECTED';
  if (record.status === 'CANCELLED') return 'CANCELLED';
  if (record.status === 'APPROVED') {
    return !record.refund || record.refund.status === 'COMPLETED' ? 'REFUNDED' : 'REFUNDING';
  }
  return 'REVIEWING';
}

/** 售后进度节点：与后端 MallAfterSaleQueryService.progressNodes 同形 */
function progressOf(record) {
  const nodes = [{ code: 'SUBMITTED', text: '已提交申请', detail: null, time: record.createdAt, done: true }];
  if (record.status === 'REJECTED') {
    nodes.push({ code: 'REJECTED', text: '申请已驳回', detail: record.decisionReason || null, time: record.rejectedAt || null, done: true });
    return nodes;
  }
  if (record.status === 'CANCELLED') {
    nodes.push({ code: 'CANCELLED', text: '申请已撤销', detail: null, time: record.cancelledAt || null, done: true });
    return nodes;
  }
  if (record.status === 'PENDING') {
    nodes.push({ code: 'REVIEW', text: '等待平台审核', detail: null, time: null, done: false });
    return nodes;
  }
  nodes.push({ code: 'REVIEW', text: '审核通过', detail: null, time: record.approvedAt || null, done: true });
  if (!record.refund) {
    nodes.push({ code: 'REFUND', text: '无需退款', detail: null, time: record.approvedAt || null, done: true });
    return nodes;
  }
  const completed = record.refund.status === 'COMPLETED';
  nodes.push({ code: 'REFUND', text: completed ? '退款完成' : '退款处理中', detail: null, time: record.refund.completedAt || null, done: completed });
  return nodes;
}

/** 读取时补齐派生字段，保证列表与详情字段一致（与后端 MallAfterSaleVO 同形） */
function decorate(record) {
  const items = record.items || [];
  return {
    ...record,
    displayStatus: displayStatusOf(record),
    cancelable: record.status === 'PENDING',
    itemCount: items.length,
    itemThumbnails: items
      .map((item) => item.primaryImageUrl)
      .filter(Boolean)
      .slice(0, 3),
    progress: progressOf(record),
  };
}

export const afterSaleRoutes = [
  {
    method: 'GET',
    path: '/scm/mall/after-sales',
    handler: async ({ data, session }) => {
      const denied = guard(session);
      if (denied) return denied;
      const orderId = data?.orderId ? Number(data.orderId) : null;
      const rows = accountAfterSales(session)
        .map(decorate)
        .filter((record) => !orderId || record.orderId === orderId);
      return ok(paginate(rows, data));
    },
  },
  {
    method: 'GET',
    path: '/scm/mall/after-sales/:returnId',
    handler: async ({ params, session }) => {
      const denied = guard(session);
      if (denied) return denied;
      const record = accountAfterSales(session).find((item) => item.returnId === Number(params.returnId));
      return record ? ok(decorate(record)) : fail(40485, '售后单不存在');
    },
  },
  {
    method: 'POST',
    path: '/scm/mall/after-sales',
    handler: async ({ data, session, header }) => {
      const denied = guard(session);
      if (denied) return denied;
      const idempotencyKey = header?.['Idempotency-Key'];
      const replayKey = `${accountKey(session)}:${idempotencyKey || ''}`;
      if (idempotencyKey && IDEMPOTENT_APPLIES.has(replayKey)) return ok(decorate(IDEMPOTENT_APPLIES.get(replayKey)));
      const reason = String(data?.reason || '').trim();
      if (!reason) return fail(400, '售后原因不能为空');
      const items = (data?.items || []).filter((item) => Number(item.quantity) > 0);
      if (!items.length) return fail(400, '售后明细不能为空');
      const createdAt = new Date().toISOString();
      const record = {
        returnId: (returnSeq += 1),
        returnNo: `MR${Date.now()}${returnSeq}`,
        orderId: Number(data?.orderId),
        orderNo: data?.orderNo || null,
        status: 'PENDING',
        reason,
        decisionReason: null,
        requestedAmount: items.reduce((sum, item) => sum + Number(item.estimatedAmount || 0), 0).toFixed(4),
        approvedAmount: '0.0000',
        createdAt,
        approvedAt: null,
        rejectedAt: null,
        cancelledAt: null,
        items: items.map((item) => ({
          returnItemId: returnSeq * 100 + items.indexOf(item) + 1,
          orderItemId: item.orderItemId,
          productName: item.productName,
          specName: item.specName,
          unit: item.unit,
          primaryImageUrl: item.primaryImageUrl || null,
          requestedQuantity: String(item.quantity),
          approvedQuantity: null,
          lockedUnitPrice: item.unitPrice,
          estimatedAmount: item.estimatedAmount || null,
          approvedAmount: '0.0000',
        })),
        refund: null,
      };
      saveAfterSales(session, [record, ...accountAfterSales(session)]);
      if (idempotencyKey) IDEMPOTENT_APPLIES.set(replayKey, record);
      return ok(decorate(record));
    },
  },
  {
    method: 'POST',
    path: '/scm/mall/after-sales/:returnId/cancel',
    handler: async ({ params, session }) => {
      const denied = guard(session);
      if (denied) return denied;
      const rows = accountAfterSales(session);
      const record = rows.find((item) => item.returnId === Number(params.returnId));
      if (!record) return fail(40485, '售后单不存在');
      if (record.status !== 'PENDING') return fail(40989, '当前售后状态不允许撤销');
      const cancelled = { ...record, status: 'CANCELLED', cancelledAt: new Date().toISOString() };
      saveAfterSales(
        session,
        rows.map((item) => (item.returnId === record.returnId ? cancelled : item))
      );
      return ok(decorate(cancelled));
    },
  },
];
