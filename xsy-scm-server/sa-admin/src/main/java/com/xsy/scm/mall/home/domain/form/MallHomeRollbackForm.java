package com.xsy.scm.mall.home.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MallHomeRollbackForm {
    @NotNull(message = "回滚目标版本不能为空")
    private Long targetReleaseId;
    @NotNull(message = "发布指针版本不能为空")
    @Min(value = 0, message = "发布指针版本不能小于0")
    private Integer pointerVersion;
    @NotBlank(message = "回滚原因不能为空")
    @Size(max = 500, message = "回滚原因不能超过500个字符")
    private String reason;
}
