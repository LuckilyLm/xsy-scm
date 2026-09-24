package net.lab1024.sa.admin.module.scm.report.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

/**
 * 库存分析返回行（库存流水 / 损耗分析 / 当前库存价值 / 收发存数量版）。
 *
 * <p>数据源是 append-only 的 {@code inventory_movement}：报表只读它，永不改它。
 * 方向不另存一份枚举清单，由 {@code ScmInventoryMovementTypeEnum.isInbound()} 在服务层派生，
 * 避免报表维护出第二套 IN / OUT 口径。
 */
@Data
public class InventoryReportVO {

    /** 粒度 = 一条流水。 */
    @Data
    public static class MovementRow {
        private Long movementId;
        private OffsetDateTime occurredAt;
        private Long warehouseId;
        private String warehouseName;
        private Long skuId;
        private String productName;
        private String skuCode;
        private String movementType;
        /** 由流水类型派生的方向描述（入库 / 出库）。 */
        private String direction;
        private String sourceDocumentType;
        /** 来源单号：当前可稳定回查的来源类型才有值，其余为 null 并回落到下方两个 ID。 */
        private String sourceDocumentNo;
        private Long sourceDocumentId;
        private Long sourceDocumentItemId;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal quantity;
        private String unit;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal unitCost;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal costAmount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal beforeQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal afterQuantity;
        private String operator;
    }

    /**
     * 损耗 KPI。只统计 {@code STOCKTAKE_LOSS}（盘亏）与 {@code LOSS_REPORT}（报损）；
     * 盘盈 / 报溢不是损耗，不得混进来。当前系统没有「采购损耗」「退货损耗」的独立事实，
     * 因此本类也不提供这两项。
     */
    @Data
    public static class LossSummary {
        private Long stocktakeLossCount;
        private Long lossReportCount;
        private BigDecimal stocktakeLossCostAmount;
        private BigDecimal lossReportCostAmount;
        private BigDecimal totalLossCostAmount;
        private Integer costMissingCount;
    }

    /** 损耗明细行，粒度 = 一条损耗流水。 */
    @Data
    public static class LossRow {
        private Long movementId;
        private OffsetDateTime occurredAt;
        private Long warehouseId;
        private String warehouseName;
        private String productName;
        private String skuCode;
        /** {@code STOCKTAKE_LOSS} / {@code LOSS_REPORT}。 */
        private String lossType;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal quantity;
        private String unit;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal unitCost;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal costAmount;
        private String sourceDocumentNo;
        private String operator;
    }

    /** 粒度 = 一条 {@code inventory_balance} 行，即「当前时点」快照，不受查询区间影响。 */
    @Data
    public static class ValueRow {
        private Long balanceId;
        private Long warehouseId;
        private String warehouseName;
        private Long skuId;
        private String productName;
        private String skuCode;
        private String unit;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal quantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal reservedQuantity;
        /** {@code quantity - reserved_quantity}。 */
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal availableQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal avgCost;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal bookAmount;
    }

    /**
     * 收发存（数量版）行，粒度 = 仓库 × SKU × 记账单位。
     *
     * <p>刻意<b>没有</b>期初 / 期末数量与金额：{@code inventory_movement} 不是从库存起点完整
     * 覆盖的（历史采购入库由 V19 一次性回填，回填之前的量不在账上），且流水存的是本次
     * {@code unit_cost} 而非每次变动后的 {@code avg_cost}，所以历史期初期末均价无法还原。
     * 宁可只有期内净变动量，也不造一个看起来精确的假期初。
     */
    @Data
    public static class FlowSummaryRow {
        private Long warehouseId;
        private String warehouseName;
        private String productName;
        private String skuCode;
        private String unit;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal purchaseInQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal salesOutQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal stocktakeGainQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal stocktakeLossQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal gainReportQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal lossReportQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal transferInQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal transferOutQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal convertInQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal convertOutQuantity;
        /** 期内净变动量，按方向由流水类型求和。 */
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal netChangeQuantity;
    }
}
