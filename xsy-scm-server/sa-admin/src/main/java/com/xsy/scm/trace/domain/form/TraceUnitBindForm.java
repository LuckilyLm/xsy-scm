package com.xsy.scm.trace.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分拣扫码绑定：把一个包装码绑到分拣任务行。
 *
 * <p>
 * 扫码发生在装箱现场，绑的是「这一件实物要发给谁」；出库单要到发车才生成，所以不绑出库行。
 */
@Data
public class TraceUnitBindForm {

    @NotBlank(message = "包装码不能为空")
    @Size(max = 32, message = "包装码不能超过32个字符")
    private String unitCode;

    @NotNull(message = "分拣任务行不能为空")
    private Long sortingTaskItemId;
}
