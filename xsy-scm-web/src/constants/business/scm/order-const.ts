/* 来源：project-reference-examples/xsy-scm/xsy-scm-web/src/constants/business/order/order-const.ts
复制日期：2026-09-16。Copy First + Adapt。
剪枝：履约/支付/裸ID/独立明细写入口/列拖拽。
适配：四状态、API、权限、四位定点、NULL、version、幂等、错误重试。
验收：W4 单测、TS 棘轮与 Playwright。 */
import type {SmartEnum} from '/@/types/smart-enum';

export const SCM_ORDER_STATUS_ENUM: SmartEnum<string> = {
    DRAFT: {value: 'DRAFT', desc: '草稿'},
    PENDING: {value: 'PENDING', desc: '待确认'},
    CONFIRMED: {value: 'CONFIRMED', desc: '已确认'},
    CANCELLED: {value: 'CANCELLED', desc: '已取消'}
};
export const SCM_ORDER_SOURCE_ENUM: SmartEnum<string> = {
    ADMIN: {value: 'ADMIN', desc: '后台录单'},
    MALL: {value: 'MALL', desc: '商城'},
    SUPPLEMENT: {value: 'SUPPLEMENT', desc: '补单'},
    IMPORT: {value: 'IMPORT', desc: 'Excel导入'}
};
export const SCM_ORDER_PRICE_SOURCE_ENUM: SmartEnum<string> = {
    AGREEMENT: {value: 'AGREEMENT', desc: '客户协议价'},
    CUSTOMER_TYPE: {value: 'CUSTOMER_TYPE', desc: '客户类型价'},
    MARKET: {value: 'MARKET', desc: '市场价'},
    OVERRIDE: {value: 'OVERRIDE', desc: '人工改价'}
};
export const SCM_ORDER_RETURN_STATUS_ENUM: SmartEnum<string> = {
    PENDING: {value: 'PENDING', desc: '待审核'},
    APPROVED: {value: 'APPROVED', desc: '已批准'},
    REJECTED: {value: 'REJECTED', desc: '已驳回'},
    CANCELLED: {value: 'CANCELLED', desc: '已取消'}
};
export const SCM_ORDER_REFUND_STATUS_ENUM: SmartEnum<string> = {
    PENDING: {value: 'PENDING', desc: '待退款'},
    COMPLETED: {value: 'COMPLETED', desc: '已完成'}
};
export const SCM_ORDER_OPERATION_ENUM: SmartEnum<string> = {
    CREATE: {value: 'CREATE', desc: '创建'},
    UPDATE: {value: 'UPDATE', desc: '修改'},
    SUBMIT: {value: 'SUBMIT', desc: '提交'},
    ACTUAL_QUANTITY: {value: 'ACTUAL_QUANTITY', desc: '实重录入'},
    CONFIRM: {value: 'CONFIRM', desc: '确认'},
    CANCEL: {value: 'CANCEL', desc: '取消'},
    RESERVE_STOCK: {value: 'RESERVE_STOCK', desc: '预留库存'},
    RETURN: {value: 'RETURN', desc: '退货'},
    REFUND: {value: 'REFUND', desc: '退款'}
};
export default {
    SCM_ORDER_STATUS_ENUM,
    SCM_ORDER_SOURCE_ENUM,
    SCM_ORDER_PRICE_SOURCE_ENUM,
    SCM_ORDER_RETURN_STATUS_ENUM,
    SCM_ORDER_REFUND_STATUS_ENUM,
    SCM_ORDER_OPERATION_ENUM
};
