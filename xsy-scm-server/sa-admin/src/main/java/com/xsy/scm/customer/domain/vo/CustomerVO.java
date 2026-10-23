package com.xsy.scm.customer.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 客户列表行。
 *
 * <p>
 * 列表不返回明细字段：地址、备注、账期细节只在 {@link CustomerDetailVO} 出现，避免列表接口随客户量变重。
 */
@Data
public class CustomerVO {
    private String visibilityPolicy;

    private Long customerId;

    private Integer version;

    private String customerCode;

    private String name;

    private Long customerTypeId;

    private String customerTypeName;

    private String status;

    private String settleMode;

    private Boolean codAllowed;

    private Long mallDeliveryWarehouseId;

    /** 授信额度，4 位定点字符串；{@code null} 保持 {@code null}。 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class, nullsUsing = ScmFixedScale4Serializer.class)
    private BigDecimal creditLimit;

    private String contactName;

    private String contactPhone;

    private Long sellerId;

    private String sellerName;

    private Long parentCustomerId;

    private String parentCustomerName;

    private OffsetDateTime updatedAt;
}
