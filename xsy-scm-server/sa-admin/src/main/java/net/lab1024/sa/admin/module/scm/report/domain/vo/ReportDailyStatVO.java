package net.lab1024.sa.admin.module.scm.report.domain.vo;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

/**
 * 按业务日聚合的统计行，同时服务趋势折线（按日画三条线）与每日统计表。
 *
 * <p>日期轴由 SQL 的 {@code generate_series} 补齐，因此「某天没有任何单据」也会返回一行零值，
 * 而不是让折线自己跳过那天。
 */
@Data
public class ReportDailyStatVO {

    /** 业务日期，{@code yyyy-MM-dd}（Asia/Shanghai）。 */
    private String bizDate;

    private Long confirmedOrderCount;

    private Long customerCount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal confirmedOrderAmount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal completedRefundAmount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal submittedPurchaseAmount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal purchaseInCostAmount;

    /** 当日无 {@code unit_cost} 的采购入库流水行数；&gt; 0 表示当日成本金额不完整。 */
    private Integer purchaseInCostMissingCount;
}
