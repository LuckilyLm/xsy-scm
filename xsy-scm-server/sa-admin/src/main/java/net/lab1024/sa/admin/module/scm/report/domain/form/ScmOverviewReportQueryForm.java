package net.lab1024.sa.admin.module.scm.report.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmReportDateForm;

/**
 * 经营概览筛选条件。指标卡、趋势与每日统计共用同一条件，保证「图上那个点」与
 * 「表里那一行」以及「卡片那个数」是同一个口径。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScmOverviewReportQueryForm extends ScmReportDateForm {

    private Long warehouseId;

    private Long customerId;

    private Long sellerId;

    private String orderSource;

    private Long categoryId;

    private String keyword;
}
