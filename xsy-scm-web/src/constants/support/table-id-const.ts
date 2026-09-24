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
        SCM_PRICING_AGREEMENT: 50007,
        SCM_PRICING_TYPE_PRICE: 50008,
        SCM_PRICING_HISTORY: 50009,
        SCM_PRICING_BATCH: 50010,
        SCM_CUSTOMER_SKU_VISIBILITY: 50011,
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
        // 调拨波次（调拨单）
        SCM_INVENTORY_TRANSFER: 50023,
        // 阈值预警波次（预警列表 + 阈值配置）
        SCM_INVENTORY_WARNING: 50024,
        SCM_INVENTORY_WARNING_THRESHOLD: 50025,
        // 规格转换波次（转换单）
        SCM_INVENTORY_CONVERSION: 50026,
        // Finance R0 报表中心：一张表一个 id（列配置按表持久化，共用会让各 Tab 的列显隐互相覆盖）
        SCM_REPORT_OVERVIEW_DAILY: 50027,
        SCM_REPORT_SALES_PRODUCT: 50028,
        SCM_REPORT_SALES_CATEGORY: 50029,
        SCM_REPORT_SALES_CUSTOMER: 50030,
        SCM_REPORT_SALES_SELLER: 50031,
        SCM_REPORT_SALES_ITEM: 50032,
        SCM_REPORT_PURCHASE_PRODUCT: 50033,
        SCM_REPORT_PURCHASE_SUPPLIER: 50034,
        SCM_REPORT_PURCHASE_PURCHASER: 50035,
        SCM_REPORT_PURCHASE_ITEM: 50036,
        SCM_REPORT_PURCHASE_PRICE_TREND: 50037,
        SCM_REPORT_RECEIPT: 50038,
        SCM_REPORT_INBOUND: 50039,
        SCM_REPORT_PENDING_PUTAWAY: 50040,
        SCM_REPORT_INVENTORY_MOVEMENT: 50041,
        SCM_REPORT_INVENTORY_LOSS: 50042,
        SCM_REPORT_INVENTORY_VALUE: 50043,
        SCM_REPORT_INVENTORY_FLOW_SUMMARY: 50044,
        // P1 分拣管理：任务列表与按商品汇总（两张都是列表页，列配置沿用同一套数字 id）
        SCM_SORTING_TASK: 50045,
        SCM_SORTING_SUMMARY: 50046,
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
