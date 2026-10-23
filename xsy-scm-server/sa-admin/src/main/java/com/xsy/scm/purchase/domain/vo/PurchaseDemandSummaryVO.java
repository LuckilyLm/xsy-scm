package com.xsy.scm.purchase.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;

/**
 * 缺口预览按仓库、商品规格和需求单位汇总。数量由服务端精确计算，单位不一致时明确返回无法比较，前端不猜换算比例。
 */
@Data
public class PurchaseDemandSummaryVO {

    private Long skuId;

    private String skuCode;

    private String productName;

    /**
     * 规格名（{@code xsy_product_sku.spec_name}）。
     */
    private String skuName;

    private String categoryName;

    /**
     * 需求单位（订单销售单位快照，来自 {@code xsy_sales_order_item.sale_unit_snapshot}）。
     */
    private String demandUnit;

    /**
     * 余额记账单位；{@code NO_BALANCE} 时为 null。
     */
    private String inventoryUnit;

    private Long sourceOrderCount;

    private Long sourceLineCount;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal orderDemandQuantity;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal onHandQuantity;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal reservedQuantity;

    /**
     * 全仓净可用 = 现有量 − 全量已预留量；SQL 内算好的派生列，{@code NO_BALANCE} 时按 0 展示。
     *
     * <p>
     * 它包含本批订单自身已占用的预留，因此不能用来判断本批是否缺料，见 {@link #stockAvailableForSelectedOrders}。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal availableQuantity;

    /**
     * 本批预览订单集合自身在该 (仓库, SKU) 上的 ACTIVE 预留量，是 {@link #reservedQuantity} 的子集。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal selectedOrderReservedQuantity;

    /**
     * 其他订单/业务占用的预留 = {@code reservedQuantity - selectedOrderReservedQuantity}。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal otherReservedQuantity;

    /**
     * 本批可用 = 现有量 − 其他业务预留；为本批订单决定是否补货的正确分母。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal stockAvailableForSelectedOrders;

    /**
     * 库存对比差额 = {@code max(orderDemandQuantity - stockAvailableForSelectedOrders, 0)}； {@code UNIT_MISMATCH} 时为
     * null（不返回伪造差额）。
     *
     * <p>
     * 这是已确认订单与当前库存/预留的对比结果，不是最终净采购建议；本视图不扣减未收采购单数量或已履约量。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal stockComparisonGap;

    /**
     * 未分配的采购在途数量。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal inTransitQuantity;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal purchaseCoverageQuantity;

    /**
     * 扣除库存、在途数量和已有采购覆盖后的净采购缺口。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal netPurchaseGap;

    /**
     * STOCK_ENOUGH / SHORTAGE / ZERO_STOCK / UNIT_MISMATCH / NO_BALANCE。
     */
    private String calculationStatus;
}
