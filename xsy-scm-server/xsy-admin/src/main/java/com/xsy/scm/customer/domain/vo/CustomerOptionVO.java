package com.xsy.scm.customer.domain.vo;

import lombok.Data;

/**
 * 客户下拉选项，只返回活动客户，不分页。
 */
@Data
public class CustomerOptionVO {

    private Long customerId;

    private String customerCode;

    private String name;

    private String status;
}
