package com.xsy.scm.trace.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 包装码净重补录。
 *
 * <p>
 * 只对还没有净重的码生效：已有重量的码要走称重更正，不能在这里覆盖，否则「这一筐到底多重」就没有可追溯的原始结论。
 */
@Data
public class TraceUnitWeightForm {

    @NotBlank(message = "净重不能为空")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String netWeight;

    @NotBlank(message = "重量单位不能为空")
    @Size(max = 32, message = "重量单位不能超过 32 个字符")
    private String weightUnit;

    @NotBlank(message = "补录原因不能为空")
    @Size(max = 200, message = "补录原因不能超过 200 个字符")
    private String reason;
}
