package com.xsy.scm.finance.domain.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 红字应收取已批准退货单的时间和批准人，不能用登记时的当前时间或操作人替代。
 */
@Data
public class FinanceReturnSourceDto {

    private Long orderReturnId;

    /**
     * 退货单号，只进操作日志供追溯，不写财务列。
     */
    private String returnNo;

    private Long orderId;

    /**
     * 红字的事件时点 = {@code xsy_order_return.approved_at}。
     */
    private OffsetDateTime approvedAt;

    /**
     * 批准人。{@code approve} 在把状态写成 {@code APPROVED} 的同一条 UPDATE 里写 {@code updated_by}，且 {@code APPROVED} 之后没有任何命令再改这一行
     * （{@code reject / cancel} 要求 {@code PENDING}），所以这一列就是批准人本身。
     */
    private String approvedBy;

    /**
     * 红字原因 = {@code xsy_order_return.reason}（建单时填写的退货原因）。
     */
    private String reason;
}
