package net.lab1024.sa.admin.module.scm.customer.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.common.json.ScmStrictDecimalStringDeserializer;
import net.lab1024.sa.admin.module.scm.common.util.ScmDecimalStrings;

/**
 * 新增客户。
 *
 * <p><b>刻意不含 {@code status}</b>：状态变更走独立的 {@code /scm/customer/updateStatus} 端点
 * （legacy 不变量 C7）。新增客户的初始状态由 Service 固定为 {@code POTENTIAL}（Q13）。
 */
@Data
public class CustomerAddForm {
    @Pattern(regexp="ALL_ENABLED|ALLOWLIST") private String visibilityPolicy;
    @jakarta.validation.Valid @Size(max=500) private java.util.List<CustomerSkuVisibilityItemForm> visibilities;


    @NotBlank
    @Size(max = 64)
    private String customerCode;

    @NotBlank
    @Size(max = 150)
    private String name;

    @NotNull
    @Positive
    private Long customerTypeId;

    @Positive
    private Long parentCustomerId;

    @Positive
    private Long sellerId;

    @Positive
    private Long supplierId;

    @Size(max = 100)
    private String contactName;

    @Size(max = 32)
    private String contactPhone;

    @Size(max = 255)
    private String address;

    @Positive
    private Integer provinceCode;

    @Size(max = 32)
    private String provinceName;

    @Positive
    private Integer cityCode;

    @Size(max = 64)
    private String cityName;

    @Positive
    private Integer districtCode;

    @Size(max = 64)
    private String districtName;

    @NotBlank
    @Pattern(regexp = "INDEPENDENT|GROUP")
    private String settleMode = "INDEPENDENT";

    /** 授信额度，4 位定点字符串；缺省视作 0。 */
    @Pattern(regexp = ScmDecimalStrings.PATTERN)
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String creditLimit;

    @Pattern(regexp = "BY_AMOUNT|BY_TIME")
    private String creditPeriodType;

    /** 仅 {@code BY_AMOUNT} 时有效。 */
    @Pattern(regexp = ScmDecimalStrings.PATTERN)
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String creditAmountThreshold;

    /** 仅 {@code BY_TIME} 时有效，必须为正。 */
    @Min(1)
    private Integer creditPeriodValue;

    /** 仅 {@code BY_TIME} 时有效。 */
    @Pattern(regexp = "DAY|MONTH")
    private String creditPeriodUnit;

    /** 仅 {@code BY_TIME} + {@code MONTH} 时有效；上限 28 保证每个自然月都存在该日。 */
    @Min(1)
    @Max(28)
    private Integer settleDay;

    @Size(max = 500)
    private String remark;
}
