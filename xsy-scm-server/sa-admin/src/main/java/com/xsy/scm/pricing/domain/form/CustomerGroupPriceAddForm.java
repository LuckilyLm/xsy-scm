package com.xsy.scm.pricing.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import com.xsy.scm.common.util.ScmDecimalStrings;

import java.time.OffsetDateTime;

/**
 * 分组价录入：选择任一规格确定「商品 + 销售单位」，分组价格本身落在商品级。
 */
@Data
public class CustomerGroupPriceAddForm {
    @NotNull(message = "客户分组不能为空")
    private Long groupId;
    @NotNull(message = "商品规格不能为空")
    private Long skuId;
    @NotNull(message = "分组价不能为空")
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "分组价格式不正确")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String unitPrice;
    @NotNull(message = "生效开始时间不能为空")
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime effectiveTo;
}
