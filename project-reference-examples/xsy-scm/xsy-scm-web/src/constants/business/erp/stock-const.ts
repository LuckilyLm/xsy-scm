/*
 * 库存
 *
 * 与后端 com.xsy.scm.admin.module.business.stock.constant 下枚举保持一致
 */

import { SmartEnum } from '/@/types/smart-enum';

/**
 * 库存调整单状态：1 待审核，2 已完成，3 已驳回
 */
export const ADJUST_STATUS_ENUM: SmartEnum<number> = {
  PENDING: {
    value: 1,
    desc: '待审核',
  },
  COMPLETED: {
    value: 2,
    desc: '已完成',
  },
  REJECTED: {
    value: 3,
    desc: '已驳回',
  },
};

/**
 * 库存调整类型：1 报损，2 报溢，3 盘点调整，4 规格转换
 */
export const ADJUST_TYPE_ENUM: SmartEnum<number> = {
  LOSS: {
    value: 1,
    desc: '报损',
  },
  OVERFLOW: {
    value: 2,
    desc: '报溢',
  },
  CHECK_ADJUST: {
    value: 3,
    desc: '盘点调整',
  },
  CONVERT: {
    value: 4,
    desc: '规格转换',
  },
};

/**
 * 盘点单状态：1 待盘点，2 盘点中，3 已完成，4 已取消
 */
export const CHECK_STATUS_ENUM: SmartEnum<number> = {
  PENDING: {
    value: 1,
    desc: '待盘点',
  },
  CHECKING: {
    value: 2,
    desc: '盘点中',
  },
  COMPLETED: {
    value: 3,
    desc: '已完成',
  },
  CANCELLED: {
    value: 4,
    desc: '已取消',
  },
};

/**
 * 盘点类型：1 全面盘点，2 动态盘点，3 抽盘
 */
export const CHECK_TYPE_ENUM: SmartEnum<number> = {
  FULL: {
    value: 1,
    desc: '全面盘点',
  },
  DYNAMIC: {
    value: 2,
    desc: '动态盘点',
  },
  SPOT: {
    value: 3,
    desc: '抽盘',
  },
};

/**
 * 库存流水类型：1 采购入库，2 销售出库，3 退货入库，4 报损，5 报溢，6 盘点调整，7 规格转换出，8 规格转换入
 */
export const STOCK_FLOW_TYPE_ENUM: SmartEnum<number> = {
  PURCHASE_IN: { value: 1, desc: '采购入库' },
  SALE_OUT: { value: 2, desc: '销售出库' },
  REFUND_IN: { value: 3, desc: '退货入库' },
  LOSS: { value: 4, desc: '报损' },
  OVERFLOW: { value: 5, desc: '报溢' },
  CHECK_ADJUST: { value: 6, desc: '盘点调整' },
  CONVERT_OUT: { value: 7, desc: '规格转换出' },
  CONVERT_IN: { value: 8, desc: '规格转换入' },
};

/**
 * 关联业务类型：1 采购，2 订单，3 分拣，4 盘点，5 报损报溢，6 规格转换
 */
export const STOCK_BIZ_TYPE_ENUM: SmartEnum<number> = {
  PURCHASE: { value: 1, desc: '采购' },
  ORDER: { value: 2, desc: '订单' },
  SORTING: { value: 3, desc: '分拣' },
  CHECK: { value: 4, desc: '盘点' },
  ADJUST: { value: 5, desc: '报损报溢' },
  CONVERT: { value: 6, desc: '规格转换' },
};

/**
 * 流水方向：1 入，2 出
 */
export const FLOW_DIRECTION_ENUM: SmartEnum<number> = {
  IN: { value: 1, desc: '入' },
  OUT: { value: 2, desc: '出' },
};

/**
 * 商品转换类型：1 整件拆零，2 组合拆分
 */
export const CONVERT_TYPE_ENUM: SmartEnum<number> = {
  SPLIT: { value: 1, desc: '整件拆零' },
  COMBINE: { value: 2, desc: '组合拆分' },
};

/**
 * 商品转换单状态：1 待审核，2 已完成，3 已驳回
 */
export const CONVERT_STATUS_ENUM: SmartEnum<number> = {
  PENDING: { value: 1, desc: '待审核' },
  COMPLETED: { value: 2, desc: '已完成' },
  REJECTED: { value: 3, desc: '已驳回' },
};

/**
 * 商品转换来源：1 手工创建，2 发货差异表批量转换
 */
export const CONVERT_SOURCE_TYPE_ENUM: SmartEnum<number> = {
  MANUAL: { value: 1, desc: '手工创建' },
  DELIVERY_DIFF: { value: 2, desc: '发货差异表批量转换' },
};

export default {
  ADJUST_STATUS_ENUM,
  ADJUST_TYPE_ENUM,
  CHECK_STATUS_ENUM,
  CHECK_TYPE_ENUM,
  STOCK_FLOW_TYPE_ENUM,
  STOCK_BIZ_TYPE_ENUM,
  FLOW_DIRECTION_ENUM,
  CONVERT_TYPE_ENUM,
  CONVERT_STATUS_ENUM,
  CONVERT_SOURCE_TYPE_ENUM,
};
