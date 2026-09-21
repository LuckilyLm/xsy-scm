package net.lab1024.sa.admin.module.scm.pricing.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.*;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.admin.module.scm.pricing.service.PriceResolver;
import net.lab1024.sa.admin.module.scm.pricing.domain.form.PriceResolveForm;
import net.lab1024.sa.admin.module.scm.pricing.domain.vo.PriceResolveResultVO;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/pricing/resolve")
public class PriceResolveController {
    private final PriceResolver service;

    @PostMapping
    @SaCheckPermission("scm:pricing:resolve:query")
    public ResponseDTO<PriceResolveResultVO> resolve(@Valid @RequestBody PriceResolveForm f) {
        return ResponseDTO.ok(service.preview(f.getCustomerId(), f.getSkuIds(), f.getAt()));
    }
}
