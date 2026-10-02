package com.xsy.scm.pricing.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.pricing.domain.form.PriceResolveForm;
import com.xsy.scm.pricing.domain.vo.PriceResolveResultVO;
import com.xsy.scm.pricing.permission.PricingPermission;
import com.xsy.scm.pricing.service.PriceResolver;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/pricing/resolve")
public class PriceResolveController {
    private final PriceResolver priceResolver;

    @PostMapping
    @SaCheckPermission(PricingPermission.RESOLVE_QUERY)
    public ResponseDTO<PriceResolveResultVO> resolve(@Valid @RequestBody PriceResolveForm form) {
        return ResponseDTO.ok(priceResolver.preview(form.getCustomerId(), form.getSkuIds(), form.getAt()));
    }
}
