import {SmartEnum} from '/@/types/smart-enum';

/**
 * 结算方式：独立结算 / 集团统一结算
 */
export const SETTLE_MODE_ENUM: SmartEnum<string> = {
  INDEPENDENT: {value: 'INDEPENDENT', desc: '独立结算'},
  GROUP: {value: 'GROUP', desc: '集团统一结算'},
};

/**
 * 客户状态：合作中 / 暂停合作 / 黑名单
 *
 * 新建客户默认「合作中」；只有「合作中」可交易（后端 `ScmCustomerStatusEnum.tradable()` 是唯一判定点）。
 */
export const CUSTOMER_STATUS_ENUM: SmartEnum<string> = {
  COOPERATING: {value: 'COOPERATING', desc: '合作中'},
  SUSPENDED: {value: 'SUSPENDED', desc: '暂停合作'},
  BLACKLIST: {value: 'BLACKLIST', desc: '黑名单'},
};

/**
 * 账期类型：按金额 / 按时间
 */
export const CREDIT_PERIOD_TYPE_ENUM: SmartEnum<string> = {
  BY_AMOUNT: {value: 'BY_AMOUNT', desc: '按金额'},
  BY_TIME: {value: 'BY_TIME', desc: '按时间'},
};

/**
 * 账期单位：天 / 月
 *
 * 单位为「月」时可选固定结算日，取值 1–28（保证 2 月也存在该日期）。
 */
export const CREDIT_PERIOD_UNIT_ENUM: SmartEnum<string> = {
  DAY: {value: 'DAY', desc: '天'},
  MONTH: {value: 'MONTH', desc: '月'},
};

export default {
  SETTLE_MODE_ENUM,
  CUSTOMER_STATUS_ENUM,
  CREDIT_PERIOD_TYPE_ENUM,
  CREDIT_PERIOD_UNIT_ENUM,
};
