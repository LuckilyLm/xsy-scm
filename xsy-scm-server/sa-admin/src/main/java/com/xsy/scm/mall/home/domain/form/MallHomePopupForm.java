package com.xsy.scm.mall.home.domain.form;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.mall.home.constant.MallHomePopupDisplayRule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.Data;

@Data
public class MallHomePopupForm {
    @NotNull(message = "配置版本不能为空")
    @Min(value = 0, message = "配置版本不能小于0")
    private Integer version;
    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;
    @NotNull(message = "展示规则不能为空")
    @ScmEnumValue(enumClass = MallHomePopupDisplayRule.class, message = "展示规则无效")
    private String displayRule;
    private OffsetDateTime startAt;
    private OffsetDateTime endAt;
    @Size(max = 5, message = "首页弹窗最多5张图片")
    private List<@Valid MallHomePopupItemForm> items;
}
