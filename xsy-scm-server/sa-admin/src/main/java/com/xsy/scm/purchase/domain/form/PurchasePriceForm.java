package com.xsy.scm.purchase.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;

/**
 * 采购价补录 / 调整。
 *
 * <p>
 * 价格必须是严格四位定点字符串：{@code 12.5} 这类写法会被拒绝，避免同一列混进不同精度。 补价的原因可选，改价的原因必填（由服务层校验）。
 */
@Data
public class PurchasePriceForm {

    @NotBlank(message = "采购单价不能为空")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String price;

    @Size(max = 500, message = "原因不能超过500个字符")
    private String reason;
}
