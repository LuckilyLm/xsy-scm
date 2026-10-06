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
    }
};
export const UNAVAILABLE_REASON_ENUM: SmartEnum<string> = {
    SKU_NOT_FOUND: {value: 'SKU_NOT_FOUND', desc: '商品规格不存在'},
    SKU_OFF_SHELF: {value: 'SKU_OFF_SHELF', desc: '商品规格已下架'},
    SPU_OFF_SHELF: {value: 'SPU_OFF_SHELF', desc: '商品已下架'},
    CATEGORY_DISABLED: {value: 'CATEGORY_DISABLED', desc: '所属分类不可用'},
    NOT_VISIBLE: {value: 'NOT_VISIBLE', desc: '客户不可见'}
};
