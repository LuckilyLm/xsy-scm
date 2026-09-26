package com.xsy.scm.customer.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.*;
import net.lab1024.sa.base.common.domain.*;
import com.xsy.scm.customer.service.CustomerSkuVisibilityService;
import com.xsy.scm.customer.domain.form.CustomerVisibilityQueryForm;
import com.xsy.scm.customer.domain.vo.CustomerSkuVisibilityReverseVO;

@RestController
@RequiredArgsConstructor
public class CustomerVisibilityController {
    private final CustomerSkuVisibilityService service;

    @PostMapping("/scm/customer/visibility/reverse/query")
    @SaCheckPermission("scm:customer:visibility:query")
    public ResponseDTO<PageResult<CustomerSkuVisibilityReverseVO>> reverse(@Valid @RequestBody CustomerVisibilityQueryForm f) {
        return ResponseDTO.ok(service.reverse(f));
    }
}
