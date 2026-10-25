package com.xsy.scm.mall.home.domain.form;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.mall.home.constant.MallHomeLinkType;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MallHomePopupItemForm {
    @Size(max = 300, message = "图片标识不能超过300个字符")
    private String imageFileKey;
    @ScmEnumValue(enumClass = MallHomeLinkType.class, message = "跳转类型无效")
    private String linkType;
    @Size(max = 500, message = "跳转目标不能超过500个字符")
    private String linkTarget;
    @Size(max = 80, message = "标题不能超过80个字符")
    private String title;
}
