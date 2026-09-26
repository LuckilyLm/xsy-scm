package com.xsy.scm.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.product.domain.form.ProductImageCenterForms;
import com.xsy.scm.product.domain.vo.ProductImageCenterVO;
import com.xsy.scm.product.service.ProductImageCenterService;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/scm/product/image")
@Tag(name = "SCM 商品图片中心")
@RequiredArgsConstructor
public class ProductImageCenterController {
    private final ProductImageCenterService productImageCenterService;

    @GetMapping("/query")
    @SaCheckPermission("scm:product:image:query")
    public ResponseDTO<ProductImageCenterVO> query(@RequestParam @NotNull @Min(1) Long spuId) {
        return ResponseDTO.ok(productImageCenterService.query(spuId));
    }

    @PostMapping("/batch-bind")
    @SaCheckPermission("scm:product:image:batch")
    @OperateLog
    public ResponseDTO<String> batchBind(@Valid @RequestBody ProductImageCenterForms.BatchBindForm form) {
        productImageCenterService.batchBind(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/batch-remove")
    @SaCheckPermission("scm:product:image:batch")
    @OperateLog
    public ResponseDTO<String> batchRemove(@Valid @RequestBody ProductImageCenterForms.BatchRemoveForm form) {
        productImageCenterService.batchRemove(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/set-primary")
    @SaCheckPermission("scm:product:image:batch")
    @OperateLog
    public ResponseDTO<String> setPrimary(@Valid @RequestBody ProductImageCenterForms.SetPrimaryForm form) {
        productImageCenterService.setPrimary(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/reorder")
    @SaCheckPermission("scm:product:image:batch")
    @OperateLog
    public ResponseDTO<String> reorder(@Valid @RequestBody ProductImageCenterForms.ReorderForm form) {
        productImageCenterService.reorder(form);
        return ResponseDTO.ok();
    }
}
