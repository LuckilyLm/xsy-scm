package net.lab1024.sa.admin.module.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 规格转换类型（与参考项目 `ConvertTypeEnum` 的 `SPLIT` / `COMBINE` 对齐）。
 *
 * <p>本枚举只表达**业务形态**，不参与任何计算：折算关系（源数量 : 目标数量）
 * 由单据显式声明。系统不推断折算率 —— 一箱到底是 9.5 kg 还是 10 kg 取决于供应商与批次，
 * 猜错会直接污染两边余额。
 *
 * <p><b>方向不由本枚举决定</b>：无论拆零还是组合，源 SKU 一律「出」、目标 SKU 一律「入」。
 * 类型只影响展示与统计（「本月拆零多少单」）。
 */
@Getter
@RequiredArgsConstructor
public enum ScmInventoryConversionTypeEnum {

    /**
     * 整件拆零：1 箱 → 10 kg（源数量少、目标数量多）。
     */
    SPLIT("整件拆零"),

    /**
     * 组合拆分：若干散装 → 1 个组合品（源数量多、目标数量少）。
     */
    COMBINE("组合拆分");

    private final String desc;

    /**
     * 该值是否允许写入 {@code inventory_conversion.convert_type}（DB CHECK 白名单的同源判定）。
     */
    public static boolean isSupported(String value) {
        for (ScmInventoryConversionTypeEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按持久化值取枚举；未知值返回 {@code null}（调用方自行判定为参数错误）。
     */
    public static ScmInventoryConversionTypeEnum of(String value) {
        for (ScmInventoryConversionTypeEnum item : values()) {
            if (item.name().equals(value)) {
                return item;
            }
        }
        return null;
    }
}
