package com.xsy.scm.print.constant;

/**
 * 打印字段目录里的一项（单据类型白名单的最小单元）。
 *
 * <p>
 * 模板只存字段键，标签与对齐方式由服务端按单据类型给出。标签是展示口径， 让模板自定义标签等于允许把「计划数量」标成「已收数量」这类误导性输出。
 */
public record ScmPrintField(String key, String label, boolean money, boolean numeric) {
}
