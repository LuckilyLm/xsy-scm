package com.xsy.scm.product.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.*;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import com.xsy.scm.product.domain.form.ProductSkuOptionQueryForm;
import com.xsy.scm.product.domain.vo.ProductSkuOptionListVO;
import com.xsy.scm.product.service.ProductSkuOptionQueryService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/product/sku")
public class ProductSkuController {
    private final ProductSkuOptionQueryService service;

    @PostMapping("/option-list")
    @SaCheckPermission("scm:product:sku:query")
    public ResponseDTO<ProductSkuOptionListVO> options(@Valid @RequestBody ProductSkuOptionQueryForm form) {
        return ResponseDTO.ok(service.optionList(form));
    }
}
