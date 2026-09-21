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
@RequestMapping("/scm/pricing/type-price")
public class CustomerTypePriceController {
    private final CustomerTypePriceService service;
    private final CustomerTypePriceQueryService queries;

    @PostMapping("/query")
    @SaCheckPermission("scm:pricing:type-price:query")
    public ResponseDTO<PageResult<CustomerTypePriceVO>> query(@Valid @RequestBody CustomerTypePriceQueryForm f) {
        return ResponseDTO.ok(queries.query(f));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission("scm:pricing:type-price:query")
    public ResponseDTO<CustomerTypePriceVO> detail(@PathVariable Long id) {
        return ResponseDTO.ok(queries.detail(id));
    }

    @PostMapping("/add")
    @SaCheckPermission("scm:pricing:type-price:add")
    @OperateLog
    public ResponseDTO<Long> add(@Valid @RequestBody CustomerTypePriceAddForm f) {
        return ResponseDTO.ok(service.add(f));
    }

    @PostMapping("/update")
    @SaCheckPermission("scm:pricing:type-price:update")
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody CustomerTypePriceUpdateForm f) {
        service.update(f);
        return ResponseDTO.ok();
    }

    @PostMapping("/delete")
    @SaCheckPermission("scm:pricing:type-price:delete")
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody CustomerTypePriceDeleteForm f) {
        service.delete(f);
        return ResponseDTO.ok();
    }
}
