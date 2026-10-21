package com.xsy.scm.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.purchase.domain.form.PurchaseInquiryForm;
import com.xsy.scm.purchase.domain.vo.PurchaseInquiryVO;
import com.xsy.scm.purchase.permission.PurchasePermission;
import com.xsy.scm.purchase.service.PurchaseInquiryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/purchase/inquiry")
public class PurchaseInquiryController {
    private final PurchaseInquiryService purchaseInquiryService;

    @PostMapping("/batch")
    @SaCheckPermission(PurchasePermission.INQUIRY_QUERY)
    public ResponseDTO<PurchaseInquiryVO.BatchPage> byBatch(@Valid @RequestBody PurchaseInquiryForm.Batch form) {
        return ResponseDTO.ok(purchaseInquiryService.byBatch(form.getBatchId(), form.getPage()));
    }

    @PostMapping("/batch/candidates")
    @SaCheckPermission(PurchasePermission.INQUIRY_QUERY)
    public ResponseDTO<List<PurchaseInquiryVO.Candidate>> candidates(
            @Valid @RequestBody PurchaseInquiryForm.Batch form) {
        return ResponseDTO.ok(purchaseInquiryService.candidates(form.getBatchId()));
    }

    @PostMapping("/history")
    @SaCheckPermission(PurchasePermission.INQUIRY_QUERY)
    public ResponseDTO<PurchaseInquiryVO.HistoryPage> history(@Valid @RequestBody PurchaseInquiryForm.History form) {
        return ResponseDTO.ok(purchaseInquiryService.history(form.getInquiryId(), form.getBeforeId()));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(PurchasePermission.INQUIRY_QUERY)
    public ResponseDTO<PurchaseInquiryVO.Detail> detail(@PathVariable @Positive Long id) {
        return ResponseDTO.ok(purchaseInquiryService.detail(id));
    }

    @PostMapping("/create")
    @SaCheckPermission(PurchasePermission.INQUIRY_CREATE)
    @OperateLog
    public ResponseDTO<PurchaseInquiryVO.Detail> create(@Valid @RequestBody PurchaseInquiryForm.Create form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(purchaseInquiryService.create(form, key));
    }

    @PostMapping("/quote")
    @SaCheckPermission(PurchasePermission.INQUIRY_QUOTE)
    @OperateLog
    public ResponseDTO<PurchaseInquiryVO.Detail> quote(@Valid @RequestBody PurchaseInquiryForm.Quote form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(purchaseInquiryService.quote(form, key));
    }

    @PostMapping("/accept")
    @SaCheckPermission(PurchasePermission.INQUIRY_ACCEPT)
    @OperateLog
    public ResponseDTO<PurchaseInquiryVO.Detail> accept(@Valid @RequestBody PurchaseInquiryForm.Accept form,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ResponseDTO.ok(purchaseInquiryService.accept(form, key));
    }
}
