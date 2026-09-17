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

//客户业务表格初始化id
let businessCustomerInitTableId = 50000;

//订单业务表格初始化id
let businessOrderInitTableId = 60000;

//产品业务表格初始化id
let businessProductInitTableId = 70000;

//采购业务表格初始化id
let businessPurchaseInitTableId = 80000;
let businessFinanceInitTableId = 90000;

//供应商协同业务表格初始化id
let businessSupplierInitTableId = 100000;

//数据大屏业务表格初始化id
let businessScreenInitTableId = 101000;

//溯源业务表格初始化id
let businessTraceInitTableId = 102000;

//外部平台对接业务表格初始化id
let businessExternalInitTableId = 103000;

//单据打印业务表格初始化id
let businessPrintInitTableId = 104000;

export const TABLE_ID_CONST = {
  /**
   * 业务
   */
  BUSINESS: {
    OA: {
      NOTICE: businessOAInitTableId + 1, //通知公告
      ENTERPRISE: businessOAInitTableId + 2, //企业信息
      ENTERPRISE_EMPLOYEE: businessOAInitTableId + 3, //企业员工
      ENTERPRISE_BANK: businessOAInitTableId + 4, //企业银行
      ENTERPRISE_INVOICE: businessOAInitTableId + 5, //企业发票
    },
    ERP: {
      GOODS: businessERPInitTableId + 1, //商品管理
      STOCK_ADJUST: businessERPInitTableId + 2, //报损报溢
      STOCK_CHECK: businessERPInitTableId + 3, //库存盘点
      STOCK_BALANCE: businessERPInitTableId + 4, //库存余额
      STOCK_FLOW: businessERPInitTableId + 5, //库存流水
      STOCK_CHECK_ITEM: businessERPInitTableId + 6, //盘点明细
      PRODUCT_CONVERT: businessERPInitTableId + 7, //商品转换
    },
    CUSTOMER: {
      CUSTOMER: businessCustomerInitTableId + 1, //客户列表
      PERIOD: businessCustomerInitTableId + 2, //客户账期
      GOODS_VISIBLE: businessCustomerInitTableId + 3, //客户商品可见
      QRCODE: businessCustomerInitTableId + 4, //客户收款码
      DISCOUNT: businessCustomerInitTableId + 5, //客户折扣率
      PRODUCT_ALIAS: businessCustomerInitTableId + 6, //客户商品别名
    },
    ORDER: {
      ORDER: businessOrderInitTableId + 1, //销售订单
      ITEM: businessOrderInitTableId + 2, //订单明细
      LOG: businessOrderInitTableId + 3, //订单日志
      REFUND: businessOrderInitTableId + 4, //退款单
    },
    PRODUCT: {
      PRODUCT: businessProductInitTableId + 1, //产品列表
      SKU: businessProductInitTableId + 2, //产品规格
      PRICE: businessProductInitTableId + 3, //产品价格
      SUPPLIER: businessProductInitTableId + 4, //产品供应商
      BARCODE: businessProductInitTableId + 5, //商品条码
    },
    PURCHASE: {
      SUPPLIER: businessPurchaseInitTableId + 1, //供应商
      PURCHASE: businessPurchaseInitTableId + 2, //采购订单
      ITEM: businessPurchaseInitTableId + 3, //采购明细
      RECEIVE: businessPurchaseInitTableId + 4, //采购收货
      INQUIRY: businessPurchaseInitTableId + 5, //询价报价
    },
    FINANCE: {
      RECEIVABLE: businessFinanceInitTableId + 1, //应收单
      PAYMENT: businessFinanceInitTableId + 2, //收款单
      VOUCHER: businessFinanceInitTableId + 3, //会计凭证
      EXTERNAL_CONFIG: businessFinanceInitTableId + 4, //外部系统配置
      INVOICE: businessFinanceInitTableId + 5, //发票
    },
    SUPPLIER: {
      ACCOUNT: businessSupplierInitTableId + 1, //供应商账号
      PRODUCT_APPLY: businessSupplierInitTableId + 2, //供应商商品提报
      MANUFACTURER: businessSupplierInitTableId + 3, //供应商厂商信息
      STATEMENT: businessSupplierInitTableId + 4, //供应商对账单
    },
    SCREEN: {
      CONFIG: businessScreenInitTableId + 1, //大屏配置
    },
    TRACE: {
      BATCH: businessTraceInitTableId + 1, //溯源批次
      INSPECT: businessTraceInitTableId + 2, //检测报告
      CODE: businessTraceInitTableId + 3, //溯源码
    },
    EXTERNAL: {
      MAPPING: businessExternalInitTableId + 1, //平台映射
      SYNC_LOG: businessExternalInitTableId + 2, //同步日志
    },
    PRINT: {
      TEMPLATE: businessPrintInitTableId + 1, //打印模板
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
