package net.lab1024.sa.admin.module.scm.report.support;

import java.time.LocalDate;

/**
 * 报表查询表单的日期契约。分页表单继承 {@code PageParam}、非分页表单继承 {@link ScmReportDateForm}，
 * Java 单继承决定了两者无法共用父类，因此用接口把「日期区间」这一必需约束收敛到同一处，
 * 让 {@link ScmReportTimeRangeResolver} 只认一个入口。
 *
 * <p>字段语义是<b>用户输入的闭区间日期</b>（Asia/Shanghai 日界），不是瞬间；
 * 半开区间的换算只允许发生在 {@link ScmReportTimeRangeResolver} 内，
 * 各报表不得自行拼日界，否则同一张页面会出现两种口径。
 */
public interface ScmReportDateFilter {

    LocalDate getStartDate();

    LocalDate getEndDate();
}
