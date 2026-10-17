package com.xsy.scm.mall.home.domain.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class MallHomeDraftSaveForm {
    @NotNull(message = "草稿版本不能为空")
    @Min(value = 0, message = "草稿版本不能小于0")
    private Integer version;
    @NotNull(message = "首页组件不能为空")
    @Size(max = 30, message = "首页最多配置30个组件")
    private List<@Valid MallHomeBlockForm> blocks;
}
