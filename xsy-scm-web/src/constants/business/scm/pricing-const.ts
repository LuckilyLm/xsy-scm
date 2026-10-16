import type {SmartEnum} from '/@/types/smart-enum';

export const SCM_PRICE_SOURCE_ENUM: SmartEnum<string> = {
    AGREEMENT: {value: 'AGREEMENT', desc: '客户协议价'},
    CUSTOMER_TYPE: {value: 'CUSTOMER_TYPE', desc: '客户类型价'},
    MARKET: {value: 'MARKET', desc: '市场价'}
};
export const SCM_PRICE_STATUS_ENUM: SmartEnum<string> = {
    PRICED: {value: 'PRICED', desc: '已定价'},
    UNPRICED: {value: 'UNPRICED', desc: '未定价'}
};
export const UNPRICED_REASON_ENUM: SmartEnum<string> = {
    NO_PRICE_SOURCE: {
        value: 'NO_PRICE_SOURCE',
        desc: '无可用价格来源'
    },
    AGREEMENT_COST_MISSING: {value: 'AGREEMENT_COST_MISSING', desc: '配送仓缺少有效单位成本或换算关系'},
    AGREEMENT_MARKET_PRICE_MISSING: {value: 'AGREEMENT_MARKET_PRICE_MISSING', desc: '商品缺少市场价'},
    AGREEMENT_RESULT_INVALID: {value: 'AGREEMENT_RESULT_INVALID', desc: '公式计算结果超出有效价格范围'}
};
export const UNAVAILABLE_REASON_ENUM: SmartEnum<string> = {
    SKU_NOT_FOUND: {value: 'SKU_NOT_FOUND', desc: '商品规格不存在'},
    SKU_OFF_SHELF: {value: 'SKU_OFF_SHELF', desc: '商品规格已下架'},
    SPU_OFF_SHELF: {value: 'SPU_OFF_SHELF', desc: '商品已下架'},
    CATEGORY_DISABLED: {value: 'CATEGORY_DISABLED', desc: '所属分类不可用'},
    NOT_VISIBLE: {value: 'NOT_VISIBLE', desc: '客户不可见'}
};
