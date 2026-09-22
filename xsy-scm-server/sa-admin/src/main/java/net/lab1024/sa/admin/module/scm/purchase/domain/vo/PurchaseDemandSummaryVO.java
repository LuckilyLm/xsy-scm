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
 * {@code availableQuantity} 与 {@code shortageAgainstAvailable} 均由<b>后端</b>用 {@code BigDecimal}
 * 在 SQL 里算好、以四位定点字符串下发，前端不参与浮点运算（§6A.4）。
 *
 * <p><b>Q13 单位门禁</b>：{@code demandUnit}（订单销售单位）与 {@code inventoryUnit}（余额记账单位）不一致时
 * {@code calculationStatus = UNIT_MISMATCH} 且 {@code shortageAgainstAvailable = null}，禁止换算/猜折算率。
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
     * 可用量 = 现有量 − 已预留量；SQL 内算好的派生列，{@code NO_BALANCE} 时按 0 展示。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal availableQuantity;

    /**
     * 缺口 = {@code max(orderDemandQuantity - availableQuantity, 0)}；
     * {@code UNIT_MISMATCH} 时为 null（不返回伪造缺口，§6A.5）。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal shortageAgainstAvailable;

    /**
     * STOCK_ENOUGH / SHORTAGE / ZERO_STOCK / UNIT_MISMATCH / NO_BALANCE。
     */
    private String calculationStatus;
}
