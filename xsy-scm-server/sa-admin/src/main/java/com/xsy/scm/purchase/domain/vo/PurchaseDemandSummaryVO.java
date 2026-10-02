package com.xsy.scm.purchase.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;

/**
 * 订单汇总 / 库存缺口预览行（只读聚合，按 {@code warehouseId + skuId + demandUnit} 归并）。
 *
 * <p>
 * <b>口径</b>：{@code orderDemandQuantity} 取来源销售订单行的<b>实发量</b>（{@code actual_quantity}）， 与需求生成使用同一数据范围和筛选条件。
 * {@code availableQuantity} 与 {@code stockComparisonGap} 均由<b>后端</b>用 {@code BigDecimal} 在 SQL
 * 里算好、以四位定点字符串下发，前端不参与浮点运算。
 *
 * <p>
 * <b>单位门禁</b>：{@code demandUnit}（订单销售单位）与 {@code inventoryUnit}（余额记账单位）不一致时 {@code calculationStatus = UNIT_MISMATCH} 且
 * {@code stockComparisonGap = null}，禁止换算/猜折算率。
 *
 * <p>
 * 本视图不计算在途采购量；缺口只反映订单需求与当前仓库可用量。
 */
@Data
public class PurchaseDemandSummaryVO {

    private Long skuId;

    private String skuCode;

    private String productName;

    /**
     * 规格名（{@code product_sku.spec_name}）。
     */
    private String skuName;

    private String categoryName;

    /**
     * 需求单位（订单销售单位快照，来自 {@code sales_order_item.sale_unit_snapshot}）。
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
     * 它<b>包含</b>本批订单自身已占用的预留，因此不能用来判断本批是否缺料，见 {@link #stockAvailableForSelectedOrders}。
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
     * 这是<b>已确认订单与当前库存/预留的对比结果，不是最终净采购建议</b>； 本视图不扣减未收采购单数量或已履约量。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal stockComparisonGap;

    /** Open purchase coverage split into unallocated in-transit quantity and allocated coverage. */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal inTransitQuantity;

    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal purchaseCoverageQuantity;

    /** Net purchase gap after stock, in-transit quantity and existing purchase coverage. */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal netPurchaseGap;

    /**
     * STOCK_ENOUGH / SHORTAGE / ZERO_STOCK / UNIT_MISMATCH / NO_BALANCE。
     */
    private String calculationStatus;
}
