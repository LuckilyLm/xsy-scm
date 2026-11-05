package com.xsy.scm.trace.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批更正申请：批准即撤销绑定，驳回只留审批意见。
 */
@Data
public class TraceCorrectionDecisionForm {

    @NotNull(message = "更正申请不能为空")
    private Long id;

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能小于0")
    private Integer version;

    @Size(max = 200, message = "审批意见不能超过200个字符")
    private String reason;
}
