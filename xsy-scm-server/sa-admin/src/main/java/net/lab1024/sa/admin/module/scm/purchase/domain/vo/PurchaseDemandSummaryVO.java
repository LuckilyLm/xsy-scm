package net.lab1024.sa.admin.module.scm.purchase.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;

/**
 * 订单汇总 / 库存缺口预览行（Wave 2A §6A.4，只读聚合，按 {@code warehouseId + skuId + demandUnit} 归并）。
 *
 * <p><b>口径</b>：{@code orderDemandQuantity} 取来源销售订单行的<b>实发量</b>（{@code actual_quantity}），
 * 与 {@code PurchaseDemandService.generate()} 的取数完全一致（§6A.1 —— 本预览不改写需求语义，只镜像其 WHERE）。
 * {@code availableQuantity} 与 {@code stockComparisonGap} 均由<b>后端</b>用 {@code BigDecimal}
 * 在 SQL 里算好、以四位定点字符串下发，前端不参与浮点运算（§6A.4）。
 *
 * <p><b>Q13 单位门禁</b>：{@code demandUnit}（订单销售单位）与 {@code inventoryUnit}（余额记账单位）不一致时
 * {@code calculationStatus = UNIT_MISMATCH} 且 {@code stockComparisonGap = null}，禁止换算/猜折算率。
 *
 * <p><b>{@code openPurchaseQuantity}（在途采购量）刻意缺席</b>：§6A.6 明确「在途是否抵扣采购缺口」尚未裁决，
 * 第一阶段不臆造哪些采购状态算在途，因此不返回、更不从缺口公式里扣。
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
     * 余额记账单位（Q13）；{@code NO_BALANCE} 时为 null。
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
     * <p>它<b>包含</b>本批订单自身已占用的预留，因此不能用来判断本批是否缺料，见
     * {@link #stockAvailableForSelectedOrders}。
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
     * 库存对比差额 = {@code max(orderDemandQuantity - stockAvailableForSelectedOrders, 0)}；
     * {@code UNIT_MISMATCH} 时为 null（不返回伪造差额，§6A.5）。
     *
     * <p>这是<b>已确认订单与当前库存/预留的对比结果，不是最终净采购建议</b>：在途采购是否抵扣、
     * 已履约量如何扣减等仍未裁决（§6A.6），因此刻意不叫「建议采购量」。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal stockComparisonGap;

    /**
     * STOCK_ENOUGH / SHORTAGE / ZERO_STOCK / UNIT_MISMATCH / NO_BALANCE。
     */
    private String calculationStatus;
}
