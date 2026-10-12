/**
 * 审计快照字段的中文名。
 *
 * 快照里的键是数据库 / 实体的原始字段名（`amount`、`eventAt`、`skuCodeSnapshot`…），
 * 直接印给业务用户看等于让他们读代码。这里做「键名 → 中文」的展示层映射，
 * 只翻译「这是哪个字段」，不翻译值 —— 值仍是审计证据，原样呈现。
 *
 * 找不到映射时回落到原始键名，不猜、不留空：新字段先以原名出现，
 * 再补进这张表，比默默显示成空白安全。
 *
 * 同一键在不同域含义不同时以业务域为准（如 `status` 统一译「状态」，
 * 各域表头自身会给出上下文）。
 */
export const SCM_AUDIT_FIELD_LABELS: Record<string, string> = {
    // ---- 通用审计列 ----
    id: '主键',
    version: '版本号',
    deleted: '已删除',
    sortOrder: '排序',
    remark: '备注',
    createdAt: '创建时间',
    createdBy: '创建人',
    updatedAt: '更新时间',
    updatedBy: '更新人',

    // ---- 订单 ----
    orderId: '订单 ID',
    orderNo: '订单编号',
    orderSource: '订单来源',
    status: '状态',
    customerId: '客户 ID',
    customerCodeSnapshot: '客户编码',
    customerNameSnapshot: '客户名称',
    sellerId: '业务员 ID',
    warehouseId: '仓库 ID',
    address: '收货地址',
    expectDeliveryTime: '期望送达时间',
    discount: '优惠金额',
    orderedTotalAmount: '订单金额',
    settlementTotalAmount: '结算金额',
    approvedAmount: '审核金额',
    confirmedAt: '确认时间',
    submittedAt: '提交时间',
    cancelledAt: '取消时间',
    cancelReason: '取消原因',
    decisionReason: '处理原因',
    supplementReason: '补录原因',
    settleModeSnapshot: '结算方式',
    originalOrderId: '原订单 ID',
    receiptId: '收款单 ID',
    refundAmount: '退款金额',
    gifts: '赠品',
    items: '商品明细',

    // ---- 订单 / 采购明细行 ----
    itemId: '明细 ID',
    spuId: '商品 ID',
    skuId: '商品规格 ID',
    spuCodeSnapshot: '商品编码',
    skuCodeSnapshot: '商品规格编码',
    productNameSnapshot: '商品名称',
    specNameSnapshot: '规格名称',
    specValuesSnapshot: '规格值',
    productTypeSnapshot: '商品类型',
    saleUnitSnapshot: '销售单位',
    orderedQuantity: '下单数量',
    actualQuantity: '实收数量',
    actualQuantityReason: '实收差异原因',
    actualQuantitySource: '实收来源',
    orderedLineAmount: '下单金额',
    settlementLineAmount: '结算金额',
    draftUnitPrice: '录单单价',
    draftPriceSource: '录单价格来源',
    draftPriceSourceId: '录单价格来源 ID',
    lockedUnitPrice: '锁定单价',
    lockedPriceSource: '锁价来源',
    lockedPriceSourceId: '锁价来源 ID',
    manualPriceOverride: '人工改价',
    manualPriceReason: '改价原因',

    // ---- 收货地址 ----
    provinceCode: '省份编码',
    provinceName: '省份',
    cityCode: '城市编码',
    cityName: '城市',
    districtCode: '区县编码',
    districtName: '区县',
    receiverName: '收货人',
    receiverPhone: '收货电话',
    latitude: '纬度',
    longitude: '经度',
    geomCrs: '坐标系',

    // ---- 采购 / 收货 ----
    purchaseOrderId: '采购单 ID',
    purchaseOrderItemId: '采购单明细 ID',
    purchaseReceiptId: '收货单 ID',
    receiptNo: '收货单号',
    supplierId: '供应商 ID',
    purchaserId: '采购员 ID',
    orderStatus: '订单状态',
    totalAmount: '总金额',
    plannedArrivalDate: '计划到货日期',
    plannedQuantity: '计划数量',
    receivedQuantity: '已收数量',
    purchasePrice: '采购单价',
    lineAmount: '行金额',
    allocations: '分配明细',
    demandIds: '需求单 ID',
    demandId: '需求单 ID',
    quantity: '数量',
    sourceLineCount: '来源行数',
    createdCount: '新增行数',
    skippedCount: '跳过行数',
    allocatedQuantity: '已分配数量',
    cumulative: '累计',
    difference: '差异',
    over: '超收',

    // ---- 财务 ----
    amount: '金额',
    netAmount: '净额',
    openAmount: '未核销金额',
    writtenOffAmount: '已核销金额',
    overAppliedAmount: '超额核销金额',
    entryType: '方向',
    eventAt: '业务时点',
    receivedAt: '收款时间',
    paidAt: '付款时间',
    method: '结算方式',
    counterpartyId: '往来方 ID',
    counterpartyType: '往来方类型',
    counterpartyNameSnapshot: '往来方名称',
    supplierNameSnapshot: '供应商名称',
    receivableNo: '应收单号',
    payableNo: '应付单号',
    paymentNo: '付款单号',
    returnNo: '退货单号',
    sourceId: '来源单据 ID',
    sourceReturnId: '来源退货单 ID',
    sourceType: '来源类型',
    targetId: '目标单据 ID',
    targetNo: '目标单号',
    targetType: '目标类型',
    originalReceivableId: '原应收单 ID',
    externalReference: '外部参考号',
    itemCount: '明细行数',

    // ---- 价格历史 ----
    customerTypeId: '客户类型 ID',
    unitPrice: '单价',
    effectiveFrom: '生效日期',
    effectiveTo: '失效日期',
};

/** 取字段的中文名；没有映射时回落原始键名。 */
export function auditFieldLabel(key: string): string {
    return SCM_AUDIT_FIELD_LABELS[key] ?? key;
}

export default SCM_AUDIT_FIELD_LABELS;
