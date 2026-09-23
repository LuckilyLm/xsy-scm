package net.lab1024.sa.admin.module.scm.report.domain.form;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportDateFilter;

/**
 * 非分页报表查询表单的公共日期字段（指标卡、趋势、每日统计、价格波动）。
 *
 * <p>分页表单必须继承 {@code PageParam}，无法继承本类，因此它们各自实现
 * {@link ScmReportDateFilter}；日期语义由该接口统一约束。
 */
@Data
public abstract class ScmReportDateForm implements ScmReportDateFilter {

    /** 起始业务日（闭区间），Asia/Shanghai 日界。 */
    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;

    /** 结束业务日（闭区间）。 */
    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;
}
