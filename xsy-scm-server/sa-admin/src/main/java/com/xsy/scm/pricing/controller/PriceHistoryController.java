package com.xsy.scm.pricing.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import cn.dev33.satoken.annotation.SaCheckPermission;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import com.xsy.scm.pricing.domain.form.PriceHistoryQueryForm;
import com.xsy.scm.pricing.domain.vo.PriceHistoryVO;
import com.xsy.scm.pricing.permission.PricingPermission;
import com.xsy.scm.pricing.service.PriceHistoryQueryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/pricing/history")
public class PriceHistoryController {
    private final PriceHistoryQueryService priceHistoryQueryService;

    @PostMapping("/query")
    @SaCheckPermission(PricingPermission.HISTORY_QUERY)
    public ResponseDTO<PageResult<PriceHistoryVO>> query(@Valid @RequestBody PriceHistoryQueryForm form) {
        return ResponseDTO.ok(priceHistoryQueryService.query(form));
    }
}
