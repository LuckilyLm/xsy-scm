package com.xsy.scm.product.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import com.xsy.scm.product.domain.form.ProductSkuOptionQueryForm;
import com.xsy.scm.product.domain.vo.ProductSkuOptionListVO;
import com.xsy.scm.product.service.ProductSkuOptionQueryService;
import com.xsy.scm.product.permission.ProductPermission;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/product/sku")
public class ProductSkuController {
    private final ProductSkuOptionQueryService productSkuOptionQueryService;

    @PostMapping("/option-list")
    @SaCheckPermission(ProductPermission.SKU_QUERY)
    public ResponseDTO<ProductSkuOptionListVO> options(@Valid @RequestBody ProductSkuOptionQueryForm form) {
        return ResponseDTO.ok(productSkuOptionQueryService.optionList(form));
    }
}
