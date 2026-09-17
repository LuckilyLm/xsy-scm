/*
 * 客户
 *
 * 与后端 com.xsy.scm.admin.module.business.customer.constant 下枚举保持一致
 */

import { SmartEnum } from '/@/types/smart-enum';

/**
 * 客户类型：1 企业，2 个人，3 集团
 */
export const CUSTOMER_TYPE_ENUM: SmartEnum<number> = {
  ENTERPRISE: { value: 1, desc: '企业' },
  PERSONAL: { value: 2, desc: '个人' },
  GROUP: { value: 3, desc: '集团' },
};

/**
 * 结算方式：1 独立结算，2 集团统一结算
 */
export const SETTLE_MODE_ENUM: SmartEnum<number> = {
  INDEPENDENT: { value: 1, desc: '独立结算' },
  GROUP_UNIFIED: { value: 2, desc: '集团统一结算' },
};

/**
 * 客户状态：1 潜在，2 合作中，3 暂停合作，4 黑名单
 */
export const CUSTOMER_STATUS_ENUM: SmartEnum<number> = {
  POTENTIAL: { value: 1, desc: '潜在' },
  COOPERATING: { value: 2, desc: '合作中' },
  SUSPENDED: { value: 3, desc: '暂停合作' },
  BLACKLIST: { value: 4, desc: '黑名单' },
};

/**
 * 账期类型：1 按金额，2 按时间
 */
export const PERIOD_TYPE_ENUM: SmartEnum<number> = {
  BY_AMOUNT: { value: 1, desc: '按金额' },
  BY_TIME: { value: 2, desc: '按时间' },
};

/**
 * 账期单位：1 天，2 月
 */
export const PERIOD_UNIT_ENUM: SmartEnum<number> = {
  DAY: { value: 1, desc: '天' },
  MONTH: { value: 2, desc: '月' },
};

/**
 * 账期状态：1 生效，2 暂停
 */
export const PERIOD_STATUS_ENUM: SmartEnum<number> = {
  ENABLED: { value: 1, desc: '生效' },
  PAUSED: { value: 2, desc: '暂停' },
};

/**
 * 商品可见性：1 显示，2 屏蔽
 */
export const VISIBLE_TYPE_ENUM: SmartEnum<number> = {
  SHOW: { value: 1, desc: '显示' },
  HIDE: { value: 2, desc: '屏蔽' },
};

export default {
  CUSTOMER_TYPE_ENUM,
  SETTLE_MODE_ENUM,
  CUSTOMER_STATUS_ENUM,
  PERIOD_TYPE_ENUM,
  PERIOD_UNIT_ENUM,
  PERIOD_STATUS_ENUM,
  VISIBLE_TYPE_ENUM,
};
