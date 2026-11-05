package com.xsy.scm.print.domain.form;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 客户绑定请求：一个客户、一种单据类型、一份模板。
 */
@Data
public class ScmPrintCustomerTemplateForm {

    @NotNull(message = "客户不能为空")
    @Positive(message = "客户编号必须大于0")
    private Long customerId;

    @NotBlank(message = "单据类型不能为空")
    @ScmEnumValue(enumClass = ScmPrintDocumentTypeEnum.class, message = "单据类型无效")
    private String documentType;

    @NotNull(message = "打印模板不能为空")
    @Positive(message = "打印模板编号必须大于0")
    private Long templateId;
}
