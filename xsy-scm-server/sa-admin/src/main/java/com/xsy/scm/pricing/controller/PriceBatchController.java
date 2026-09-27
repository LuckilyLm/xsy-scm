package com.xsy.scm.pricing.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import com.xsy.scm.pricing.service.PriceBatchService;
import com.xsy.scm.pricing.domain.form.PriceBatchForm;
import com.xsy.scm.pricing.domain.vo.PriceBatchResultVO;
import com.xsy.scm.pricing.permission.PricingPermission;

@RestController
@RequiredArgsConstructor
public class PriceBatchController {
    private final PriceBatchService priceBatchService;

    @PostMapping("/scm/pricing/type-price/batch")
    @SaCheckPermission(PricingPermission.TYPE_PRICE_BATCH)
    @OperateLog
    public ResponseDTO<
            PriceBatchResultVO> batch(@Valid @RequestBody PriceBatchForm form) {
        return ResponseDTO.ok(priceBatchService.submit(form));
    }
}
