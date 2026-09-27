package com.xsy.scm.customer.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import cn.dev33.satoken.annotation.SaCheckPermission;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import com.xsy.scm.customer.domain.form.CustomerVisibilityQueryForm;
import com.xsy.scm.customer.domain.vo.CustomerSkuVisibilityReverseVO;
import com.xsy.scm.customer.permission.CustomerPermission;
import com.xsy.scm.customer.service.CustomerSkuVisibilityService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CustomerVisibilityController {
    private final CustomerSkuVisibilityService customerSkuVisibilityService;

    @PostMapping("/scm/customer/visibility/reverse/query")
    @SaCheckPermission(CustomerPermission.VISIBILITY_QUERY)
    public ResponseDTO<PageResult<CustomerSkuVisibilityReverseVO>> reverse(
            @Valid @RequestBody CustomerVisibilityQueryForm queryForm) {
        return ResponseDTO.ok(customerSkuVisibilityService.reverse(queryForm));
    }
}
