package com.xsy.scm.customer.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import com.xsy.scm.common.constant.ScmCreditPeriodTypeEnum;
import com.xsy.scm.common.constant.ScmCreditPeriodUnitEnum;
import com.xsy.scm.common.constant.ScmSettleModeEnum;
import com.xsy.scm.common.domain.ScmLocationForm;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import com.xsy.scm.common.util.ScmDecimalStrings;
import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.customer.constant.CustomerVisibilityPolicy;

import java.util.List;

/**
 * 新增客户。
 *
 * <p><b>刻意不含 {@code status}</b>：状态变更走独立的 {@code /scm/customer/updateStatus} 端点，
 * 新客户的初始状态由 Service 固定为 {@code POTENTIAL}。
 */
@Data
public class CustomerAddForm extends ScmLocationForm {
    @Pattern(regexp = CustomerVisibilityPolicy.PATTERN, message = "商品可见性策略无效")
    private String visibilityPolicy;
    @Valid
    @Size(max = 500, message = "商品可见性清单不能超过500项")
    private List<CustomerSkuVisibilityItemForm> visibilities;


    @NotBlank(message = "客户编码不能为空")
    @Size(max = 64, message = "客户编码不能超过64个字符")
    private String customerCode;

    @NotBlank(message = "客户名称不能为空")
    @Size(max = 150, message = "客户名称不能超过150个字符")
    private String name;

    @NotNull(message = "客户类型不能为空")
    @Positive(message = "客户类型必须大于0")
    private Long customerTypeId;

    @Positive(message = "上级客户 ID 必须大于0")
    private Long parentCustomerId;

    @Positive(message = "业务员 ID 必须大于0")
    private Long sellerId;

    @Positive(message = "供应商 ID 必须大于0")
    private Long supplierId;

    @Size(max = 100, message = "联系人姓名不能超过100个字符")
    private String contactName;

    @Size(max = 32, message = "联系电话不能超过32个字符")
    private String contactPhone;

    @Size(max = 255, message = "联系地址不能超过255个字符")
    private String address;

    @Positive(message = "省份编码必须大于0")
    private Integer provinceCode;

    @Size(max = 32, message = "省份名称不能超过32个字符")
    private String provinceName;

    @Positive(message = "城市编码必须大于0")
    private Integer cityCode;

    @Size(max = 64, message = "城市名称不能超过64个字符")
    private String cityName;

    @Positive(message = "区县编码必须大于0")
    private Integer districtCode;

    @Size(max = 64, message = "区县名称不能超过64个字符")
    private String districtName;

    @NotBlank(message = "结算模式不能为空")
    @ScmEnumValue(enumClass = ScmSettleModeEnum.class, message = "结算模式无效")
    private String settleMode = ScmSettleModeEnum.INDEPENDENT.name();

    /**
     * 授信额度，4 位定点字符串；缺省视作 0。
     */
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "授信额度格式不正确")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String creditLimit;

    @ScmEnumValue(enumClass = ScmCreditPeriodTypeEnum.class, message = "账期类型无效")
    private String creditPeriodType;

    /**
     * 仅 {@code BY_AMOUNT} 时有效。
     */
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "授信金额阈值格式不正确")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String creditAmountThreshold;

    /**
     * 仅 {@code BY_TIME} 时有效，必须为正。
     */
    @Min(value = 1, message = "账期值必须至少为1")
    private Integer creditPeriodValue;

    /**
     * 仅 {@code BY_TIME} 时有效。
     */
    @ScmEnumValue(enumClass = ScmCreditPeriodUnitEnum.class, message = "账期单位无效")
    private String creditPeriodUnit;

    /**
     * 仅 {@code BY_TIME} + {@code MONTH} 时有效；上限 28 保证每个自然月都存在该日。
     */
    @Min(value = 1, message = "月结日必须在1至28之间")
    @Max(value = 28, message = "月结日必须在1至28之间")
    private Integer settleDay;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
