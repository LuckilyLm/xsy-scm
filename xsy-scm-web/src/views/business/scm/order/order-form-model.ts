import Decimal from 'decimal.js';
import type {Address, Item, Order} from './order-types.ts';

export function newOrder(): Order {
    return {orderSource: 'ADMIN', items: [], address: {receiverName: '', receiverPhone: '', address: ''}};
}

export function fixed(value: string | number): string {
    return new Decimal(value).toDecimalPlaces(4, Decimal.ROUND_HALF_UP).toFixed(4);
}

/**
 * 本地未提交草稿的行（Wave 3 §7.3）：只保留用户录入，不含任何服务端解析结果。
 */
export interface DraftItem {
    skuId?: string | number;
    orderedQuantity: string;
    manualPriceOverride: boolean;
    unitPrice?: string | null;
    overrideReason?: string | null;
}

/**
 * 本地未提交草稿（浏览器 localStorage，第一版不入库）。
 *
 * 刻意不含：权限结果、PriceResolver 最终价（draftUnitPrice/draftPriceSource）、库存可用量、
 * 后端 version、服务端计算/快照字段（status、orderNo、lockedUnitPrice 等）。恢复后一律重新解析。
 */
export interface Draft {
    customerId?: string | number;
    orderSource: string;
    expectDeliveryTime?: string | null;
    remark?: string | null;
    supplementReason?: string | null;
    originalOrderId?: string | number | null;
    items: DraftItem[];
}

/** 草稿存储键：按登录用户隔离，避免不同操作员草稿互串（§7.3 / 验收「不同登录用户草稿互不串」）。 */
export function draftKey(employeeId: string | number): string {
    return `xsy-scm:order-draft:${employeeId}`;
}

/** 把当前新建表单压缩为草稿：逐字段白名单挑选，绝不落入解析价 / version / 快照。 */
export function serializeDraft(form: Order): Draft {
    return {
        customerId: form.customerId,
        orderSource: form.orderSource,
        expectDeliveryTime: form.expectDeliveryTime ?? null,
        remark: form.remark ?? null,
        supplementReason: form.orderSource === 'SUPPLEMENT' ? (form.supplementReason ?? null) : null,
        originalOrderId: form.orderSource === 'SUPPLEMENT' ? (form.originalOrderId ?? null) : null,
        items: form.items
            .filter(i => i.skuId != null || (i.orderedQuantity && i.orderedQuantity !== ''))
            .map(i => ({
                skuId: i.skuId,
                orderedQuantity: i.orderedQuantity,
                manualPriceOverride: !!i.manualPriceOverride,
                unitPrice: i.manualPriceOverride ? (i.unitPrice ?? null) : null,
                overrideReason: i.manualPriceOverride ? (i.overrideReason ?? null) : null
            }))
    };
}

/** 从草稿还原为可编辑表单：仅回填用户录入字段，解析价 / 地址由调用方重新请求客户与价格填充。 */
export function applyDraft(draft: Draft): Order {
    const form = newOrder();
    form.customerId = draft.customerId;
    form.orderSource = draft.orderSource || 'ADMIN';
    form.expectDeliveryTime = draft.expectDeliveryTime ?? null;
    form.remark = draft.remark ?? null;
    form.supplementReason = draft.supplementReason ?? null;
    form.originalOrderId = draft.originalOrderId ?? null;
    form.items = (draft.items ?? []).map(i => ({
        skuId: i.skuId,
        orderedQuantity: i.orderedQuantity,
        manualPriceOverride: !!i.manualPriceOverride,
        unitPrice: i.manualPriceOverride ? (i.unitPrice ?? null) : null,
        overrideReason: i.manualPriceOverride ? (i.overrideReason ?? null) : null
    }));
    return form;
}

/**
 * 历史订单复用（Wave 3 §7.4）：从历史单构造「新增草稿」，只复制允许字段并强制重新解析。
 *
 * 绝不复制：orderId / orderNo / version / status / 历史 lockedUnitPrice / draftUnitPrice /
 * 人工改价 / 库存预留 / 日志。订单来源回落为后台录单、补单字段清空；价格与主档可用性由后续
 * preview + 后端 create 校验重新决定。
 */
export function fromHistory(detail: Order): Order {
    const form = newOrder();
    form.customerId = detail.customerId;
    form.expectDeliveryTime = detail.expectDeliveryTime ?? null;
    form.remark = detail.remark ?? null;
    const addr: Address = detail.address
        ? {
            receiverName: detail.address.receiverName ?? '',
            receiverPhone: detail.address.receiverPhone ?? '',
            address: detail.address.address ?? ''
        }
        : form.address;
    form.address = addr;
    form.items = (detail.items ?? []).map((i): Item => ({
        skuId: i.skuId,
        orderedQuantity: i.orderedQuantity,
        manualPriceOverride: false,
        unitPrice: null,
        overrideReason: null
    }));
    return form;
}

export function validateOrder(f: Order): string | undefined {
    if (!f.customerId) return '请选择客户';
    if (!f.items.length) return '请添加商品';
    if (!f.address.receiverName || !f.address.receiverPhone || !f.address.address) return '请填写完整收货信息';
    if (f.orderSource === 'SUPPLEMENT' && !f.supplementReason?.trim()) return '请填写补单原因';
    const seen = new Set<string>();
    for (const i of f.items) {
        if (!i.skuId) return '请选择 SKU';
        if (seen.has(String(i.skuId))) return '同一 SKU 不能重复';
        seen.add(String(i.skuId));
        if (!/^\d{1,14}\.\d{4}$/.test(i.orderedQuantity) || new Decimal(i.orderedQuantity).lte(0)) return '数量必须为正的四位定点数';
        if (i.manualPriceOverride && (!i.overrideReason?.trim() || i.unitPrice == null || !/^\d{1,14}\.\d{4}$/.test(i.unitPrice))) return '人工改价需要有效价格和原因';
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
            sortOrder: index
        }))
    };
}

export function amount(value: string | null | undefined, unpriced = false): string {
    return value == null ? (unpriced ? '未定价' : '—') : '¥ ' + fixed(value);
}
