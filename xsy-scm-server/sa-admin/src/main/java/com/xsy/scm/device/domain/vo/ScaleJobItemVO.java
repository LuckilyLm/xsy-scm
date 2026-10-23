package com.xsy.scm.device.domain.vo;

import lombok.Data;

/**
 * 作业明细投影：只给称重必需字段，不同步客户地址、金额等与计量无关的数据。
 */
@Data
public class ScaleJobItemVO {

    private String taskItemId;

    private String productName;

    private String specName;

    /**
     * 销售单位；秤端据此判断设备单位能否直接使用，未知单位不得自动换算。
     */
    private String saleUnit;

    private String plannedQuantity;

    private String sortedQuantity;

    /**
     * 标品或非标品；非标品才需要人工裁定结果。
     */
    private String productType;

    private Boolean active;

    /**
     * 明细版本；采集读数时冻结，用于发现任务重开导致的上下文过时。
     */
    private Long taskItemVersion;

    private String customerName;

    private String orderNo;

    private String skuId;

    private String customerId;
}
