/*
 * @Description: 表格id
 * @LastEditTime: 2022-08-21
 * @LastEditors: 
 */

//system系统功能表格初始化id
let systemInitTableId = 10000;

//support支撑功能表格初始化id
let supportInitTableId = 20000;

//业务表格初始化id
let businessOAInitTableId = 30000;

let businessERPInitTableId = 40000;

export const TABLE_ID_CONST = {
  /**
   * 业务
   */
  BUSINESS: {
    SCM_PRODUCT: 50001,
    SCM_PRODUCT_CATEGORY: 50002,
    SCM_CUSTOMER: 50003,
    SCM_CUSTOMER_TYPE: 50004,
    SCM_SUPPLIER: 50005,
    SCM_SUPPLIER_SKU: 50006,
    SCM_PRICING_AGREEMENT:50007,
    SCM_PRICING_TYPE_PRICE:50008,
    SCM_PRICING_HISTORY:50009,
    SCM_PRICING_BATCH:50010,
    SCM_CUSTOMER_SKU_VISIBILITY:50011,
    // W4 销售订单沿用 C 的裸数字（602–605）；W5 起统一走常量（A23）
    SCM_ORDER: 602,
    SCM_ORDER_RETURN: 603,
    SCM_ORDER_REFUND: 604,
    SCM_ORDER_LOG: 605,
    SCM_PURCHASE_ORDER: 50012,
    SCM_PURCHASE_RECEIPT: 50013,
    SCM_PURCHASE_DEMAND: 50014,
    SCM_PURCHASE_LOG: 50015,
    SCM_WAREHOUSE: 50016,
    // W6 库存域（两个只读查询页）
    SCM_INVENTORY_BALANCE: 50017,
    SCM_INVENTORY_MOVEMENT: 50018,
    // 出库波次（出库单 / 库存预留）
    SCM_INVENTORY_OUTBOUND: 50019,
    SCM_INVENTORY_RESERVATION: 50020,
    // 盘点波次（盘点单）
    SCM_INVENTORY_STOCKTAKE: 50021,
    // 报损报溢波次（报损报溢单）
    SCM_INVENTORY_LOSS_GAIN: 50022,
    OA: {
      NOTICE: businessOAInitTableId + 1, //通知公告
      ENTERPRISE: businessOAInitTableId + 2, //企业信息
      ENTERPRISE_EMPLOYEE: businessOAInitTableId + 3, //企业员工
      ENTERPRISE_BANK: businessOAInitTableId + 4, //企业银行
      ENTERPRISE_INVOICE: businessOAInitTableId + 5, //企业发票
    },
    ERP: {
      GOODS: businessERPInitTableId + 1, //商品管理
    },
  },

  /**
   * 系统
   */
  SYSTEM: {
    EMPLOYEE: systemInitTableId + 1, //员工
    MENU: systemInitTableId + 2, //菜单
    POSITION: systemInitTableId + 3, //职位
  },
  /**
   * 支撑
   */
  SUPPORT: {
    CONFIG: supportInitTableId + 1, //参数配置
    DICT: supportInitTableId + 2, //字典
    SERIAL_NUMBER: supportInitTableId + 3, //单号
    OPERATE_LOG: supportInitTableId + 4, //请求监控
    HEART_BEAT: supportInitTableId + 5, //心跳
    LOGIN_LOG: supportInitTableId + 6, //登录日志
    RELOAD: supportInitTableId + 7, //reload
    HELP_DOC: supportInitTableId + 8, //帮助文档
    JOB: supportInitTableId + 9, //Job
    JOB_LOG: supportInitTableId + 10, //JobLog
    MAIL: supportInitTableId + 11,
  },
};
