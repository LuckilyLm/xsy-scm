package com.xsy.scm.finance.domain.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 财务只读到的余额消费流水事实。
 *
 * <p>
 * <b>为什么是财务自己的 DTO 而不是余额域的实体</b>：财务域的只读契约不能把别的域的模型当返回值 —— 那等于让余额域的表结构变化直接成为财务域的编译期依赖（见 {@code ScmArchitectureTest}）。
 * 这里只取财务判定与核销真正用到的那几列。
 */
@Data
public class FinanceBalanceMovementDto {

    /**
     * 余额流水主键（核销的来源 id）。
     */
    private Long id;

    /**
     * 流水号，落核销快照用。
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
