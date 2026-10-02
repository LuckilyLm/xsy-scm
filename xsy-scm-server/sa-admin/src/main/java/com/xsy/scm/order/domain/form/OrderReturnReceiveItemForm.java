package com.xsy.scm.order.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import com.xsy.scm.common.util.ScmDecimalStrings;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class OrderReturnReceiveItemForm {
    @NotNull(message = "退货行不能为空")
    private Long returnItemId;
    @NotNull(message = "处置方式不能为空")
    @Pattern(regexp = "RETURN_TO_STOCK|DAMAGE", message = "处置方式无效")
    private String disposition;
    @NotNull(message = "接收数量不能为空")
    @Pattern(regexp = ScmDecimalStrings.PATTERN, message = "接收数量格式无效")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String quantity;
}
