/*
 * @Description: 表格id
 * @Author: zhuoda
 * @Date: 2022-08-21
 * @LastEditTime: 2022-08-21
 * @LastEditors: zhuoda
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
