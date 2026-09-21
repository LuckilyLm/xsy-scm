package net.lab1024.sa.admin.module.scm.purchase.constant;

/**
 * W5 采购域使用的 SmartAdmin Config（{@code t_config}）键。
 *
 * <p>设计依据：W5 Target Design §7.5（Q3a 修订后）。**载体是 SmartAdmin 原生 Config**，
 * 不是 Dict，也**不新建** {@code sys_config} 或任何 SCM 自定义配置基础设施。
 *
 * <p>本类只持有**字面量常量**（key / 默认值 / 范围），**不含任何读取逻辑**：
 * 读取由 {@code PurchaseReceiptService} 注入 SmartAdmin 的 {@code ConfigService} 完成
 * （只读路径 {@code ConfigService.getConfig(String)}），解析与范围校验放在
 * {@code PurchaseReceiptQuantityCalculator.tolerance(String raw)} 这个**纯函数**里。
 *
 * <p>**为什么不扩充 {@code ConfigKeyEnum}**：{@code ConfigKeyEnum} 属 SmartAdmin 底座
 * （在零修改清单内），且它在 {@code @PostConstruct} 期被枚举遍历做校验，
 * 扩充它会改变底座行为。因此 SCM 侧自持 key 常量。
 *
 * <p>**为什么默认值必须可回退**：配置缺失时若直接报错，会让「未配置环境」完全无法收货；
 * A 源的 {@code tolerancePercent()} 就是 {@code raw == null ? "10" : raw}（缺失回退默认）。
 */
public final class PurchaseConfigKey {

    private PurchaseConfigKey() {
    }

    /**
     * 采购收货超收容差百分比。V15 已播种该键（{@code ON CONFLICT (config_key) DO NOTHING}）。
     */
    public static final String OVER_RECEIPT_TOLERANCE_PERCENT =
            "scm.purchase.over_receipt_tolerance_percent";

    /**
     * 配置缺失时的回退值。与 V15 的播种值、A 源的默认值三者一致。
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
