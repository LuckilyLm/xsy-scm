package com.xsy.scm.trace.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 包装码作废。
 *
 * <p>
 * 只对未绑定的码生效：已绑到分拣任务行的码代表一件承诺给客户的实物，作废它会让回放自相矛盾， 现场应先走退货或更正流程。
 */
@Data
public class TraceUnitVoidForm {

    @NotBlank(message = "包装码不能为空")
    @Size(max = 32, message = "包装码不能超过32个字符")
    private String unitCode;

    @NotBlank(message = "作废原因不能为空")
    @Size(max = 200, message = "作废原因不能超过200个字符")
    private String reason;
}
