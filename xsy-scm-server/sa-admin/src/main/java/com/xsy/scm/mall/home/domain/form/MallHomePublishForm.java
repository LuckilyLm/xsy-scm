package com.xsy.scm.mall.home.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MallHomePublishForm {
    @NotNull(message = "草稿版本不能为空")
    @Min(value = 0, message = "草稿版本不能小于0")
    private Integer draftVersion;
    @NotNull(message = "发布指针版本不能为空")
    @Min(value = 0, message = "发布指针版本不能小于0")
    private Integer pointerVersion;
}
