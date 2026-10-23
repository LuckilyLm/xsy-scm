package com.xsy.scm.finance.domain.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 生成应收所用的签收时间、签收人必须来自已保存的配送签收记录，不能在财务侧重新取当前时间。
 */
@Data
public class FinanceReceivableSourceDto {

    /**
     * 被签收的配送分配行 id（{@code xsy_delivery_route_order.id}）：应收的来源是订单，但时点与操作人只存在于这一行上。
     */
    private Long deliveryRouteOrderId;

    private Long salesOrderId;

    private Long customerId;

    /**
     * 订单上的客户名称快照，不回读 {@code xsy_customer} 主档。
     */
    private String customerNameSnapshot;

    private Long settlementCustomerId;

    private String settlementCustomerNameSnapshot;

    /**
     * 应收的事件时点：{@code xsy_delivery_route_order.signed_at}。
     */
    private OffsetDateTime signedAt;

    /**
     * 签收动作的真实操作人（{@code xsy_delivery_route_order.signed_by}，loginId 形态）。
     */
    private String signedBy;
}
