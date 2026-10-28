package com.xsy.scm.order.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import com.xsy.scm.common.util.ScmDecimalStrings;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderReturnIndependentItemForm {
    @NotNull(message = "商品规格不能为空")
    private Long skuId;

    /**
     * 退货销售单位：缺省取商品销售单位；显式提交时必须与商品销售单位一致，不做跨单位换算。
     */
    @Size(max = 32, message = "销售单位不能超过32个字符")
    private String saleUnit;

    @NotBlank(message = "申请退货数量不能为空")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String requestedQuantity;

    /**
     * 人工填价：留空时服务端按「最近成交价 → 取价链」取价；填写时覆盖取价结果。
     */
    @Pattern(regexp = "^$|" + ScmDecimalStrings.PATTERN, message = "单价格式无效")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String unitPrice;

    @Size(max = 500, message = "价格原因不能超过500个字符")
    private String priceReason;
}
