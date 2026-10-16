package com.xsy.scm.sorting.domain.vo;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/** 按客户与订单逐行查看正式分拣事实；数量仍按原销售单位展示，不跨行合计。 */
@Getter
@Setter
public class SortingCustomerLineVO extends SortingTaskItemVO {
    private String taskNo;
    private String taskStatus;
    private String warehouseNameSnapshot;
    private OffsetDateTime deliveryTimeSnapshot;
}
