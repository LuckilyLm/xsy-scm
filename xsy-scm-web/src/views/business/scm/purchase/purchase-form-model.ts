/**
 * W5 采购域表单模型（新增文件，仿 W4 `order-form-model.ts`）。
 *
 * 三块职责：
 * 1. **定点数纪律（A17/A18）**：所有数量 / 金额在提交前经 {@link fixed} 归一为 4 位小数字符串；
 *    渲染时 `null` → `—`、`"0.0000"` → `0.0000`（**三态不可合并**）。
 * 2. **分配集合（A31 / Q13）**：`items[].allocations[]` 是**集合**，增删改都以 `demandId` 为身份，
 *    只改一条不得影响同行其它条。
 * 3. **单位一致性（A32 / Q17）**：需求单位 ≠ 采购单位时**禁止**加入分配，且不猜换算系数。
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
 * 金额渲染（A18）。
 *
 * @param unpriced 该字段的 `null` 是否表示「未定价」而非「无此金额」。
 *   W5 里 `totalAmount` 的 `null` 是「还没有行」，不是「未定价」→ 传 `false` 渲染 `—`。
 */
export function amount(value: string | null | undefined, unpriced = false): string {
    if (value === null || value === undefined || value === '') {
        return unpriced ? '未定价' : '—';
    }
    return '¥ ' + fixed(value);
}

/** 数量渲染（A18）：`null` → `—`，`"0.0000"` → `0.0000`。 */
export function quantity(value: string | null | undefined): string {
    if (value === null || value === undefined || value === '') {
        return '—';
    }
    return fixed(value);
}

/**
 * 收货进度渲染。
 *
 * `received_progress` 是**比例**（`已收合计 / 计划合计`，scale 4），**不是金额**；
 * 没有活动行时后端给 `null`（「无值 ≠ 0.0000」），这里同样渲染 `—`。
 */
export function progress(value: string | null | undefined): string {
    if (value === null || value === undefined || value === '') {
        return '—';
    }
    return new Decimal(value).times(100).toDecimalPlaces(2, Decimal.ROUND_HALF_UP).toFixed(2) + '%';
}

/*
 * 时间渲染（A18 同族）的实现在 `../common/scm-display.ts`，**不再从这里转出**。
 *
 * 历史：提交 48134bf 曾在这里 `export { datetime } from '../common/scm-display'`（漏了 `.ts`），
 * 于是本模块在 `node --experimental-strip-types --test` 下以 ERR_MODULE_NOT_FOUND 整体加载失败；
 * 而补上 `.ts` 又会触发 TS5097（本项目 `tsconfig` 未开启 `allowImportingTsExtensions`）。
 * 两个约束的交集是：**本模块不得有值导入**（它要被 node 直接加载）。
 * 因此转出去掉，原来经这里取 `datetime` 的两个页面改为直接从 `common/scm-display` 取。
 * 详见 `test/w6-inventory-contract.test.mjs` 里的 node 可加载性门禁。
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
 * 注意这是**本行**合计，不是该需求的全库已分配合计 —— 后者由服务端裁定（40082）。
 */
export function allocatedOnItem(item: OrderItem): string {
    const sum = (item.allocations ?? []).reduce(
        (acc, row) => acc.plus(new Decimal(row.quantity || '0')),
        new Decimal(0)
    );
    return sum.toFixed(4);
}

/**
 * 本条分配在该需求上的**可分配上限**（A31 的客户端预检）。
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
 * **A32 / Q17**：需求单位与采购单位是否不一致。
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

/** 同一采购行内是否已经有该需求（Q13 禁止重复 `(item, demand)`）。 */
export function hasAllocation(item: OrderItem, demandId: Order['id']): boolean {
    return (item.allocations ?? []).some((row) => String(row.demandId) === String(demandId));
}

/**
 * 把**输入框里正在编辑的数**归一为 4 位定点字符串（{@link fixed} 的宽进版）。
 *
 * 存在的理由：`a-input-number` 在用户键入期间**不应用** `precision`
 * （antd 4.2.5 `InputNumber.js` 的 `getPrecision(numStr, userTyping)` 在 `userTyping` 为真时
 * 直接返回 `undefined`，注释原文「it will not block user typing」；提交只发生在 blur 时经
 * `onBlur → flushInputValue(false) → triggerValueUpdate(parsed, false)`）。
 * 于是 `<a-input-number :precision="4">` 绑定到模型上的是**裸输入** `"2"` / `"2.5"`，而不是 `"2.0000"`。
 *
 * 用户没有输入 → 原样返回（`''` / `null` / `undefined` 不伪造 `0`）；
 * 是合法数字 → {@link fixed} 归一；其余（`'-'`、`'.'`、空串）→ `''`，
 * 由调用方按「未填写」处理，绝不能把这类值交给 `new Decimal()`（否则抛 Invalid argument）。
 *
 * 这里**只归一数值表示，不放宽定点形状**：`10 ^ 15` 这类超出后端
 * `ScmStrictDecimalStringDeserializer` （`\d{1,14}`）整数位的输入，归一后仍会被 {@link FIXED} 拒绝。
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
 * 输入框失焦用的归一：完整数字 → 4 位定点；**未定型的输入原样保留**。
 *
 * 刻意不在这里把 `"2."` 清掉 —— 用户在「2.」上误触失焦后再点回来，留着比抹掉更少惊吓；
 * 该值提交时会被 {@link validateOrder} 拦下并给出文案。
 */
export function normalizeTyped(value: string | number | null | undefined): string {
    if (value === null || value === undefined) {
        return '';
    }
    const normalized = typedFixed(value);
    return normalized !== '' ? normalized : String(value);
}

/**
 * 单价是否**已填写**（形状合法 + 非负）。
 *
 * 未填写时由服务端按「未定价」处理（W5 允许价格待定）；一旦填了就必须是合法价。
 * 与 {@link validateOrder} 同口径：先 {@link typedFixed} 归一（吸收输入框键入中的裸数），
 * 再套 {@link FIXED} 的严格形状 —— 不放宽形状，只对齐「校验时机」。
 */
export function priceFilled(value: string | null | undefined): boolean {
    const normalized = typedFixed(value);
    return normalized !== '' && FIXED.test(normalized) && new Decimal(normalized).gte(0);
}

/**
 * 提交前校验。返回第一条错误文案；全部通过返回 `undefined`。
 *
 * 只做**形状与业务前置**校验（必填、正数、重复 SKU、单位一致、上限）；
 * 「需求被别的单据抢走」这类并发冲突交给服务端（40972 / 40082）—— 前端不假装能预测。
 *
 * **校验时机与提交口径一致**：用户可能一次都没离开过数量输入框就点「保存草稿」，
 * 此时模型里是键入中的裸数（见 {@link typedFixed}），必须先归一再套 `FIXED`；
 * 否则会出现「明明填了 2 却说不是四位定点数」。{@link payload} 用的是同一个 {@link fixed}，
 * 因此校验通过的输入一定能构造出合法请求体。
 *
 * 只有真正**缺失**（`''` / `null`）才报「4 位定点数」文案。空串一律经 {@link typedFixed}
 * 兜底转 `''` —— 单元测试里可以直接把 `null` 塞进模型，此时 `test(null)` 会把 `null`
 * 强转成字符串 `'null'` 而误判「形状合法」，必须防住。
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
            return '同一 SKU 不能重复，请合并到同一行';
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

/** 表单 → 请求体（A17：所有定点数归一为 4 位字符串；`null` 保持 `null`）。 */
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
    }));
}

/** 非标品必须录入实重（A25）：实重缺失 → 40083。 */
export function isNonStandard(item: ReceiptItem | undefined): boolean {
    return item?.productType === 'NON_STANDARD';
}

/**
 * 确认收货的客户端预检。
 *
 * 必须覆盖**全部**收货行（40998）；非标品必须有实重（40083）；
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
        })),
    };
}

/**
 * **A26** 容差提示文案。
 *
 * 超收容差是服务端配置（`scm.purchase.over_receipt_tolerance_percent`，Q3a），
 * W5 **没有**把它暴露给前端的读取端点，因此这里不假装能算出「上限」，
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
