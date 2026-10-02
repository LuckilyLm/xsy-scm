package com.xsy.scm.promotion.domain.form;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.promotion.constant.ScmPromotionCouponDiscountTypeEnum;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class PromotionCouponForm {

    private Long id;

    @NotBlank(message = "券编码不能为空")
    @Size(max = 64, message = "券编码不能超过64个字符")
    @Pattern(regexp = "[A-Za-z0-9_\\-]+", message = "券编码只能包含字母、数字、下划线与连字符")
    private String couponCode;

    @NotBlank(message = "券名称不能为空")
    @Size(max = 150, message = "券名称不能超过150个字符")
    private String couponName;

    @NotBlank(message = "优惠类型不能为空")
    @ScmEnumValue(enumClass = ScmPromotionCouponDiscountTypeEnum.class, message = "优惠类型无效")
    private String discountType;

    @NotNull(message = "优惠值不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "优惠值必须大于0")
    @Digits(integer = 14, fraction = 4, message = "优惠值最多14位整数和4位小数")
    private BigDecimal discountValue;

    @DecimalMin(value = "0", message = "门槛金额不能为负")
    @Digits(integer = 14, fraction = 4, message = "门槛金额最多14位整数和4位小数")
    private BigDecimal minOrderAmount;

    @NotNull(message = "生效时间不能为空")
    private OffsetDateTime validFrom;

    @NotNull(message = "失效时间不能为空")
    private OffsetDateTime validTo;

    @Size(max = 200, message = "备注不能超过200个字符")
    private String remark;

    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;
}
