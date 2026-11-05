package com.xsy.scm.finance.domain.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 财务读取的余额消费流水。
 *
 * <p>
 * 财务用自己的 DTO 而不是余额域的实体：直接返回别的域的模型，会让余额域的表结构变化成为财务域的编译期依赖（见 {@code ScmArchitectureTest}）。 这里只取财务判定与核销真正用到的列。
 */
@Data
public class FinanceBalanceMovementDto {

    /**
     * 余额流水主键（核销的来源 id）。
     */
    private Long id;

    /**
     * 流水号，核销时保存。
     */
    private String movementNo;

    /**
     * 发生流水的客户。
     */
    private Long customerId;

    /**
     * 该笔流水所属的结算主体。
     */
    private Long settlementCustomerId;

    /**
     * 流水类型（只认消费）。
     */
    private String type;

    /**
     * 流水方向（只认支出）。
     */
    private String direction;

    /**
     * 流水金额，即该腿的有效可核销额。
     */
    private BigDecimal amount;

    /**
     * 流水对应的业务来源 id（余额消费时是支付意图 id，用来与订单资金取数对齐）。
     */
    private Long sourceId;
}
