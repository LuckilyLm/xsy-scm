package com.xsy.scm.product.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import com.xsy.scm.product.domain.form.ProductSkuOptionQueryForm;
import com.xsy.scm.product.domain.vo.ProductSkuOptionListVO;
import com.xsy.scm.product.service.ProductSkuOptionQueryService;
import com.xsy.scm.product.permission.ProductPermission;
import com.xsy.scm.product.domain.form.ProductSkuStockConversionDeleteForm;
import com.xsy.scm.product.domain.form.ProductSkuStockConversionForm;
import com.xsy.scm.product.domain.vo.ProductSkuStockConversionVO;
import com.xsy.scm.product.service.ProductSkuStockConversionService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/product/sku")
public class ProductSkuController {
    private final ProductSkuOptionQueryService productSkuOptionQueryService;
    private final ProductSkuStockConversionService conversionService;

    @PostMapping("/option-list")
    @SaCheckPermission(ProductPermission.SKU_QUERY)
    public ResponseDTO<ProductSkuOptionListVO> options(@Valid @RequestBody ProductSkuOptionQueryForm form) {
        return ResponseDTO.ok(productSkuOptionQueryService.optionList(form));
    }

    @GetMapping("/stock-conversions")
    @SaCheckPermission(ProductPermission.QUERY)
    public ResponseDTO<List<ProductSkuStockConversionVO>> stockConversions(@RequestParam Long skuId) {
        return ResponseDTO.ok(conversionService.list(skuId));
    }

    @PostMapping("/stock-conversions")
    @SaCheckPermission(ProductPermission.UPDATE)
    public ResponseDTO<String> saveStockConversion(@Valid @RequestBody ProductSkuStockConversionForm form) {
        conversionService.save(form);
        return ResponseDTO.ok("OK");
    }

    @PostMapping("/stock-conversions/delete")
    @SaCheckPermission(ProductPermission.UPDATE)
    public ResponseDTO<String> removeStockConversion(@Valid @RequestBody ProductSkuStockConversionDeleteForm form) {
        conversionService.remove(form);
        return ResponseDTO.ok("OK");
    }
}
