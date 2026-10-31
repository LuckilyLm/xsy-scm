package com.xsy.scm.report.controller;

import java.io.IOException;
import java.util.List;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.xsy.scm.common.export.ScmExportColumnSelection;
import com.xsy.scm.common.export.ScmExportColumnSelection.Column;
import com.xsy.scm.report.constant.ScmReportPermission;
import com.xsy.scm.report.domain.form.ScmCustomerMonthlyProfitQueryForm;
import com.xsy.scm.report.domain.vo.ScmCustomerMonthlyProfitVO;
import com.xsy.scm.report.service.ScmCustomerMonthlyProfitService;
import com.xsy.scm.report.support.ScmReportExcel;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scm/report/finance/profit/monthly")
@RequiredArgsConstructor
public class ScmCustomerMonthlyProfitController {
    private static final List<Column> COLUMNS = List.of(new Column("dimensionName", "客户名称"),
        new Column("revenueAmount", "销售收入"), new Column("salesCostAmount", "实际商品成本"), new Column("grossProfit", "销售毛利"),
        new Column("taxAmount", "税费"), new Column("serviceAmount", "服务费"), new Column("freightAmount", "运费"),
        new Column("commissionAmount", "扣点金额"), new Column("expenseAmount", "费用合计"), new Column("netProfit", "扣费后利润"),
        new Column("costMissingCount", "待确认成本数"));
    private final ScmCustomerMonthlyProfitService profitService;

    @PostMapping("/query")
    @SaCheckPermission(value = {ScmReportPermission.FINANCE_PROFIT_QUERY, ScmReportPermission.COST_QUERY},
        mode = SaMode.AND)
    public ResponseDTO<PageResult<ScmCustomerMonthlyProfitVO>> query(
        @Valid @RequestBody ScmCustomerMonthlyProfitQueryForm form) {
        return ResponseDTO.ok(profitService.query(form));
    }

    @PostMapping("/summary")
    @SaCheckPermission(value = {ScmReportPermission.FINANCE_PROFIT_QUERY, ScmReportPermission.COST_QUERY},
        mode = SaMode.AND)
    public ResponseDTO<ScmCustomerMonthlyProfitVO> summary(@Valid @RequestBody ScmCustomerMonthlyProfitQueryForm form) {
        return ResponseDTO.ok(profitService.summary(form));
    }

    @GetMapping("/export/columns")
    @SaCheckPermission(value = {ScmReportPermission.FINANCE_PROFIT_QUERY, ScmReportPermission.COST_QUERY},
        mode = SaMode.AND)
    public ResponseDTO<List<Column>> columns() {
        return ResponseDTO.ok(COLUMNS);
    }

    @PostMapping("/export")
    @SaCheckPermission(
        value = {ScmReportPermission.FINANCE_PROFIT_QUERY, ScmReportPermission.COST_QUERY, ScmReportPermission.EXPORT},
        mode = SaMode.AND)
    @OperateLog
    public void export(@Valid @RequestBody ScmCustomerMonthlyProfitQueryForm form, HttpServletResponse response)
        throws IOException {
        var selection = ScmExportColumnSelection.resolve(form.getExportColumns(), COLUMNS, true);
        var titles = COLUMNS.stream().map(Column::title).toList();
        List<List<Object>> rows = profitService.export(form).stream()
            .map(row -> ScmReportExcel.row(titles, row.getDimensionName(), row.getRevenueAmount(),
                row.getSalesCostAmount(), row.getGrossProfit(), row.getTaxAmount(), row.getServiceAmount(),
                row.getFreightAmount(), row.getCommissionAmount(), row.getExpenseAmount(), row.getNetProfit(),
                row.getCostMissingCount()))
            .toList();
        ScmExportColumnSelection.writeSelected(response, "客户月利润.xlsx", "客户月利润", selection, rows);
    }
}
