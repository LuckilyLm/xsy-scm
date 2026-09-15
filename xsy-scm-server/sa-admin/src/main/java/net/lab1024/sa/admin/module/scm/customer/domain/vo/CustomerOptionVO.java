package net.lab1024.sa.admin.module.scm.customer.domain.vo;

import lombok.Data;

/**
 * 客户下拉选项。只返回活动客户，不分页。
 *
 * <p>带上 {@code customerTypeId} / {@code customerTypeCode} 是为了让「上级集团客户」这类
 * 需要按类型收窄的选择器可以在**一次请求**里完成过滤，而不必先查字典表再反查客户。
 */
@Data
public class CustomerOptionVO {

    private Long customerId;

    private String customerCode;

    private String name;

    private String status;

    private Long customerTypeId;

    /** 客户类型编码（例如 {@code GROUP}），供前端按类型过滤选项。 */
    private String customerTypeCode;
}
