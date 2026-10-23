package com.xsy.scm.customer.domain.vo;

import lombok.Data;

/**
 * 客户下拉选项，只返回活动客户，不分页。
 *
 * <p>
 * 带上 {@code customerTypeId} / {@code customerTypeCode}，让「上级集团客户」这类按类型收窄的选择器能一次请求完成过滤。
 */
@Data
public class CustomerOptionVO {

    private Long customerId;

    private String customerCode;

    private String name;

    private String status;

    private Long customerTypeId;

    /** 客户类型编码（例如 {@code GROUP}），供前端按类型过滤。 */
    private String customerTypeCode;
}
