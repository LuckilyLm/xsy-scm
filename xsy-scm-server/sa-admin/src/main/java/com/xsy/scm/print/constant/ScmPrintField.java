package com.xsy.scm.print.constant;

/**
 * 打印字段目录里的一项，也是单据类型允许字段的最小单元。
 *
 * <p>
 * 模板只存字段键，标签与对齐方式由服务端按单据类型给出；否则可以把「计划数量」标成「已收数量」。
 */
public record ScmPrintField(String key, String label, boolean money, boolean numeric) {
}
