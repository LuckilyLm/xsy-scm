/*
 * 供应商协同
 *
 * 与后端 com.xsy.scm.admin.module.business.supplier.constant 下枚举保持一致
 */

import { SmartEnum } from '/@/types/smart-enum';

/**
 * 供应商账号状态：1 启用，2 停用
 */
export const SUPPLIER_ACCOUNT_STATUS_ENUM: SmartEnum<number> = {
  ENABLED: { value: 1, desc: '启用' },
  DISABLED: { value: 2, desc: '停用' },
};

/**
 * 供应商商品提报审核状态：1 待审核，2 已通过，3 已驳回
 */
export const SUPPLIER_APPLY_STATUS_ENUM: SmartEnum<number> = {
  WAIT_AUDIT: { value: 1, desc: '待审核' },
  PASSED: { value: 2, desc: '已通过' },
  REJECTED: { value: 3, desc: '已驳回' },
};

/**
 * 供应商厂商信息状态：1 启用，2 停用
 */
export const SUPPLIER_MANUFACTURER_STATUS_ENUM: SmartEnum<number> = {
  ENABLED: { value: 1, desc: '启用' },
  DISABLED: { value: 2, desc: '停用' },
};

/**
 * 供应商对账单状态：1 待供应商确认，2 供应商已确认，3 已结算，4 已驳回
 */
export const SUPPLIER_STATEMENT_STATUS_ENUM: SmartEnum<number> = {
  WAIT_CONFIRM: { value: 1, desc: '待供应商确认' },
  CONFIRMED: { value: 2, desc: '供应商已确认' },
  SETTLED: { value: 3, desc: '已结算' },
  REJECTED: { value: 4, desc: '已驳回' },
};

export default {
  SUPPLIER_ACCOUNT_STATUS_ENUM,
  SUPPLIER_APPLY_STATUS_ENUM,
  SUPPLIER_MANUFACTURER_STATUS_ENUM,
  SUPPLIER_STATEMENT_STATUS_ENUM,
};
