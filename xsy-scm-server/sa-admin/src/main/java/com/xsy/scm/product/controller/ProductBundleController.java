package com.xsy.scm.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.product.domain.vo.ProductBundleComponentVO;
import com.xsy.scm.product.domain.form.ProductBundleReplaceForm;
import com.xsy.scm.product.permission.ProductPermission;
import com.xsy.scm.product.service.ProductBundleService;
import jakarta.validation.Valid;

import java.util.List;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/product/bundle")
public class ProductBundleController {
    private final ProductBundleService bundleService;

    @GetMapping("/{bundleSkuId}/components")
    @SaCheckPermission(ProductPermission.QUERY)
    public ResponseDTO<List<ProductBundleComponentVO>> components(@PathVariable Long bundleSkuId) {
        return ResponseDTO.ok(bundleService.components(bundleSkuId));
    }

    @PostMapping("/replace")
    @SaCheckPermission(ProductPermission.UPDATE)
    @OperateLog
    public ResponseDTO<String> replace(@Valid @RequestBody ProductBundleReplaceForm form) {
        bundleService.replace(form);
        return ResponseDTO.ok();
    }
}
