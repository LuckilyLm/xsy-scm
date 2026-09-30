package com.xsy.scm.finance.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import com.xsy.scm.common.util.ScmDecimalStrings;

/** 手工红字应付明细；商品与单位快照从原应付明细读取。 */
@Data
public class FinancePayableRedItemForm {

    @NotNull(message = "采购订单行不能为空")
    @Positive(message = "采购订单行编号必须大于0")
    private Long purchaseOrderItemId;

    @NotNull(message = "红字数量不能为空")
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "红字数量格式无效")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String quantity;

    @NotNull(message = "红字单价不能为空")
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "红字单价格式无效")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String unitPrice;

    @NotNull(message = "红字金额不能为空")
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "红字金额格式无效")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String amount;
}
