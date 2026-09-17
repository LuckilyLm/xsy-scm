/*
 * 产品
 *
 * 与后端 com.xsy.scm.admin.module.business.product.constant 下枚举保持一致
 */

import { SmartEnum } from '/@/types/smart-enum';

/**
 * 产品类型：1 标品，2 非标品
 */
export const PRODUCT_TYPE_ENUM: SmartEnum<number> = {
  STANDARD: { value: 1, desc: '标品' },
  NON_STANDARD: { value: 2, desc: '非标品' },
};

/**
 * 计量方式：1 按件，2 按重
 */
export const MEASURE_TYPE_ENUM: SmartEnum<number> = {
  BY_PIECE: { value: 1, desc: '按件' },
  BY_WEIGHT: { value: 2, desc: '按重' },
};

/**
 * 采购模式：1 自采，2 供应商送货
 */
export const PURCHASE_MODE_ENUM: SmartEnum<number> = {
  SELF: { value: 1, desc: '自采' },
  SUPPLIER_DELIVERY: { value: 2, desc: '供应商送货' },
};

/**
 * 产品状态：1 草稿，2 待上架，3 已上架，4 已下架，5 已作废
 */
export const PRODUCT_STATUS_ENUM: SmartEnum<number> = {
  DRAFT: { value: 1, desc: '草稿' },
  WAIT_SHELF: { value: 2, desc: '待上架' },
  ON_SHELF: { value: 3, desc: '已上架' },
  OFF_SHELF: { value: 4, desc: '已下架' },
  INVALID: { value: 5, desc: '已作废' },
};

/**
 * 价格类型：1 基础价，2 客户分级价，3 时价，4 协议价
 */
export const PRICE_TYPE_ENUM: SmartEnum<number> = {
  BASE: { value: 1, desc: '基础价' },
  CUSTOMER_LEVEL: { value: 2, desc: '客户分级价' },
  CURRENT: { value: 3, desc: '时价' },
  AGREEMENT: { value: 4, desc: '协议价' },
};

/**
 * 价格状态：1 生效，2 失效
 */
export const PRICE_STATUS_ENUM: SmartEnum<number> = {
  ENABLED: { value: 1, desc: '生效' },
  DISABLED: { value: 2, desc: '失效' },
};

/**
 * 规格状态：1 启用，2 停用（后端无枚举类，仅用于前端展示）
 */
export const SKU_STATUS_ENUM: SmartEnum<number> = {
  ENABLED: { value: 1, desc: '启用' },
  DISABLED: { value: 2, desc: '停用' },
};

export default {
  PRODUCT_TYPE_ENUM,
  MEASURE_TYPE_ENUM,
  PURCHASE_MODE_ENUM,
  PRODUCT_STATUS_ENUM,
  PRICE_TYPE_ENUM,
  PRICE_STATUS_ENUM,
  SKU_STATUS_ENUM,
};
