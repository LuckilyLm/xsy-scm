package net.lab1024.sa.admin.module.scm.screen.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存健康度判定输入行（DAO 投影，不是接口输出）。
 *
 * <p>刻意只带**原始事实**（可用量 + 上下限），不带分类结果：分档规则只能有
 * {@link net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryWarningStatusEnum#evaluate}
 * 一处实现。若让 SQL 直接算出「正常 / 低于下限 / 高于上限」，就出现了第二份规则，
 * 而两份规则一旦漂移，大屏的健康度会和预警列表对不上 —— 那比没有这个面板更糟。
 *
 * <p>{@code availableQuantity} 为 {@code null} 表示「配置了阈值但还没有余额行」，
 * 按预警枚举的既有口径计为 0（设了下限却一件没有，正是要预警的情形）。
 */
@Data
@Schema(description = "库存健康度判定输入行")
public class ScreenInventoryHealthRow {

    @Schema(description = "可用量（现有量 − 预留量）；null = 无余额行")
    private BigDecimal availableQuantity;

    @Schema(description = "阈值下限；null = 未配置")
    private BigDecimal warnMin;

    @Schema(description = "阈值上限；null = 未配置")
    private BigDecimal warnMax;
}
