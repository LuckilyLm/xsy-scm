/**
 * 审计快照里不对业务用户展示的键。
 *
 * 快照存的是全量实体，混着「业务事实」和「技术引用」：`id` / `orderId` 这类是给程序配对用的，
 * `version` / `updatedAt` 每次写入都会变，`geomCrs` / `longitude` 只有地图用得上。
 * 直接列出来，用户看到的是「主键 8160」「版本号 1 → 2」这种读不出信息的行，
 * 真正关心的状态、金额、数量反而被淹掉 —— 所以展示前先剔掉，再算「变了几项」。
 *
 * 与 `scm-diff.ts` 的 `IDENTITY_KEYS` 分开：行标识（`skuId` 等）要留着给程序配对，
 * 只是不作为字段显示；这里剔的是「连配对都不需要」的键。
 *
 * 剔除只发生在展示层，快照原文一字未动，需要时仍可从数据库逐字回查。
 */

/** 系统簿记：每次写入都会变，或与日志行自己的「操作人 / 时间」重复。 */
const BOOKKEEPING_KEYS = ['version', 'deleted', 'sortOrder', 'createdBy', 'updatedBy', 'updatedAt'];

/** 技术引用：数据库主键、外键、行政区划编码、坐标与坐标系。 */
const REFERENCE_KEYS = [
    'id', 'itemId', 'orderId', 'customerId', 'sellerId', 'warehouseId', 'supplierId', 'purchaserId',
    'counterpartyId', 'targetId', 'sourceId', 'spuId', 'skuId', 'receiptId', 'receiptItemId',
    'purchaseOrderId', 'purchaseOrderItemId', 'purchaseReceiptId', 'originalOrderId',
    'originalReceivableId', 'customerTypeId', 'draftPriceSourceId', 'lockedPriceSourceId',
    'demandId', 'demandIds', 'sourceReturnId', 'returnId', 'allocationId', 'receivableId',
    'payableId', 'refundId', 'exceptionId',
    'provinceCode', 'cityCode', 'districtCode', 'latitude', 'longitude', 'geomCrs',
];

const HIDDEN_AUDIT_KEYS = new Set([...BOOKKEEPING_KEYS, ...REFERENCE_KEYS]);

/**
 * 子记录（嵌套对象、明细行）里额外不展示的键。
 *
 * 收货地址是「某个字段的值」，明细行是「某条主记录的一部分」，它们自己的审计列
 * （创建时间 / 更新时间）在单元格里没有参照意义 —— 单据的时间在顶层已经给过了。顶层的时间戳保留。
 */
const CHILD_HIDDEN_AUDIT_KEYS = new Set([...HIDDEN_AUDIT_KEYS, 'createdAt', 'updatedAt']);

/** 该键是否属于「不展示给用户」的技术字段。 */
export function isHiddenAuditKey(key: string): boolean {
    return HIDDEN_AUDIT_KEYS.has(key);
}

/** 该键在子记录（嵌套对象 / 明细行）里是否不展示。 */
export function isHiddenChildAuditKey(key: string): boolean {
    return CHILD_HIDDEN_AUDIT_KEYS.has(key);
}

export default HIDDEN_AUDIT_KEYS;
