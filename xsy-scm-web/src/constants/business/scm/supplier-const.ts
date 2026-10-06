import {SmartEnum} from '/@/types/smart-enum';

/**
 * 供应商状态：启用 / 停用
 *
 * - 新建供应商强制 `ENABLED`（legacy 不变量 S7），前端表单不提供状态字段；
 * - 管理列表返回全部状态，下拉只返回 `ENABLED`（S11）。
 */
export const SUPPLIER_STATUS_ENUM: SmartEnum<string> = {
    ENABLED: {value: 'ENABLED', desc: '启用'},
    DISABLED: {value: 'DISABLED', desc: '停用'},
};

/**
 * 商品-供应商关系状态：启用 / 停用
 *
 * 只有 `ENABLED` 的关联才参与「可采购来源」判定。
 */
export const SUPPLIER_SKU_STATUS_ENUM: SmartEnum<string> = {
    ENABLED: {value: 'ENABLED', desc: '启用'},
    DISABLED: {value: 'DISABLED', desc: '停用'},
};

export default {
    SUPPLIER_STATUS_ENUM,
    SUPPLIER_SKU_STATUS_ENUM,
};
