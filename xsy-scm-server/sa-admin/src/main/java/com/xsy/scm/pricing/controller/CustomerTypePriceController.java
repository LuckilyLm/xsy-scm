package com.xsy.scm.pricing.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import cn.dev33.satoken.annotation.SaCheckPermission;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import com.xsy.scm.pricing.domain.form.CustomerTypePriceAddForm;
import com.xsy.scm.pricing.domain.form.CustomerTypePriceDeleteForm;
import com.xsy.scm.pricing.domain.form.CustomerTypePriceQueryForm;
import com.xsy.scm.pricing.domain.form.CustomerTypePriceUpdateForm;
import com.xsy.scm.pricing.domain.vo.CustomerTypePriceVO;
import com.xsy.scm.pricing.permission.PricingPermission;
import com.xsy.scm.pricing.service.CustomerTypePriceQueryService;
import com.xsy.scm.pricing.service.CustomerTypePriceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/pricing/type-price")
public class CustomerTypePriceController {
    private final CustomerTypePriceService customerTypePriceService;
    private final CustomerTypePriceQueryService customerTypePriceQueryService;

    @PostMapping("/query")
    @SaCheckPermission(PricingPermission.TYPE_PRICE_QUERY)
    public ResponseDTO<PageResult<CustomerTypePriceVO>> query(@Valid @RequestBody CustomerTypePriceQueryForm form) {
        return ResponseDTO.ok(customerTypePriceQueryService.query(form));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(PricingPermission.TYPE_PRICE_QUERY)
    public ResponseDTO<CustomerTypePriceVO> detail(@PathVariable("id") Long customerTypePriceId) {
        return ResponseDTO.ok(customerTypePriceQueryService.detail(customerTypePriceId));
    }

    @PostMapping("/add")
    @SaCheckPermission(PricingPermission.TYPE_PRICE_ADD)
    @OperateLog
    public ResponseDTO<Long> add(@Valid @RequestBody CustomerTypePriceAddForm form) {
        return ResponseDTO.ok(customerTypePriceService.add(form));
    }

    @PostMapping("/update")
    @SaCheckPermission(PricingPermission.TYPE_PRICE_UPDATE)
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody CustomerTypePriceUpdateForm form) {
        customerTypePriceService.update(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/delete")
    @SaCheckPermission(PricingPermission.TYPE_PRICE_DELETE)
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody CustomerTypePriceDeleteForm form) {
        customerTypePriceService.delete(form);
        return ResponseDTO.ok();
    }
}
