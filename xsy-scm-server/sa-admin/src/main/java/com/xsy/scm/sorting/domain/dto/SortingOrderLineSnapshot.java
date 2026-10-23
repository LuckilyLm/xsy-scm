package com.xsy.scm.sorting.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 建分拣任务前校验订单已确认、未删除且明细有效。读取订单状态和删除标记用于准确返回拒绝原因。
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
