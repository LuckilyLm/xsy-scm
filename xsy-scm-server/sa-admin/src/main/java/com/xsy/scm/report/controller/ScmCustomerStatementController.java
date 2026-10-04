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
import com.xsy.scm.report.constant.ScmReportPermission;
import com.xsy.scm.report.domain.form.ScmCustomerStatementForm;
import com.xsy.scm.report.domain.vo.ScmCustomerStatementVO;
import com.xsy.scm.report.service.ScmCustomerStatementService;
import com.xsy.scm.report.support.ScmReportExcel;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;

@RestController
@RequestMapping("/scm/report/customer/statement")
@RequiredArgsConstructor
public class ScmCustomerStatementController {
    private static final List<String> TITLES = List.of("对账版本", "结算方", "范围口径", "冻结时间", "期初日", "截止日", "期初净应收", "新增应收",
            "红字应收", "核销净额", "期末净应收", "本期实收净额", "期末未分配资金", "本期实退净额", "事件时间", "类型", "单号", "关联单号", "原客户", "应收变动", "收款变动",
            "核销变动", "退款变动", "滚动净应收");
    private final ScmCustomerStatementService statementService;

    @PostMapping("/freeze")
    @SaCheckPermission(value = {ScmReportPermission.CUSTOMER_STATEMENT_QUERY,
            ScmReportPermission.CUSTOMER_STATEMENT_FREEZE}, mode = SaMode.AND)
    @OperateLog
    public ResponseDTO<ScmCustomerStatementVO> freeze(@Valid @RequestBody ScmCustomerStatementForm form) {
        return ResponseDTO.ok(statementService.freeze(form));
    }

    @GetMapping("/history")
    @SaCheckPermission(ScmReportPermission.CUSTOMER_STATEMENT_QUERY)
    public ResponseDTO<List<ScmCustomerStatementVO>> history(@RequestParam Long settlementCustomerId) {
        return ResponseDTO.ok(statementService.history(settlementCustomerId));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(ScmReportPermission.CUSTOMER_STATEMENT_QUERY)
    public ResponseDTO<ScmCustomerStatementVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(statementService.detail(id));
    }

    @PostMapping("/{id}/export")
    @SaCheckPermission(value = {ScmReportPermission.CUSTOMER_STATEMENT_QUERY,
            ScmReportPermission.EXPORT}, mode = SaMode.AND)
    @OperateLog
    public void export(@PathVariable Long id, HttpServletResponse response) throws IOException {
        ScmCustomerStatementVO version = statementService.detail(id);
        List<List<Object>> rows = version.getItems().stream()
                .map(item -> ScmReportExcel.row(TITLES, version.getId(), version.getSettlementCustomerName(),
                        version.getPartialScope() ? "部分授权范围，非完整集团对账" : "完整结算方范围", version.getGeneratedAt(),
                        version.getStartDate(), version.getEndDate(), version.getOpeningReceivable(),
                        version.getReceivableIncrease(), version.getReceivableRed(), version.getWriteOffNet(),
                        version.getClosingReceivable(), version.getReceiptNet(), version.getClosingUnallocated(),
                        version.getRefundNet(), item.getEventAt(), item.getFactType(), item.getDocumentNo(),
                        item.getRelatedNo(), item.getCustomerName(), item.getReceivableDelta(), item.getReceiptDelta(),
                        item.getWriteOffDelta(), item.getRefundDelta(), item.getReceivableBalance()))
                .toList();
        if (rows.isEmpty()) {
            rows = List.of(ScmReportExcel.row(TITLES, version.getId(), version.getSettlementCustomerName(),
                    version.getPartialScope() ? "部分授权范围，非完整集团对账" : "完整结算方范围", version.getGeneratedAt(),
                    version.getStartDate(), version.getEndDate(), version.getOpeningReceivable(),
                    version.getReceivableIncrease(), version.getReceivableRed(), version.getWriteOffNet(),
                    version.getClosingReceivable(), version.getReceiptNet(), version.getClosingUnallocated(),
                    version.getRefundNet(), null, null, null, null, null, null, null, null, null, null));
        }
        ScmReportExcel.write(response, "客户对账单-" + id + ".xlsx", "冻结明细", TITLES, rows);
    }
}
