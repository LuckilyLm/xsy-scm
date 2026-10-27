package com.xsy.scm.pricing.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import cn.dev33.satoken.annotation.SaCheckPermission;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import com.xsy.scm.pricing.domain.form.CustomerGroupPriceAddForm;
import com.xsy.scm.pricing.domain.form.CustomerGroupPriceDeleteForm;
import com.xsy.scm.pricing.domain.form.CustomerGroupPriceQueryForm;
import com.xsy.scm.pricing.domain.form.CustomerGroupPriceUpdateForm;
import com.xsy.scm.pricing.domain.vo.CustomerGroupPriceVO;
import com.xsy.scm.pricing.permission.PricingPermission;
import com.xsy.scm.pricing.service.CustomerGroupPriceQueryService;
import com.xsy.scm.pricing.service.CustomerGroupPriceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/pricing/group-price")
public class CustomerGroupPriceController {
    private final CustomerGroupPriceService customerGroupPriceService;
    private final CustomerGroupPriceQueryService customerGroupPriceQueryService;

    @PostMapping("/query")
    @SaCheckPermission(PricingPermission.GROUP_PRICE_QUERY)
    public ResponseDTO<PageResult<CustomerGroupPriceVO>> query(@Valid @RequestBody CustomerGroupPriceQueryForm form) {
        return ResponseDTO.ok(customerGroupPriceQueryService.query(form));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(PricingPermission.GROUP_PRICE_QUERY)
    public ResponseDTO<CustomerGroupPriceVO> detail(@PathVariable("id") Long groupPriceId) {
        return ResponseDTO.ok(customerGroupPriceQueryService.detail(groupPriceId));
    }

    @PostMapping("/add")
    @SaCheckPermission(PricingPermission.GROUP_PRICE_ADD)
    @OperateLog
    public ResponseDTO<Long> add(@Valid @RequestBody CustomerGroupPriceAddForm form) {
        return ResponseDTO.ok(customerGroupPriceService.add(form));
    }

    @PostMapping("/update")
    @SaCheckPermission(PricingPermission.GROUP_PRICE_UPDATE)
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody CustomerGroupPriceUpdateForm form) {
        customerGroupPriceService.update(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/delete")
    @SaCheckPermission(PricingPermission.GROUP_PRICE_DELETE)
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody CustomerGroupPriceDeleteForm form) {
        customerGroupPriceService.delete(form);
        return ResponseDTO.ok();
    }
}
