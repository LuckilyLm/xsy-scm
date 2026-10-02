package com.xsy.scm.print.constant;

/**
 * 打印字段目录里的一项（单据类型白名单的最小单元）。
 *
 * <p>
 * 模板只存字段 <b>key</b>，标签与对齐方式由服务端按单据类型给出 —— 标签是展示口径，
 * 让模板自定义标签等于允许把「计划数量」标成「已收数量」这类误导性输出。
 *
 * @param key
 *            稳定字段键；模板里存的就是它
 * @param label
 *            打印表头文案
 * @param money
 *            是否为金额字段；为 {@code true} 时渲染阶段按调用者的金额权限二次剔除
 * @param numeric
 *            是否右对齐（数量与金额）
 */
public record ScmPrintField(String key, String label, boolean money, boolean numeric) {
}
