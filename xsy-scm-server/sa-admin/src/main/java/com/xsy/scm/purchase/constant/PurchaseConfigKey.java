package com.xsy.scm.purchase.constant;

/** 采购配置沿用 SmartAdmin 的 Config 表。这里仅定义键、默认值和范围；读取、解析与参数校验由采购收货服务及数量计算器完成。 */
public final class PurchaseConfigKey {

    private PurchaseConfigKey() {
    }

    /**
     * 采购收货超收容差百分比，由配置种子初始化。
     */
    public static final String OVER_RECEIPT_TOLERANCE_PERCENT = "scm.purchase.over_receipt_tolerance_percent";

    /**
     * 配置缺失时的回退值，与配置种子值及计算器默认值一致。
     */
    public static final String OVER_RECEIPT_TOLERANCE_PERCENT_DEFAULT = "10";

    /**
     * 合法范围下界（含）。
     */
    public static final int OVER_RECEIPT_TOLERANCE_PERCENT_MIN = 0;

    /**
     * 合法范围上界（含）。越界 → {@code PURCHASE_TOLERANCE_CONFIG_INVALID(40999)}。
     */
    public static final int OVER_RECEIPT_TOLERANCE_PERCENT_MAX = 100;
}
