package com.xsy.scm.report.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Data;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;

/**
 * 库存分析返回行（库存流水 / 损耗分析 / 当前库存价值 / 收发存数量版）。
 *
 * <p>
 * 数据源是 append-only 的 {@code xsy_inventory_movement}：报表只读它，永不改它。方向不另存一份枚举清单，由
 * {@code ScmInventoryMovementTypeEnum.isInbound()} 在服务层派生，避免报表维护出第二套 IN / OUT 判断。
 */
@Data
public class InventoryReportVO {

    /**
     * 粒度 = 一条流水。
     */
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
        @JsonSerialize(using = ScmOperatorNameSerializer.class)
        private String operator;
    }

    /**
     * 损耗 KPI。只统计 {@code STOCKTAKE_LOSS}（盘亏）与 {@code LOSS_REPORT}（报损）；盘盈 / 报溢不是损耗，不得混进来。
     * 当前系统没有「采购损耗」「退货损耗」的独立记录，因此本类也不提供这两项。
     */
    @Data
    public static class LossSummary {
        private Long stocktakeLossCount;
        private Long lossReportCount;
        /** 同一种损耗可有多种库存记账单位，显示按单位分组的文本而不跨单位求和。 */
        private String stocktakeLossQuantityText;
        private String lossReportQuantityText;
        private List<LossTrendRow> dailyTrend;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal stocktakeLossCostAmount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal lossReportCostAmount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal totalLossCostAmount;
        private Integer costMissingCount;
    }

    /**
     * 期内分组只按损耗类型与真实库存单位聚合。
     */
    @Data
    public static class LossUnitRow {
        private String movementType;
        private String unit;
        private BigDecimal quantity;
    }

    /**
     * 服务端以 Asia/Shanghai 的自然日聚合，成本缺失的日金额为 null。
     */
    @Data
    public static class LossTrendRow {
        private LocalDate bizDate;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal stocktakeLossCostAmount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal lossReportCostAmount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal totalLossCostAmount;
        private Long costMissingCount;
    }

    /**
     * 损耗明细行，粒度 = 一条损耗流水。
     */
    @Data
    public static class LossRow {
        private Long movementId;
        private OffsetDateTime occurredAt;
        private Long warehouseId;
        private String warehouseName;
        private String productName;
        private String skuCode;
        /**
         * {@code STOCKTAKE_LOSS} / {@code LOSS_REPORT}。
         */
        private String lossType;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal quantity;
        private String unit;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal unitCost;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal costAmount;
        private String sourceDocumentNo;
        @JsonSerialize(using = ScmOperatorNameSerializer.class)
        private String operator;
    }

    /**
     * 粒度 = 一条 {@code xsy_inventory_balance} 行，即当前时点的余额，不受查询区间影响。
     */
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
        /**
         * {@code quantity - reserved_quantity}。
         */
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal availableQuantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal avgCost;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal bookAmount;
    }

    /**
     * 负库存损耗行：查询时点的余额快照（{@code quantity < 0}），不是历史月份回算。
     *
     * <p>
     * 负数量本身不是损耗事实，只是「卖出去了但没有入库成本」的缺口。真实损耗仍以报损单 / 盘亏流水为准， 本报表按月导出供负责人估算，不生成任何库存流水或报损单。
     */
    @Data
    public static class NegativeStockRow {
        private Long balanceId;
        private Long warehouseId;
        private String warehouseName;
        private Long skuId;
        private String productName;
        private String skuCode;
        private String specName;
        /** 余额的记账单位；一个 (仓库, SKU) 只有一个单位。 */
        private String unit;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal quantity;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal avgCost;
        /** {@code quantity × avgCost}，跟随余额符号为负；无成本权限时为 null。 */
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal estimatedAmount;
        /** 最近一条销售出库流水的时间；从未销售出库时为 null（例如纯调拨导致的负数）。 */
        private OffsetDateTime lastOutboundAt;
        private String lastOutboundNo;
    }

    /**
     * 负库存损耗汇总。数量跨单位不可相加，因此只给规格数 / 仓库数 / 金额。
     */
    @Data
    public static class NegativeStockSummary {
        private Long rowCount;
        private Long warehouseCount;
        /** 均价为 0 的行数：成本未知，估算金额会偏低，页面必须提示而不是当 0 展示完整。 */
        private Long zeroCostRowCount;
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal totalEstimatedAmount;
        /** 取数时点；负库存是当前状态，历史月份需要月度快照表才能回溯。 */
        private OffsetDateTime snapshotAt;
    }

    /**
     * 收发存数量报表按仓库、商品规格和单位汇总期内变动。缺少完整历史成本时不推算看似精确的期初、期末金额。
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
        /**
         * 期内净变动量，按方向由流水类型求和。
         */
        @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
        private BigDecimal netChangeQuantity;
    }
}
