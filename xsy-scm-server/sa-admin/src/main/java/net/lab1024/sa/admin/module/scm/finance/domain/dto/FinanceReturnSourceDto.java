package net.lab1024.sa.admin.module.scm.finance.domain.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 红字应收所需的**退货批准事实**（单头维度），由 {@code FinanceReceivableSourceDao} 只读取得。
 *
 * <p>{@code approvedAt} / {@code approvedBy} 取 {@code order_return} 上已落库的列：
 * 红字是「已经成立的 {@code OrderReturn APPROVED} 在财务域中的事实映射」（第三批 D-2 原则），
 * 时点与操作人属于那个业务事实，不能由财务侧现取 {@code now()} 或现取请求上下文。
 */
@Data
public class FinanceReturnSourceDto {

    private Long orderReturnId;

    /**
     * 退货单号，只进操作日志快照供追溯，不落财务列。
     */
    private String returnNo;

    private Long orderId;

    /**
     * 红字的事件时点 = {@code order_return.approved_at}（第二批 Q27 / 设计稿 §3.1）。
     */
    private OffsetDateTime approvedAt;

    /**
     * 批准人。{@code approve} 在把状态写成 {@code APPROVED} 的同一条 UPDATE 里落
     * {@code updated_by}，且 {@code APPROVED} 之后没有任何命令再改这一行
     * （{@code reject / cancel} 要求 {@code PENDING}），所以这一列就是批准人本身。
     */
    private String approvedBy;

    /**
     * 红字原因 = {@code order_return.reason}（建单时填写的退货原因，已成立的业务事实）。
     */
    private String reason;
}
