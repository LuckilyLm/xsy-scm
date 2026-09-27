package com.xsy.scm.product.domain.form;

import jakarta.validation.constraints.Size;
import lombok.Data;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.validation.ScmEnumValue;

/**
 * 辅助资料列表查询条件；字典规模有限，不启用分页。
 */
@Data
public class ProductAssistantQueryForm {
    @Size(max = 64, message = "查询关键词不能超过64个字符")
    private String keyword;
    @ScmEnumValue(enumClass = ScmEnableStatusEnum.class, message = "主档状态无效")
    private String status;
}
