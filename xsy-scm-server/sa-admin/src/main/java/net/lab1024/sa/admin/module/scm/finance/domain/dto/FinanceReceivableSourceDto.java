package net.lab1024.sa.admin.module.scm.finance.domain.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 应收生成所需的**签收事实**（单头维度），由 {@code FinanceReceivableSourceDao} 只读取得。
 *
 * <p>{@code signedAt} / {@code signedBy} 一律取 {@code delivery_route_order} 上已落库的列：
 * {@code markSigned} 写的是数据库时钟 {@code now()}，服务层再取一次 {@code OffsetDateTime.now()}
 * 会得到一个**比签收时刻更晚**的值，那正是本类要避免的第二个时点事实。
 */
@Data
public class FinanceReceivableSourceDto {

    /**
     * 被签收的配送分配行 id（{@code delivery_route_order.id}）：应收的来源锚点是订单，
     * 但时点与操作人只存在于这一行上。
     */
    private Long deliveryRouteOrderId;

    private Long salesOrderId;

    private Long customerId;

    /**
     * 订单上的客户名称快照，不回读 {@code customer} 主档。
     */
    private String customerNameSnapshot;

    /**
     * 应收的事件时点（第一批 Q1）：{@code delivery_route_order.signed_at}。
     */
    private OffsetDateTime signedAt;

    /**
     * 签收动作的真实操作人（{@code delivery_route_order.signed_by}，loginId 形态）。
     */
    private String signedBy;
}
