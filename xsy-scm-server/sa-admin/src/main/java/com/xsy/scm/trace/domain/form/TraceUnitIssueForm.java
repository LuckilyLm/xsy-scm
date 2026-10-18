package com.xsy.scm.trace.domain.form;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 按批次发码。
 *
 * <p>
 * 一次给同一个批次发 N 个包装码，每个包装装同样的数量。发码总量不得超过该批次当前余额： 包装码描述的是「已经存在的实物」，不能在库存之外凭空造出实物身份。
 */
@Data
public class TraceUnitIssueForm {

    @NotNull(message = "批次不能为空")
    private Long lotId;

    @NotBlank(message = "包装类型不能为空")
    @Pattern(regexp = "BAG|BOX|CRATE", message = "包装类型只允许袋、箱、筐")
    private String packageType;

    @NotNull(message = "发码数量不能为空")
    @Min(value = 1, message = "至少发 1 个包装码")
    @Max(value = 500, message = "一次最多发 500 个包装码")
    private Integer packageCount;

    @NotBlank(message = "每包数量不能为空")
    @JsonDeserialize(using = ScmStrictDecimalStringDeserializer.class)
    private String quantityPerPackage;

    @Size(max = 200, message = "备注不能超过200个字符")
    private String remark;
}
