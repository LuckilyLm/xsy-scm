/**
 * 采购表单的数据处理与校验。
 * 提交前把数量、金额统一转成四位小数字符串。
 * 分配明细按 demandId 更新；需求单位与采购单位不一致时禁止分配。
 */
import Decimal from 'decimal.js';
import type {
    Allocation,
    AllocationPayload,
    Demand,
    Order,
    OrderItem,
    OrderItemPayload,
    OrderPayload,
    Receipt,
    ReceiptConfirmItemPayload,
    ReceiptConfirmPayload,
    ReceiptItem,
} from './purchase-types.ts';

/** 4 位定点字符串的严格形状（与后端 `ScmStrictDecimalStringDeserializer` 同源）。 */
const FIXED = /^\d{1,14}\.\d{4}$/;

/** 归一为 4 位小数字符串（四舍五入）。`null` / `undefined` / 空串原样返回，不伪造 0。 */
export function fixed(value: string | number | null | undefined): string {
    if (value === null || value === undefined || value === '') {
        return '';
    }
    return new Decimal(value).toDecimalPlaces(4, Decimal.ROUND_HALF_UP).toFixed(4);
}

/**
 * 金额渲染。`unpriced=true` 时 `null` 显示「未定价」，否则显示 `—`。
 */
export function amount(value: string | null | undefined, unpriced = false): string {
    if (value === null || value === undefined || value === '') {
        return unpriced ? '未定价' : '—';
    }
    return '¥ ' + fixed(value);
}

/** 数量渲染：`null` → `—`，`"0.0000"` → `0.0000`。 */
export function quantity(value: string | null | undefined): string {
    if (value === null || value === undefined || value === '') {
        return '—';
    }
    return fixed(value);
}

/**
 * 收货进度渲染。
 *
 * `received_progress` 是比例（`已收合计 / 计划合计`，scale 4），不是金额；
 * 没有活动行时后端给 `null`（「无值 ≠ 0.0000」），这里同样渲染 `—`。
 */
export function progress(value: string | null | undefined): string {
    if (value === null || value === undefined || value === '') {
        return '—';
    }
    return new Decimal(value).times(100).toDecimalPlaces(2, Decimal.ROUND_HALF_UP).toFixed(2) + '%';
}

/*
 * 本模块会被 `node --experimental-strip-types --test` 直接加载，因此不得有相对路径的值导入：
 * node 的 ESM 解析不给相对路径补扩展名，打包器与 `vue-tsc` 都会补，所以构建与类型检查发现不了；
 * 而补上 `.ts` 又会触发 TS5097（本项目 `tsconfig` 未开启 `allowImportingTsExtensions`）。
 * 时间渲染由 `common/scm-display.ts` 的调用方直接引入，不从本模块转出。
 */


// ------------------------------------------------------------------
// 采购单表单
// ------------------------------------------------------------------

/** 一行空白采购行。 */
export function newOrderItem(): OrderItem {
    return {plannedQuantity: '1.0000', purchasePrice: '0.0000', allocations: []};
}

/** 一张空白采购单。 */
export function newOrder(): Order {
    return {items: [newOrderItem()]};
}

/** 由需求构造一条分配（带上当前需求版本，缺了会 40972）。 */
export function newAllocation(demand: Demand): Allocation {
    return {
        demandId: demand.id,
        skuId: demand.skuId,
        quantity: demand.unallocatedQuantity ?? '0.0000',
        demandUnit: demand.demandUnit,
        demandVersion: demand.version ?? 0,
        demandStatus: demand.status,
    };
}

/**
 * 该采购行上的分配合计。
 *
 * 注意这是本行合计，不是该需求的全库已分配合计 —— 后者由服务端裁定（40082）。
 */
export function allocatedOnItem(item: OrderItem): string {
    const sum = (item.allocations ?? []).reduce(
        (acc, row) => acc.plus(new Decimal(row.quantity || '0')),
        new Decimal(0)
    );
    return sum.toFixed(4);
}

/**
 * 本条分配在该需求上的可分配上限（客户端预检）。
 *
 * `demand.unallocatedQuantity` 是「全库剩余」，其中已扣掉本行上一次提交的量；
 * 因此加上「本行该需求的旧分配量」才是本行本次可填的上限 —— 与后端
 * `validateAllocations` 的 `otherAllocated = allocated − oldTotals[demand]` 同一口径。
 */
export function allocationCapacity(demand: Demand, previousOnItem: string | null | undefined): Decimal {
    const remaining = new Decimal(demand.unallocatedQuantity ?? '0');
    const own = new Decimal(previousOnItem && previousOnItem !== '' ? previousOnItem : '0');
    return remaining.plus(own);
}

/**
 * 需求单位与采购单位是否不一致。
 *
 * 不一致时前端直接拒绝加入分配，并给出 40971 的同一句话 —— 不允许「只换单位字符串」
 * 或猜换算系数（那会把 100 kg 静默变成 100 箱）。
 */
export function unitMismatch(item: OrderItem, demand: Demand): boolean {
    if (!item.purchaseUnit || !demand.demandUnit) {
        return false;
    }
    return item.purchaseUnit !== demand.demandUnit;
}

/** 同一采购行内是否已经有该需求（禁止重复 `(item, demand)`）。 */
export function hasAllocation(item: OrderItem, demandId: Order['id']): boolean {
    return (item.allocations ?? []).some((row) => String(row.demandId) === String(demandId));
}

/**
 * 把输入中的数量转成四位小数字符串。
 * 输入过程中允许 "2"、"2.5" 等写法；空值保留为空，非法输入返回空字符串。
 * 超出金额精度限制的值仍会被拒绝。
 */
function typedFixed(value: string | number | null | undefined): string {
    if (value === null || value === undefined || value === '') {
        return '';
    }
    const text = String(value).trim();
    if (!/^-?\d+(\.\d+)?$/.test(text)) {
        return '';
    }
    return fixed(text);
}

/**
 * 输入框失焦用的归一：完整数字 → 4 位定点；未定型的输入原样保留。
 *
 * 刻意不在这里把 `"2."` 清掉 —— 用户在「2.」上误触失焦后再点回来，留着比抹掉更少惊吓；
 * 该值提交时会被 validateOrder 拦下并给出文案。
 */
export function normalizeTyped(value: string | number | null | undefined): string {
    if (value === null || value === undefined) {
        return '';
    }
    const normalized = typedFixed(value);
    return normalized !== '' ? normalized : String(value);
}

/**
 * 单价是否已填写（形状合法 + 非负）。
 *
 * 未填写时由服务端按「未定价」处理（允许价格待定）；一旦填了就必须是合法价。
 * 与 validateOrder 同口径：先 typedFixed 归一（吸收输入框键入中的裸数），
 * 再套 FIXED 的严格形状 —— 不放宽形状，只对齐「校验时机」。
 */
export function priceFilled(value: string | null | undefined): boolean {
    const normalized = typedFixed(value);
    return normalized !== '' && FIXED.test(normalized) && new Decimal(normalized).gte(0);
}

/**
 * 提交前检查必填、正数、重复商品规格、单位及数量上限，返回第一条错误。
 * 先把尚未失焦的数字输入归一化；并发占用等冲突仍由服务端判断。
 */
export function validateOrder(form: Order): string | undefined {
    if (!form.supplierId) {
        return '请选择供应商';
    }
    if (!form.warehouseId) {
        return '请选择收货仓库';
    }
    const items = form.items ?? [];
    if (!items.length) {
        return '请至少添加一行采购明细';
    }
    const seen = new Set<string>();
    for (const item of items) {
        if (!item.skuId) {
            return '请选择采购商品';
        }
        if (seen.has(String(item.skuId))) {
            return '同一商品规格不能重复，请合并到同一行';
        }
        seen.add(String(item.skuId));
        const plannedQuantity = typedFixed(item.plannedQuantity);
        if (!FIXED.test(plannedQuantity) || new Decimal(plannedQuantity).lte(0)) {
            return '采购数量必须为大于零的四位定点数';
        }
        // 单价可以留空（未定价）；填了才校验形状，否则服务端会以 40083 之类直接拒绝。
        if (String(item.purchasePrice ?? '').trim() !== '' && !priceFilled(item.purchasePrice)) {
            return '采购单价必须为非负四位定点数';
        }
        for (const row of item.allocations ?? []) {
            if (!row.demandId) {
                return '分配缺少采购需求';
            }
            const quantity = typedFixed(row.quantity);
            if (!FIXED.test(quantity) || new Decimal(quantity).lte(0)) {
                return '分配数量必须为大于零的四位定点数';
            }
            if (row.demandVersion === undefined || row.demandVersion === null) {
                return '分配缺少需求版本，请移除该分配后重新选择需求';
            }
        }
    }
    return undefined;
}

/** 表单 → 请求体（所有定点数归一为 4 位字符串；`null` 保持 `null`）。 */
export function payload(form: Order): OrderPayload {
    const body: OrderPayload = {
        supplierId: form.supplierId!,
        purchaserId: form.purchaserId ?? null,
        warehouseId: form.warehouseId!,
        plannedArrivalDate: form.plannedArrivalDate ?? null,
        remark: form.remark ?? null,
        items: (form.items ?? []).map((item): OrderItemPayload => ({
            // 保留行必须带 `id` + `version`（缺版本 → 40088）；新增行两者都不带。
            id: item.id,
            version: item.version,
            skuId: item.skuId!,
            quantity: fixed(item.plannedQuantity),
            price: fixed(item.purchasePrice),
            allocations: (item.allocations ?? []).map((row): AllocationPayload => ({
                demandId: row.demandId!,
                quantity: fixed(row.quantity),
                demandVersion: row.demandVersion as number,
            })),
        })),
    };
    if (form.id !== undefined && form.id !== null) {
        body.id = form.id;
        body.version = form.version;
    }
    return body;
}

// ------------------------------------------------------------------
// 收货单表单
// ------------------------------------------------------------------

/** 由收货单详情生成确认表单的初始行（声明数量默认取剩余可收量）。 */
export function newConfirmLines(receipt: Receipt): ReceiptConfirmItemPayload[] {
    return (receipt.items ?? []).map((item) => ({
        receiptItemId: item.id,
        version: item.version,
        receivedQuantity: item.remainingQuantity ?? '0.0000',
        actualWeight: null,
        weightSource: null,
        correctionReason: null,
        lots: [],
    }));
}

/** 非标品必须录入实重：实重缺失 → 40083。 */
export function isNonStandard(item: ReceiptItem | undefined): boolean {
    return item?.productType === 'NON_STANDARD';
}

/**
 * 确认收货的客户端预检。
 *
 * 必须覆盖全部收货行（40998）；非标品必须有实重（40083）；
 * 声明数量与实重都必须是 4 位定点字符串。
 */
export function validateConfirm(receipt: Receipt, lines: ReceiptConfirmItemPayload[]): string | undefined {
    const items = receipt.items ?? [];
    if (!items.length) {
        return '收货单没有明细';
    }
    if (lines.length !== items.length) {
        return '必须为全部收货明细填写本次收货数量';
    }
    for (const line of lines) {
        const item = items.find((row) => String(row.id) === String(line.receiptItemId));
        if (!item) {
            return '收货明细与收货单不匹配，请刷新后重试';
        }
        if (!FIXED.test(line.receivedQuantity) || new Decimal(line.receivedQuantity).lt(0)) {
            return '声明数量必须为非负四位定点数';
        }
        if (isNonStandard(item)) {
            if (!line.actualWeight || !FIXED.test(line.actualWeight) || new Decimal(line.actualWeight).lt(0)) {
                return '非标品必须录入实重（四位定点数）';
            }
            if (line.weightSource !== 'MANUAL') {
                return '实重来源必须标记为人工录入';
            }
        } else if (line.actualWeight != null && line.actualWeight !== '') {
            if (!FIXED.test(line.actualWeight)) {
                return '实重必须为四位定点数';
            }
        }
        if (receipt.warehouseLotTrackingEnabled) {
            const lots = line.lots ?? [];
            if (!lots.length) {
                return '该仓库启用了批次追踪，每行都要登记批次';
            }
            let sum = new Decimal(0);
            for (const lot of lots) {
                if (!FIXED.test(lot.quantity) || new Decimal(lot.quantity).lte(0)) {
                    return '批次数量必须为大于零的四位定点数';
                }
                sum = sum.plus(new Decimal(lot.quantity));
            }
            const effective = isNonStandard(item) ? line.actualWeight ?? '0' : line.receivedQuantity;
            if (!sum.eq(new Decimal(effective))) {
                return '批次数量之和必须等于本次收货量';
            }
        } else if ((line.lots ?? []).length) {
            return '该仓库未启用批次追踪，不能登记批次';
        }
    }
    return undefined;
}

/** 确认表单 → 请求体。 */
export function confirmPayload(receipt: Receipt, lines: ReceiptConfirmItemPayload[]): ReceiptConfirmPayload {
    return {
        id: receipt.id!,
        version: receipt.version ?? 0,
        items: lines.map((line) => ({
            receiptItemId: line.receiptItemId,
            version: line.version,
            receivedQuantity: fixed(line.receivedQuantity),
            actualWeight: line.actualWeight ? fixed(line.actualWeight) : null,
            weightSource: line.actualWeight ? 'MANUAL' : null,
            correctionReason: line.correctionReason ?? null,
            lots: (line.lots ?? []).map((lot) => ({
                sourceLotNo: lot.sourceLotNo?.trim() ? lot.sourceLotNo.trim() : null,
                quantity: fixed(lot.quantity),
            })),
        })),
    };
}

/**
 * 容差提示文案。
 *
 * 超收容差是服务端配置（`scm.purchase.over_receipt_tolerance_percent`），
 *  没有把它暴露给前端的读取端点，因此这里不假装能算出「上限」，
 * 只给出确定的事实：剩余可收量 + 超收会被服务端按配置拒绝（40989）。
 */
export function toleranceHint(item: ReceiptItem): string {
    const remaining = quantity(item.remainingQuantity);
    return `剩余可收 ${remaining}（可超出剩余量的比例由服务端「采购超收容差」配置决定，超出将被拒绝）`;
}

/** 该行本次收货是否超出剩余可收量（仅提示，最终以服务端 40989 为准）。 */
export function beyondRemaining(item: ReceiptItem, received: string): boolean {
    if (!item.remainingQuantity || !FIXED.test(received)) {
        return false;
    }
    return new Decimal(received).gt(new Decimal(item.remainingQuantity));
}
