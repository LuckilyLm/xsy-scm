package com.xsy.scm.print.domain.form;

import lombok.Data;

/**
 * 正式打印入参。
 *
 * <p>
 * 只有「用哪份模板」一个选项：业务单据由路径给出，模板为空时用该类型的默认模板。
 * 刻意<b>没有</b> version 之类的乐观锁字段 —— 打印不改业务单据，它只在打印域追加一条冻结记录，
 * 让打印去校验业务版本会把「打印」变成一次伪写操作。
 */
@Data
public class ScmPrintActionForm {

    /**
     * 模板 id；为空使用该单据类型的默认模板。
     */
    private Long templateId;
}
