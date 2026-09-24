package net.lab1024.sa.admin.module.scm.sorting.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 建单时从订单域读到的行快照与校验所需字段。
 *
 * <p>刻意带上 {@code orderStatus} / {@code orderDeleted} / {@code itemDeleted}：
 * 建单要求「已确认订单的有效明细」，把状态读出来才能给出可分辨的拒绝理由，
 * 而不是让一条已经被删掉的行以「数量为 null」的形式冒出来。
 */
@Data
public class SortingOrderLineSnapshot {
    private Long salesOrderItemId;
    private Long salesOrderId;
    private String orderStatus;
    private Boolean orderDeleted;
    private Boolean itemDeleted;
    private String orderNo;
    private Long customerId;
    private String customerName;
    private Long spuId;
    private Long skuId;
    private String spuCodeSnapshot;
    private String productNameSnapshot;
    private String skuCodeSnapshot;
    private String specNameSnapshot;
    private String saleUnitSnapshot;
    private String productTypeSnapshot;
    private BigDecimal actualQuantity;
}
