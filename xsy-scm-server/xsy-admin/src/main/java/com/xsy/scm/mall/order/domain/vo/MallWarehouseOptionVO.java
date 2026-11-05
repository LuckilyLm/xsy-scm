package com.xsy.scm.mall.order.domain.vo;

import lombok.Data;

@Data
public class MallWarehouseOptionVO {
    private Long warehouseId;
    private String warehouseName;
    private String source;
    private Integer priority;
}
