/*
 * 采购
 *
 * 与后端 com.xsy.scm.admin.module.business.purchase.constant 下枚举保持一致
 */

import { SmartEnum } from '/@/types/smart-enum';

/**
 * 采购单状态：1 待接单，2 采购中，3 部分收货，4 已完成，5 已取消
 */
export const PURCHASE_STATUS_ENUM: SmartEnum<number> = {
  WAIT_ACCEPT: { value: 1, desc: '待接单' },
  PURCHASING: { value: 2, desc: '采购中' },
  PART_RECEIVED: { value: 3, desc: '部分收货' },
  COMPLETED: { value: 4, desc: '已完成' },
  CANCELLED: { value: 5, desc: '已取消' },
};

/**
 * 采购明细状态：1 待收，2 部分收，3 已收齐
 */
export const PURCHASE_ITEM_STATUS_ENUM: SmartEnum<number> = {
  WAIT_RECEIVE: { value: 1, desc: '待收' },
  PART_RECEIVED: { value: 2, desc: '部分收' },
  ALL_RECEIVED: { value: 3, desc: '已收齐' },
};

/**
 * 收货状态：1 已收，2 已入库，3 已作废
 */
export const RECEIVE_STATUS_ENUM: SmartEnum<number> = {
  RECEIVED: { value: 1, desc: '已收' },
  STOCKED: { value: 2, desc: '已入库' },
  INVALID: { value: 3, desc: '已作废' },
};

/**
 * 收货标记：1 正常，2 少收，3 超收（动态记录少多收，Q2）
 */
export const RECEIVE_FLAG_ENUM: SmartEnum<number> = {
  NORMAL: { value: 1, desc: '正常' },
  UNDER: { value: 2, desc: '少收' },
  OVER: { value: 3, desc: '超收' },
};

/**
 * 供应商状态：1 启用，2 停用（后端无枚举类，仅用于前端展示）
 */
export const SUPPLIER_STATUS_ENUM: SmartEnum<number> = {
  ENABLED: { value: 1, desc: '启用' },
  DISABLED: { value: 2, desc: '停用' },
};

/**
 * 询价单状态：1 待报价，2 报价中，3 已完成，4 已取消
 */
export const INQUIRY_STATUS_ENUM: SmartEnum<number> = {
  PENDING: { value: 1, desc: '待报价' },
  QUOTING: { value: 2, desc: '报价中' },
  COMPLETED: { value: 3, desc: '已完成' },
  CANCELLED: { value: 4, desc: '已取消' },
};

export default {
  PURCHASE_STATUS_ENUM,
  PURCHASE_ITEM_STATUS_ENUM,
  RECEIVE_STATUS_ENUM,
  RECEIVE_FLAG_ENUM,
  SUPPLIER_STATUS_ENUM,
  INQUIRY_STATUS_ENUM,
};
