package com.xsy.scm.finance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.finance.domain.form.FinanceOperationLogQueryForm;
import com.xsy.scm.finance.domain.form.FinancePayableQueryForm;
import com.xsy.scm.finance.domain.form.FinancePaymentQueryForm;
import com.xsy.scm.finance.domain.form.FinanceReceivableQueryForm;
import com.xsy.scm.finance.domain.form.FinanceReceiptQueryForm;
import com.xsy.scm.finance.domain.vo.FinanceOperationLogVO;
import com.xsy.scm.finance.domain.vo.FinancePayableDetailVO;
import com.xsy.scm.finance.domain.vo.FinancePayableVO;
import com.xsy.scm.finance.domain.vo.FinancePaymentDetailVO;
import com.xsy.scm.finance.domain.vo.FinancePaymentQueryVO;
import com.xsy.scm.finance.domain.vo.FinanceReceivableDetailVO;
import com.xsy.scm.finance.domain.vo.FinanceReceivableVO;
import com.xsy.scm.finance.domain.vo.FinanceReceiptDetailVO;
import com.xsy.scm.finance.domain.vo.FinanceReceiptQueryVO;
import com.xsy.scm.finance.permission.FinancePermission;
import com.xsy.scm.finance.service.FinanceOperationLogQueryService;
import com.xsy.scm.finance.service.FinancePayableQueryService;
import com.xsy.scm.finance.service.FinancePaymentQueryService;
import com.xsy.scm.finance.service.FinanceReceivableQueryService;
import com.xsy.scm.finance.service.FinanceReceiptQueryService;
import com.xsy.scm.finance.support.FinanceExcel;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/** Finance read-only pages, details, exports and financial operation history. */
@RestController
@RequestMapping("/scm/finance")
@Tag(name = "SCM 财务查询")
@RequiredArgsConstructor
public class FinanceReadController {

    private static final List<String> RECEIVABLE_EXPORT_TITLES = List.of("应收单号", "订单号", "客户名称", "方向", "金额", "净应收",
            "已核销", "未核销", "超额核销", "结清状态", "事件时点", "原因");

    private static final List<String> PAYABLE_EXPORT_TITLES = List.of("应付单号", "采购单号", "供应商名称", "方向", "金额", "净应付", "已核销",
            "未核销", "超额核销", "结清状态", "事件时点", "原因");

    private static final List<String> RECEIPT_EXPORT_TITLES = List.of("收款单号", "客户名称", "方向", "金额", "有效金额", "已核销", "待核销",
            "方式", "收款时点", "资金凭据号", "反向原因");

    private static final List<String> PAYMENT_EXPORT_TITLES = List.of("付款单号", "往来方类型", "往来方名称", "方向", "金额", "有效金额",
            "已核销", "待核销", "方式", "付款时点", "来源类型", "来源编号");

    private final FinanceReceivableQueryService financeReceivableQueryService;
    private final FinancePayableQueryService financePayableQueryService;
    private final FinanceReceiptQueryService financeReceiptQueryService;
    private final FinancePaymentQueryService financePaymentQueryService;
    private final FinanceOperationLogQueryService financeOperationLogQueryService;

    @PostMapping("/receivable/query")
    @SaCheckPermission(FinancePermission.RECEIVABLE_QUERY)
    public ResponseDTO<PageResult<FinanceReceivableVO>> receivableQuery(
            @Valid @RequestBody FinanceReceivableQueryForm form) {
        return ResponseDTO.ok(financeReceivableQueryService.query(form));
    }

    @GetMapping("/receivable/{id}")
    @SaCheckPermission(FinancePermission.RECEIVABLE_QUERY)
    public ResponseDTO<FinanceReceivableDetailVO> receivableDetail(@PathVariable("id") Long id) {
        return ResponseDTO.ok(financeReceivableQueryService.detail(id));
    }

    @PostMapping("/receivable/export")
    @SaCheckPermission(value = {FinancePermission.RECEIVABLE_QUERY, FinancePermission.EXPORT}, mode = SaMode.AND)
    @OperateLog
    public void exportReceivables(@Valid @RequestBody FinanceReceivableQueryForm form, HttpServletResponse response)
            throws IOException {
        List<FinanceReceivableVO> rows = financeReceivableQueryService.exportRows(form);
        List<List<Object>> data = rows.stream()
                .map(row -> FinanceExcel.row(RECEIVABLE_EXPORT_TITLES, row.getReceivableNo(), row.getOrderNo(),
                        row.getCustomerName(), row.getEntryType(), row.getAmount(), row.getNetAmount(),
                        row.getWrittenOffAmount(), row.getOpenAmount(), row.getOverAppliedAmount(),
                        row.getSettleState(), row.getEventAt(), row.getReason()))
                .toList();
        FinanceExcel.write(response, "应收明细.xlsx", "应收明细", RECEIVABLE_EXPORT_TITLES, data);
    }

    @PostMapping("/payable/query")
    @SaCheckPermission(FinancePermission.PAYABLE_QUERY)
    public ResponseDTO<PageResult<FinancePayableVO>> payableQuery(@Valid @RequestBody FinancePayableQueryForm form) {
        return ResponseDTO.ok(financePayableQueryService.query(form));
    }

    @GetMapping("/payable/{id}")
    @SaCheckPermission(FinancePermission.PAYABLE_QUERY)
    public ResponseDTO<FinancePayableDetailVO> payableDetail(@PathVariable("id") Long id) {
        return ResponseDTO.ok(financePayableQueryService.detail(id));
    }

    @PostMapping("/payable/export")
    @SaCheckPermission(value = {FinancePermission.PAYABLE_QUERY, FinancePermission.EXPORT}, mode = SaMode.AND)
    @OperateLog
    public void exportPayables(@Valid @RequestBody FinancePayableQueryForm form, HttpServletResponse response)
            throws IOException {
        List<FinancePayableVO> rows = financePayableQueryService.exportRows(form);
        List<List<Object>> data = rows.stream()
                .map(row -> FinanceExcel.row(PAYABLE_EXPORT_TITLES, row.getPayableNo(), row.getPurchaseOrderNo(),
                        row.getSupplierName(), row.getEntryType(), row.getAmount(), row.getNetAmount(),
                        row.getWrittenOffAmount(), row.getOpenAmount(), row.getOverAppliedAmount(),
                        row.getSettleState(), row.getEventAt(), row.getReason()))
                .toList();
        FinanceExcel.write(response, "应付明细.xlsx", "应付明细", PAYABLE_EXPORT_TITLES, data);
    }

    @PostMapping("/receipt/query")
    @SaCheckPermission(FinancePermission.RECEIPT_QUERY)
    public ResponseDTO<PageResult<FinanceReceiptQueryVO>> receiptQuery(
            @Valid @RequestBody FinanceReceiptQueryForm form) {
        return ResponseDTO.ok(financeReceiptQueryService.query(form));
    }

    @GetMapping("/receipt/{id}")
    @SaCheckPermission(FinancePermission.RECEIPT_QUERY)
    public ResponseDTO<FinanceReceiptDetailVO> receiptDetail(@PathVariable("id") Long id) {
        return ResponseDTO.ok(financeReceiptQueryService.detail(id));
    }

    @PostMapping("/receipt/export")
    @SaCheckPermission(value = {FinancePermission.RECEIPT_QUERY, FinancePermission.EXPORT}, mode = SaMode.AND)
    @OperateLog
    public void exportReceipts(@Valid @RequestBody FinanceReceiptQueryForm form, HttpServletResponse response)
            throws IOException {
        List<FinanceReceiptQueryVO> rows = financeReceiptQueryService.exportRows(form);
        List<List<Object>> data = rows.stream()
                .map(row -> FinanceExcel.row(RECEIPT_EXPORT_TITLES, row.getReceiptNo(), row.getCustomerName(),
                        row.getEntryType(), row.getAmount(), row.getEffectiveAmount(), row.getUsedAmount(),
                        row.getPendingWriteOffAmount(), row.getMethod(), row.getReceivedAt(),
                        row.getExternalReference(), row.getReason()))
                .toList();
        FinanceExcel.write(response, "收款明细.xlsx", "收款明细", RECEIPT_EXPORT_TITLES, data);
    }

    @PostMapping("/payment/query")
    @SaCheckPermission(FinancePermission.PAYMENT_QUERY)
    public ResponseDTO<PageResult<FinancePaymentQueryVO>> paymentQuery(
            @Valid @RequestBody FinancePaymentQueryForm form) {
        return ResponseDTO.ok(financePaymentQueryService.query(form));
    }

    @GetMapping("/payment/{id}")
    @SaCheckPermission(FinancePermission.PAYMENT_QUERY)
    public ResponseDTO<FinancePaymentDetailVO> paymentDetail(@PathVariable("id") Long id) {
        return ResponseDTO.ok(financePaymentQueryService.detail(id));
    }

    @PostMapping("/payment/export")
    @SaCheckPermission(value = {FinancePermission.PAYMENT_QUERY, FinancePermission.EXPORT}, mode = SaMode.AND)
    @OperateLog
    public void exportPayments(@Valid @RequestBody FinancePaymentQueryForm form, HttpServletResponse response)
            throws IOException {
        List<FinancePaymentQueryVO> rows = financePaymentQueryService.exportRows(form);
        List<List<Object>> data = rows.stream()
                .map(row -> FinanceExcel.row(PAYMENT_EXPORT_TITLES, row.getPaymentNo(), row.getCounterpartyType(),
                        row.getCounterpartyName(), row.getEntryType(), row.getAmount(), row.getEffectiveAmount(),
                        row.getUsedAmount(), row.getPendingWriteOffAmount(), row.getMethod(), row.getPaidAt(),
                        row.getSourceType(), row.getSourceId()))
                .toList();
        FinanceExcel.write(response, "付款明细.xlsx", "付款明细", PAYMENT_EXPORT_TITLES, data);
    }

    @GetMapping("/log/query")
    public ResponseDTO<List<FinanceOperationLogVO>> operationLogs(
            @Valid @ModelAttribute FinanceOperationLogQueryForm form) {
        return ResponseDTO.ok(financeOperationLogQueryService.query(form));
    }
}
