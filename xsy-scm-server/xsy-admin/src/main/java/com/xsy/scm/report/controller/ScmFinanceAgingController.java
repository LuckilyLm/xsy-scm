package com.xsy.scm.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.xsy.scm.report.constant.ScmFinanceAgingBucketEnum;
import com.xsy.scm.report.constant.ScmReportPermission;
import com.xsy.scm.report.domain.form.ScmFinanceAgingQueryForm;
import com.xsy.scm.report.domain.vo.ScmFinanceAgingRowVO;
import com.xsy.scm.report.domain.vo.ScmFinanceAgingSummaryVO;
import com.xsy.scm.report.service.ScmFinanceAgingService;
import com.xsy.scm.common.excel.ScmExcel;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/scm/report/finance/aging")
@RequiredArgsConstructor
public class ScmFinanceAgingController {
    private static final List<String> TITLES = List.of("截止日", "账款类型", "财务单号", "来源单号", "客户或供应商", "结算方", "业务时间", "到期日",
        "逾期天数", "到期分组", "原金额", "扣减金额", "调整后金额", "已抵扣", "剩余未结金额");
    private final ScmFinanceAgingService financeAgingService;

    @PostMapping("/query")
    @SaCheckPermission(ScmReportPermission.FINANCE_AGING_QUERY)
    public ResponseDTO<PageResult<ScmFinanceAgingRowVO>> query(@Valid @RequestBody ScmFinanceAgingQueryForm form) {
        return ResponseDTO.ok(financeAgingService.query(form));
    }

    @PostMapping("/summary")
    @SaCheckPermission(ScmReportPermission.FINANCE_AGING_QUERY)
    public ResponseDTO<List<ScmFinanceAgingSummaryVO>> summary(@Valid @RequestBody ScmFinanceAgingQueryForm form) {
        return ResponseDTO.ok(financeAgingService.summary(form));
    }

    @PostMapping("/export")
    @SaCheckPermission(value = {ScmReportPermission.FINANCE_AGING_QUERY, ScmReportPermission.EXPORT}, mode = SaMode.AND)
    @OperateLog
    public void export(@Valid @RequestBody ScmFinanceAgingQueryForm form, HttpServletResponse response)
        throws IOException {
        var rows = financeAgingService.export(form).stream()
            .map(row -> ScmExcel.row(TITLES, form.getAsOfDate(), form.isReceivable() ? "应收" : "应付", row.getDocumentNo(),
                row.getSourceNo(), row.getCounterpartyName(), row.getSettlementCustomerName(), row.getEventAt(),
                row.getDueDate(), row.getOverdueDays(),
                ScmFinanceAgingBucketEnum.valueOf(row.getAgingBucket()).getDescription(), row.getAmount(),
                row.getRedAmount(), row.getNetAmount(), row.getWrittenOffAmount(), row.getOpenAmount()))
            .toList();
        ScmExcel.write(response, "账款到期情况.xlsx", "到期情况明细", TITLES, rows);
    }
}
