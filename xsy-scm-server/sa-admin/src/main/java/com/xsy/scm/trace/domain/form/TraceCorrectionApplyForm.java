package com.xsy.scm.trace.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 申请更正：撤销一条已经生效的绑定。
 *
 * <p>
 * 申请与审批必须不是同一个人；批准后包装码回到可绑定状态，可以重新绑到正确的分拣行。
 */
@Data
public class TraceCorrectionApplyForm {

    @NotBlank(message = "包装码不能为空")
    @Size(max = 32, message = "包装码不能超过32个字符")
    private String unitCode;

    @NotBlank(message = "更正原因不能为空")
    @Size(max = 200, message = "更正原因不能超过200个字符")
    private String reason;
}
