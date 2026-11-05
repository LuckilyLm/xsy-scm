package com.xsy.scm.mall.decoration.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class MallStoreSaveForm {
    @NotNull(message = "版本不能为空")
    @Min(value = 0, message = "版本不能小于0")
    private Integer version;
    @NotBlank(message = "商城名称不能为空")
    @Size(max = 80, message = "商城名称不能超过80个字符")
    private String storeName;
    @Size(max = 500, message = "商城链接不能超过500个字符")
    @Pattern(regexp = "^$|^https://[0-9A-Za-z.-]+(:\\d+)?(/.*)?$", message = "商城链接必须是以 https:// 开头的地址")
    private String storeLink;
}
