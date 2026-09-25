package net.lab1024.sa.admin.module.scm.finance.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 红字应收所需的**退货批准事实**（明细维度）：一条 {@code order_return_item} 一行红字明细。
 *
 * <p>{@code amount} 直接取订单域已落库的 {@code approved_amount}
 * （{@code OrderReturnService.approve} 按 {@code round(approved_quantity × locked_unit_price, 4)} 算好），
 * 财务**不重算** —— 重算就是给同一个金额造第二个权威来源（设计稿 §8.2）。
 */
@Data
public class FinanceReturnSourceLineDto {

    private Long orderReturnItemId;

    private Long orderItemId;

    /**
     * {@code order_return_item} 不存商品快照，SKU 三件套从其来源订单行取。
     */
    private Long skuId;

    private String skuNameSnapshot;

    private String unitSnapshot;

    private BigDecimal approvedQuantity;

    private BigDecimal lockedUnitPrice;

    /**
     * 订单域已算好的批准金额，财务原样采用。
     */
    private BigDecimal approvedAmount;
}
