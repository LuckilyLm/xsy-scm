package com.xsy.scm.finance.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.finance.permission.FinancePermission;
import com.xsy.scm.finance.domain.form.FinanceCodCollectionForm;
import com.xsy.scm.finance.domain.form.FinanceReceiptAddForm;
import com.xsy.scm.finance.domain.form.FinanceReceiptReverseForm;
import com.xsy.scm.finance.domain.vo.FinanceCodCollectionPreviewVO;
import com.xsy.scm.finance.domain.vo.FinanceReceiptVO;
import com.xsy.scm.finance.service.FinanceReceiptService;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 财务收款登记入口。
 *
 * <p>
 * 收款查询、导出与反向动作由独立端点提供，不复用本登记端点的权限。
 *
 * <p>
 * 收款是资金动作，重复请求会重复入账，所以必须带 {@code Idempotency-Key}：同键同内容重放首次结果，不产生第二张收款单。
 */
@RestController
@RequestMapping("/scm/finance/receipt")
@Tag(name = "SCM 财务收款")
@RequiredArgsConstructor
public class FinanceReceiptController {

    private final FinanceReceiptService financeReceiptService;

    @PostMapping("/add")
    @SaCheckPermission(FinancePermission.RECEIPT_ADD)
    @OperateLog
    public ResponseDTO<FinanceReceiptVO> add(@Valid @RequestBody FinanceReceiptAddForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(financeReceiptService.add(form, idempotencyKey));
    }

    @PostMapping("/reverse")
    @SaCheckPermission(FinancePermission.RECEIPT_REVERSE)
    @OperateLog
    public ResponseDTO<FinanceReceiptVO> reverse(@Valid @RequestBody FinanceReceiptReverseForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(financeReceiptService.reverse(form, idempotencyKey));
    }

    @GetMapping("/cod-preview/{orderId}")
    @SaCheckPermission(FinancePermission.RECEIPT_ADD)
    public ResponseDTO<FinanceCodCollectionPreviewVO> previewCod(@PathVariable Long orderId) {
        return ResponseDTO.ok(financeReceiptService.previewCod(orderId));
    }

    /**
     * 货到付款收款确认：对已签收的 COD 订单登记实收并核销其应收。
     *
     * <p>
     * 沿用「登记收款」权限。必须带 {@code Idempotency-Key}；一张订单可以分笔登记，每笔以请求来源唯一键防重，合计不得超过应收剩余。
     */
    @PostMapping("/cod-collection")
    @SaCheckPermission(FinancePermission.RECEIPT_ADD)
    @OperateLog
    public ResponseDTO<FinanceReceiptVO> collectCod(@Valid @RequestBody FinanceCodCollectionForm form,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseDTO.ok(financeReceiptService.collectCod(form, idempotencyKey));
    }
}
