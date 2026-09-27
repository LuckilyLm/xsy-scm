package com.xsy.scm.pricing.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import cn.dev33.satoken.annotation.SaCheckPermission;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import com.xsy.scm.pricing.domain.form.AgreementPriceAddForm;
import com.xsy.scm.pricing.domain.form.AgreementPriceDeleteForm;
import com.xsy.scm.pricing.domain.form.AgreementPriceQueryForm;
import com.xsy.scm.pricing.domain.form.AgreementPriceUpdateForm;
import com.xsy.scm.pricing.domain.vo.AgreementPriceVO;
import com.xsy.scm.pricing.permission.PricingPermission;
import com.xsy.scm.pricing.service.AgreementPriceQueryService;
import com.xsy.scm.pricing.service.AgreementPriceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/pricing/agreement-price")
public class AgreementPriceController {
    private final AgreementPriceService agreementPriceService;
    private final AgreementPriceQueryService agreementPriceQueryService;

    @PostMapping("/query")
    @SaCheckPermission(PricingPermission.AGREEMENT_QUERY)
    public ResponseDTO<PageResult<AgreementPriceVO>> query(@Valid @RequestBody AgreementPriceQueryForm form) {
        return ResponseDTO.ok(agreementPriceQueryService.query(form));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(PricingPermission.AGREEMENT_QUERY)
    public ResponseDTO<AgreementPriceVO> detail(@PathVariable("id") Long agreementPriceId) {
        return ResponseDTO.ok(agreementPriceQueryService.detail(agreementPriceId));
    }

    @PostMapping("/add")
    @SaCheckPermission(PricingPermission.AGREEMENT_ADD)
    @OperateLog
    public ResponseDTO<Long> add(@Valid @RequestBody AgreementPriceAddForm form) {
        return ResponseDTO.ok(agreementPriceService.add(form));
    }

    @PostMapping("/update")
    @SaCheckPermission(PricingPermission.AGREEMENT_UPDATE)
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody AgreementPriceUpdateForm form) {
        agreementPriceService.update(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/delete")
    @SaCheckPermission(PricingPermission.AGREEMENT_DELETE)
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody AgreementPriceDeleteForm form) {
        agreementPriceService.delete(form);
        return ResponseDTO.ok();
    }
}
