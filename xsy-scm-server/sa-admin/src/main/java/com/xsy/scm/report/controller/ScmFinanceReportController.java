package com.xsy.scm.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.xsy.scm.report.constant.ScmReportPermission;
import com.xsy.scm.report.domain.form.ScmFinanceReportExportQueryForm;
import com.xsy.scm.report.domain.form.ScmFinanceOverviewQueryForm;
import com.xsy.scm.report.domain.form.ScmFinanceReportQueryForm;
import com.xsy.scm.report.domain.vo.ScmFinanceOverviewVO;
import com.xsy.scm.report.domain.vo.ScmFinancePayableDetailVO;
import com.xsy.scm.report.domain.vo.ScmFinanceReceivableDetailVO;
import com.xsy.scm.report.service.ScmFinanceReportService;
import com.xsy.scm.report.support.ScmReportExcel;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/** Read-only account overview and aging-free detail endpoints. */
@RestController
@RequestMapping("/scm/report/finance")
@RequiredArgsConstructor
public class ScmFinanceReportController {

    private static final List<
            String> OVERVIEW_TITLES = List.of("统计起始日", "统计结束日", "应收发生额", "应收已核销", "期末待收", "应付发生额", "应付已核销", "期末待付");

    private static final List<String> RECEIVABLE_TITLES = List.of("应收单号", "订单号", "客户", "原应收金额", "红字金额", "净应收", "已核销金额",
            "期末待收", "超额核销待处理", "业务时点");

    private static final List<String> PAYABLE_TITLES = List.of("应付单号", "采购单号", "供应商", "原应付金额", "红字金额", "净应付", "已核销金额",
            "期末待付", "超额核销待处理", "业务时点");

    private final ScmFinanceReportService financeReportService;

    @PostMapping("/overview")
    @SaCheckPermission(ScmReportPermission.FINANCE_QUERY)
    public ResponseDTO<ScmFinanceOverviewVO> overview(@Valid @RequestBody ScmFinanceOverviewQueryForm form) {
        return ResponseDTO.ok(financeReportService.overview(form));
    }

    @PostMapping("/receivable/aging-free-detail")
    @SaCheckPermission(ScmReportPermission.FINANCE_QUERY)
    public ResponseDTO<PageResult<ScmFinanceReceivableDetailVO>> receivableDetails(
            @Valid @RequestBody ScmFinanceReportQueryForm form) {
        return ResponseDTO.ok(financeReportService.receivableDetails(form));
    }

    @PostMapping("/payable/aging-free-detail")
    @SaCheckPermission(ScmReportPermission.FINANCE_QUERY)
    public ResponseDTO<PageResult<ScmFinancePayableDetailVO>> payableDetails(
            @Valid @RequestBody ScmFinanceReportQueryForm form) {
        return ResponseDTO.ok(financeReportService.payableDetails(form));
    }

    @PostMapping("/overview/export")
    @SaCheckPermission(value = {ScmReportPermission.FINANCE_QUERY, ScmReportPermission.EXPORT}, mode = SaMode.AND)
    @OperateLog
    public void exportOverview(@Valid @RequestBody ScmFinanceOverviewQueryForm form, HttpServletResponse response)
            throws IOException {
        ScmFinanceOverviewVO result = financeReportService.overview(form);
        List<List<Object>> rows = List.of(ScmReportExcel.row(OVERVIEW_TITLES, form.getStartDate(), form.getEndDate(),
                result.getReceivableOccurredAmount(), result.getReceivableWrittenOffAmount(),
                result.getEndingReceivableAmount(), result.getPayableOccurredAmount(),
                result.getPayableWrittenOffAmount(), result.getEndingPayableAmount()));
        ScmReportExcel.write(response, "往来概览.xlsx", "往来概览", OVERVIEW_TITLES, rows);
    }

    @PostMapping("/receivable/aging-free-detail/export")
    @SaCheckPermission(value = {ScmReportPermission.FINANCE_QUERY, ScmReportPermission.EXPORT}, mode = SaMode.AND)
    @OperateLog
    public void exportReceivables(@Valid @RequestBody ScmFinanceReportExportQueryForm form,
            HttpServletResponse response) throws IOException {
        List<List<Object>> rows = financeReportService.exportReceivableDetails(form).stream()
                .map(row -> ScmReportExcel.row(RECEIVABLE_TITLES, row.getReceivableNo(), row.getOrderNo(),
                        row.getCustomerName(), row.getAmount(), row.getRedAmount(), row.getNetAmount(),
                        row.getWrittenOffAmount(), row.getOpenAmount(), row.getOverAppliedAmount(), row.getEventAt()))
                .toList();
        ScmReportExcel.write(response, "应收明细.xlsx", "应收明细", RECEIVABLE_TITLES, rows);
    }

    @PostMapping("/payable/aging-free-detail/export")
    @SaCheckPermission(value = {ScmReportPermission.FINANCE_QUERY, ScmReportPermission.EXPORT}, mode = SaMode.AND)
    @OperateLog
    public void exportPayables(@Valid @RequestBody ScmFinanceReportExportQueryForm form, HttpServletResponse response)
            throws IOException {
        List<List<Object>> rows = financeReportService.exportPayableDetails(form).stream()
                .map(row -> ScmReportExcel.row(PAYABLE_TITLES, row.getPayableNo(), row.getPurchaseOrderNo(),
                        row.getSupplierName(), row.getAmount(), row.getRedAmount(), row.getNetAmount(),
                        row.getWrittenOffAmount(), row.getOpenAmount(), row.getOverAppliedAmount(), row.getEventAt()))
                .toList();
        ScmReportExcel.write(response, "应付明细.xlsx", "应付明细", PAYABLE_TITLES, rows);
    }
}
