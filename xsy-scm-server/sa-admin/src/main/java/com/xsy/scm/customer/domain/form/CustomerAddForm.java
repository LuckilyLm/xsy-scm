package com.xsy.scm.customer.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.xsy.scm.common.constant.ScmCreditPeriodTypeEnum;
import com.xsy.scm.common.constant.ScmCreditPeriodUnitEnum;
import com.xsy.scm.common.constant.ScmSettleModeEnum;
import com.xsy.scm.common.domain.ScmPartyForm;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import com.xsy.scm.common.util.ScmDecimalStrings;
import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.customer.constant.CustomerVisibilityPolicy;

import java.util.List;

/**
 * 新客户默认是潜在客户，状态通过专门接口变更。客户编码由服务端生成，不接受客户端自行填写。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerAddForm extends ScmPartyForm {
    @Pattern(regexp = CustomerVisibilityPolicy.PATTERN, message = "商品可见性策略无效")
    private String visibilityPolicy;
    @Valid
    @Size(max = 500, message = "商品可见性清单不能超过500项")
    private List<CustomerSkuVisibilityItemForm> visibilities;

    @NotBlank(message = "客户名称不能为空")
    @Size(max = 150, message = "客户名称不能超过150个字符")
    private String name;

    @Positive(message = "客户分类必须大于0")
    private Long businessCategoryId;

    @Positive(message = "客户分组 ID 必须大于0")
    private Long customerGroupId;

    @Positive(message = "上级客户 ID 必须大于0")
    private Long parentCustomerId;

    @Positive(message = "结算客户 ID 必须大于0")
    private Long settlementCustomerId;

    @Positive(message = "业务员 ID 必须大于0")
    private Long sellerId;

    @Positive(message = "供应商 ID 必须大于0")
    private Long supplierId;

    @NotBlank(message = "结算模式不能为空")
    @ScmEnumValue(enumClass = ScmSettleModeEnum.class, message = "结算模式无效")
    private String settleMode = ScmSettleModeEnum.INDEPENDENT.name();

    /** 仅结算模式为 GROUP 的集团主体可用：允许子客户用集团余额结算。 */
    private Boolean sharedSettlementEnabled;

    /** 商城货到付款准入；未提供时新增默认关闭，编辑不改变已有设置。 */
    private Boolean codAllowed;

    /** 商城默认配送仓；仅同时持客户编辑与仓库编辑权时可设置。 */
    @Positive(message = "商城配送仓必须大于0")
    private Long mallDeliveryWarehouseId;

    private Boolean clearMallDeliveryWarehouse;

    /** 授信额度，4 位定点字符串；缺省视作 0。 */
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "授信额度格式不正确")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String creditLimit;

    @ScmEnumValue(enumClass = ScmCreditPeriodTypeEnum.class, message = "账期类型无效")
    private String creditPeriodType;

    /** 仅 {@code BY_AMOUNT} 时有效。 */
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "授信金额阈值格式不正确")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String creditAmountThreshold;

    /** 仅 {@code BY_TIME} 时有效，必须为正。 */
    @Min(value = 1, message = "账期值必须至少为1")
    private Integer creditPeriodValue;

    /** 仅 {@code BY_TIME} 时有效。 */
    @ScmEnumValue(enumClass = ScmCreditPeriodUnitEnum.class, message = "账期单位无效")
    private String creditPeriodUnit;

    /** 仅 {@code BY_TIME} + {@code MONTH} 时有效；上限 28 保证每个自然月都有这一天。 */
    @Min(value = 1, message = "月结日必须在1至28之间")
    @Max(value = 28, message = "月结日必须在1至28之间")
    private Integer settleDay;
}
