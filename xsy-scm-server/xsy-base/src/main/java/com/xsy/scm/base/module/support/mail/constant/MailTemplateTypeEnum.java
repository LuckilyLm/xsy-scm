package com.xsy.scm.base.module.support.mail.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import com.xsy.scm.base.common.enumeration.BaseEnum;

/**
 * 邮件模板类型
 *
 */

@Getter
@AllArgsConstructor
public enum MailTemplateTypeEnum implements BaseEnum {

    STRING("string", "字符串替代器"),

    FREEMARKER("freemarker", "freemarker模板引擎");

    private String value;

    private String desc;

}
