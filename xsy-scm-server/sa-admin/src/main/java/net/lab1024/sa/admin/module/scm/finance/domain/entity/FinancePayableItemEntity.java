package net.lab1024.sa.admin.module.scm.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 应付明细：一条 {@code purchase_receipt_item} 一行；手工红字明细来源为 {@code MANUAL} 且无来源行。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("finance_payable_item")
public class FinancePayableItemEntity extends FinanceRecord {

    private Long payableId;

    /**
     * {@code ScmFinancePayableItemSourceTypeEnum}；与 {@link #sourceId} 的空/非空由
     * {@code ck_finance_payable_item_source_pairing} 配对约束。
     */
    private String sourceType;

    /**
     * 正常 = {@code purchase_receipt_item.id}；{@code MANUAL} 时**必须为 {@code null}**。
     */
    private Long sourceId;

    /**
     * 采购订单行 id，回溯结算单价来源（Q10）。
     */
    private Long purchaseOrderItemId;

    private Long skuId;

    private String skuNameSnapshot;

    private String unitSnapshot;

    /**
     * = 收货有效量（标品取申报量、非标取 {@code actual_weight}，见
     * {@code PurchaseReceiptQuantityCalculator}），含合法容差内超收（Q11）。恒 &gt; 0。
     */
    private BigDecimal quantity;

    /**
     * = {@code purchase_order_item.purchase_price}，语义为「当前采购业务确认使用的结算单价」，
     * <b>不含</b>含税 / 未税 / 税额 / 可抵扣税额中的任何含义（Q14）。
     */
    private BigDecimal unitPrice;

    /**
     * {@code ROUND(quantity × unitPrice, 4)}。
     */
    private BigDecimal amount;
}
