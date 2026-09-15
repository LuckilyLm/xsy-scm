package net.lab1024.sa.admin.module.scm.customer.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 客户列表行。
 *
 * <p>列表<b>不</b>返回明细字段（C16）：地址 / 备注 / 账期细节只在 {@link CustomerDetailVO} 出现，
 * 避免列表接口在客户量增长后变重。
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
