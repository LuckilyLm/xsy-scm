package net.lab1024.sa.admin.module.scm.pricing.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.*;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import net.lab1024.sa.admin.module.scm.pricing.domain.form.*;
import net.lab1024.sa.admin.module.scm.pricing.domain.vo.*;
import net.lab1024.sa.admin.module.scm.pricing.service.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/pricing/agreement-price")
public class AgreementPriceController {
    private final AgreementPriceService service;
    private final AgreementPriceQueryService queries;

    @PostMapping("/query")
    @SaCheckPermission("scm:pricing:agreement:query")
    public ResponseDTO<PageResult<AgreementPriceVO>> query(@Valid @RequestBody AgreementPriceQueryForm f) {
        return ResponseDTO.ok(queries.query(f));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission("scm:pricing:agreement:query")
    public ResponseDTO<AgreementPriceVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(queries.detail(id));
    }

    @PostMapping("/add")
    @SaCheckPermission("scm:pricing:agreement:add")
    @OperateLog
    public ResponseDTO<Long> add(@Valid @RequestBody AgreementPriceAddForm f) {
        return ResponseDTO.ok(service.add(f));
    }

    @PostMapping("/update")
    @SaCheckPermission("scm:pricing:agreement:update")
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody AgreementPriceUpdateForm f) {
        service.update(f);
        return ResponseDTO.ok();
    }

    @PostMapping("/delete")
    @SaCheckPermission("scm:pricing:agreement:delete")
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody AgreementPriceDeleteForm f) {
        service.delete(f);
        return ResponseDTO.ok();
    }
}
