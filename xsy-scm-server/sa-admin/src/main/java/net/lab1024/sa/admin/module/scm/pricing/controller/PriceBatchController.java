package net.lab1024.sa.admin.module.scm.pricing.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.*;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import net.lab1024.sa.admin.module.scm.pricing.service.PriceBatchService;
import net.lab1024.sa.admin.module.scm.pricing.domain.form.PriceBatchForm;
import net.lab1024.sa.admin.module.scm.pricing.domain.vo.PriceBatchResultVO;

@RestController
@RequiredArgsConstructor
public class PriceBatchController {
    private final PriceBatchService service;

    @PostMapping("/scm/pricing/type-price/batch")
    @SaCheckPermission("scm:pricing:type-price:batch")
    @OperateLog
    public ResponseDTO<PriceBatchResultVO> batch(@Valid @RequestBody PriceBatchForm f) {
        return ResponseDTO.ok(service.submit(f));
    }
}
