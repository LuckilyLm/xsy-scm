/*
 * 供应商域枚举
 *
 * 来源：**新写**。
 * C 的 `constants/business/supplier/supplier-const.ts` 全部是**供应商协同**枚举
 * （账号 / 报品 / 厂商 / 对账），与供应商主数据无关；C 把主数据状态
 * `SUPPLIER_STATUS_ENUM` 放在了 `constants/business/purchase/purchase-const.ts` 里，
 * 属于归类错误。V2 按业务域归位到本文件。
 */

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
