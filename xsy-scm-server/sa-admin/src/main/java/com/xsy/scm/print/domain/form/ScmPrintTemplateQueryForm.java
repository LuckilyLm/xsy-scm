package com.xsy.scm.print.domain.form;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 打印模板列表查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScmPrintTemplateQueryForm extends PageParam {

    /**
     * 单据类型（可选）；为空则列出全部已接入的类型。
     */
    @ScmEnumValue(enumClass = ScmPrintDocumentTypeEnum.class, message = "单据类型无效")
    private String documentType;

    /**
     * 模板编码 / 名称模糊匹配。
     */
    @Size(max = 64, message = "搜索关键词不能超过64个字符")
    private String keyword;

    /**
     * 是否启用；为空表示不限。
     */
    private Boolean enabledFlag;

    @Override
    @Min(value = 1, message = "页码必须至少为1")
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Max(value = 100, message = "每页条数不能超过100")
    @Min(value = 1, message = "每页条数必须至少为1")
    public Long getPageSize() {
        return super.getPageSize();
    }
}
