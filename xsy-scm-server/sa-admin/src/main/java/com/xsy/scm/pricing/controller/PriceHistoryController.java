package com.xsy.scm.pricing.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.*;
import net.lab1024.sa.base.common.domain.*;
import com.xsy.scm.pricing.service.PriceHistoryQueryService;
import com.xsy.scm.pricing.domain.form.PriceHistoryQueryForm;
import com.xsy.scm.pricing.domain.vo.PriceHistoryVO;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/pricing/history")
public class PriceHistoryController {
    private final PriceHistoryQueryService service;

    @PostMapping("/query")
    @SaCheckPermission("scm:pricing:history:query")
    public ResponseDTO<PageResult<PriceHistoryVO>> query(@Valid @RequestBody PriceHistoryQueryForm f) {
        return ResponseDTO.ok(service.query(f));
    }
}
