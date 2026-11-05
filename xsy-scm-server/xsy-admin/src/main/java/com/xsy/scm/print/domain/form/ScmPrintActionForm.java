package com.xsy.scm.print.domain.form;

import lombok.Data;

/**
 * 正式打印入参，只有「用哪份模板」一个选项；模板为空时用该类型的默认模板。
 *
 * <p>
 * 没有 version 之类的乐观锁字段：打印不改业务单据，只在打印域追加一条记录。
 */
@Data
public class ScmPrintActionForm {

    /** 模板 id；为空使用该单据类型的默认模板。 */
    private Long templateId;
}
