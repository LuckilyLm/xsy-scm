/*
 * 采购收货枚举（与后端 com.xsy.scm.admin.module.business.purchase.constant 保持一致）
 */

// 收货状态：1 已收，2 已入库，3 已作废
const RECEIVE_STATUS_ENUM = {
  RECEIVED: { value: 1, desc: '已收' },
  STOCKED: { value: 2, desc: '已入库' },
  INVALID: { value: 3, desc: '已作废' },
};

// 收货标记：1 正常，2 少收，3 超收（动态记录少多收，Q2）
const RECEIVE_FLAG_ENUM = {
  NORMAL: { value: 1, desc: '正常' },
  UNDER: { value: 2, desc: '少收' },
  OVER: { value: 3, desc: '超收' },
};

export default {
  RECEIVE_STATUS_ENUM,
  RECEIVE_FLAG_ENUM,
};
