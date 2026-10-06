import {SmartEnum} from '/@/types/smart-enum';

/**
 * 结算方式：独立结算 / 集团统一结算
 */
export const SETTLE_MODE_ENUM: SmartEnum<string> = {
    INDEPENDENT: {value: 'INDEPENDENT', desc: '独立结算'},
    GROUP: {value: 'GROUP', desc: '集团统一结算'},
};

/**
 * 客户状态：潜在 / 合作中 / 暂停合作 / 黑名单
 *
 * 只有「合作中」可交易（后端 `ScmCustomerStatusEnum.tradable()` 是唯一判定点）。
 */
export const CUSTOMER_STATUS_ENUM: SmartEnum<string> = {
    POTENTIAL: {value: 'POTENTIAL', desc: '潜在'},
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

/**
 * 客户类型状态：启用 / 停用
 *
 * 只有 `ENABLED` 的类型会出现在下拉里（后端 `/scm/customer/type/option/list` 已做过滤）；
 * 停用的类型仍可在管理列表中看到，但不允许再被新建客户引用（40431）。
 */
export const CUSTOMER_TYPE_STATUS_ENUM: SmartEnum<string> = {
    ENABLED: {value: 'ENABLED', desc: '启用'},
    DISABLED: {value: 'DISABLED', desc: '停用'},
};

export default {
    SETTLE_MODE_ENUM,
    CUSTOMER_STATUS_ENUM,
    CREDIT_PERIOD_TYPE_ENUM,
    CREDIT_PERIOD_UNIT_ENUM,
    CUSTOMER_TYPE_STATUS_ENUM,
};
