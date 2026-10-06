package com.xsy.scm.purchase.constant;

/**
 * 采购域使用的 SmartAdmin Config（{@code t_config}）键。
 *
 * <p>
 * 使用 SmartAdmin 原生 Config 作为载体，不使用 Dict，也不新建 SCM 配置表。本类只持有<b>字面量常量</b>（key / 默认值 / 范围），<b>不含任何读取逻辑</b>：读取由
 * {@code PurchaseReceiptService} 注入的 {@code ConfigService} 完成，解析与范围校验放在
 * {@code PurchaseReceiptQuantityCalculator.tolerance(String raw)} 这个<b>纯函数</b>里。
 *
 * <p>
 * <b>不扩充 {@code ConfigKeyEnum}</b>：它属 SmartAdmin 底座（在零修改清单内），且它在 {@code @PostConstruct} 期被枚举遍历做校验，扩充它会改变底座行为，因此 SCM 侧自持
 * key 常量。
 *
 * <p>
 * <b>默认值必须可回退</b>：配置缺失时若直接报错，会让「未配置环境」完全无法收货，因此缺失时回退默认值 10。
 */
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
