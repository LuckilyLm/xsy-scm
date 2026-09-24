package net.lab1024.sa.admin.module.scm.report.domain.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

/**
 * 经营概览的指标卡与每日统计行。
 *
 * <p>命名即口径：本类<b>不存在</b>「营业收入」「已收款」「应收」这类字段。R0 没有签收、
 * 应收与核销事实，把已确认订单金额叫成收入会把「承诺」说成「已实现」，因此只允许
 * {@code confirmedOrderAmount} / 前端「已确认订单金额」这套命名。
 */
@Data
public class ReportOverviewVO {

    /** {@code status=CONFIRMED} 且 {@code confirmed_at} 落在区间内的订单数。 */
    private Long confirmedOrderCount;

    /** 同一确认范围内的去重下单客户数。 */
    private Long customerCount;

    /** {@code SUM(settlement_total_amount)}：结算口径，不是下单口径。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal confirmedOrderAmount;

    /** {@code order_refund} 中 {@code COMPLETED} 的退款额；独立展示，不冲减订单金额。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal completedRefundAmount;

    private Long submittedPurchaseOrderCount;

    /** 已提交（含部分收货 / 已收货 / 短关）采购单的 {@code SUM(total_amount)}。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal submittedPurchaseAmount;

    /** {@code PURCHASE_IN} 流水的 {@code SUM(quantity * unit_cost)}。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal purchaseInCostAmount;

    /**
     * 上述成本金额里被跳过的无成本流水行数。
     *
     * <p>&gt; 0 表示 {@link #purchaseInCostAmount} 是<b>不完整</b>的和，前端必须显性提示，
     * 不能让用户把它当成全部入库成本。
     */
    private Integer purchaseInCostMissingCount;

    /**
     * 当前库存账面金额：{@code SUM(inventory_balance.quantity * avg_cost)}。
     *
     * <p>不受查询历史区间影响，配 {@link #snapshotAt} 一起读才是完整事实。
     */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal inventoryBookValue;

    /** 当前有账面库存的余额行数。 */
    private Long stockedSkuCount;

    /** {@link #inventoryBookValue} 的取值时点。 */
    private OffsetDateTime snapshotAt;
}
