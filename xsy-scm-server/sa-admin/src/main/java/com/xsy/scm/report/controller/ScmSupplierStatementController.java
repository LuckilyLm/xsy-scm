package com.xsy.scm.report.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.xsy.scm.finance.permission.FinancePermission;
import com.xsy.scm.report.constant.ScmReportPermission;
import com.xsy.scm.report.domain.form.ScmSupplierStatementForm;
import com.xsy.scm.report.domain.vo.ScmSupplierStatementVO;
import com.xsy.scm.report.service.ScmSupplierStatementService;
import com.xsy.scm.report.support.ScmReportExcel;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;

@RestController
@RequestMapping("/scm/report/supplier/statement")
@RequiredArgsConstructor
public class ScmSupplierStatementController {
    private static final List<String> TITLES = List.of("对账版本", "供应商", "范围口径", "冻结时间", "起始日", "截止日", "期初净应付", "新增应付",
            "红字应付", "核销净额", "期末净应付", "期初未分配付款", "本期全供应商付款净额", "期末未分配付款", "事件时间", "类型", "单号", "关联单号", "应付变动", "付款变动",
            "核销变动", "滚动净应付");
    private final ScmSupplierStatementService statementService;

    @PostMapping("/freeze")
    @SaCheckPermission(value = {ScmReportPermission.SUPPLIER_STATEMENT_QUERY,
            ScmReportPermission.SUPPLIER_STATEMENT_FREEZE, FinancePermission.PAYABLE_QUERY,
            FinancePermission.PAYMENT_QUERY}, mode = SaMode.AND)
    @OperateLog
    public ResponseDTO<ScmSupplierStatementVO> freeze(@Valid @RequestBody ScmSupplierStatementForm form) {
        return ResponseDTO.ok(statementService.freeze(form));
    }

    @GetMapping("/history")
    @SaCheckPermission(value = {ScmReportPermission.SUPPLIER_STATEMENT_QUERY, FinancePermission.PAYABLE_QUERY,
            FinancePermission.PAYMENT_QUERY}, mode = SaMode.AND)
    public ResponseDTO<List<ScmSupplierStatementVO>> history(@RequestParam Long supplierId) {
        return ResponseDTO.ok(statementService.history(supplierId));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(value = {ScmReportPermission.SUPPLIER_STATEMENT_QUERY, FinancePermission.PAYABLE_QUERY,
            FinancePermission.PAYMENT_QUERY}, mode = SaMode.AND)
    public ResponseDTO<ScmSupplierStatementVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(statementService.detail(id));
    }

    @PostMapping("/{id}/export")
    @SaCheckPermission(value = {ScmReportPermission.SUPPLIER_STATEMENT_QUERY, ScmReportPermission.EXPORT,
            FinancePermission.PAYABLE_QUERY, FinancePermission.PAYMENT_QUERY}, mode = SaMode.AND)
    @OperateLog
    public void export(@PathVariable Long id, HttpServletResponse response) throws IOException {
        ScmSupplierStatementVO version = statementService.detail(id);
        String coverage = version.getPartialScope() ? "部分应付范围；付款为全供应商，金额不可勾稽" : "完整供应商范围";
        List<List<Object>> rows = version.getItems().stream()
                .map(item -> ScmReportExcel.row(TITLES, version.getId(), version.getSupplierName(), coverage,
                        version.getGeneratedAt(), version.getStartDate(), version.getEndDate(),
                        version.getOpeningPayable(), version.getPayableIncrease(), version.getPayableRed(),
                        version.getWriteOffNet(), version.getClosingPayable(), version.getOpeningUnallocated(),
                        version.getPaymentNet(), version.getClosingUnallocated(), item.getEventAt(), item.getFactType(),
                        item.getDocumentNo(), item.getRelatedNo(), item.getPayableDelta(), item.getPaymentDelta(),
                        item.getWriteOffDelta(), item.getPayableBalance()))
                .toList();
        if (rows.isEmpty()) {
            rows = List.of(ScmReportExcel.row(TITLES, version.getId(), version.getSupplierName(), coverage,
                    version.getGeneratedAt(), version.getStartDate(), version.getEndDate(), version.getOpeningPayable(),
                    version.getPayableIncrease(), version.getPayableRed(), version.getWriteOffNet(),
                    version.getClosingPayable(), version.getOpeningUnallocated(), version.getPaymentNet(),
                    version.getClosingUnallocated(), null, null, null, null, null, null, null, null));
        }
        ScmReportExcel.write(response, "供应商对账单-" + id + ".xlsx", "冻结明细", TITLES, rows);
    }
}
