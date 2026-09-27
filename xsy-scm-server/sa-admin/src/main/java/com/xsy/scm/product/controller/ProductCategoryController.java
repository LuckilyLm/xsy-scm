package com.xsy.scm.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.product.domain.form.ProductCategoryAddForm;
import com.xsy.scm.product.domain.form.ProductCategoryDeleteForm;
import com.xsy.scm.product.domain.form.ProductCategoryUpdateForm;
import com.xsy.scm.product.domain.vo.ProductCategoryTreeVO;
import com.xsy.scm.product.domain.vo.ProductCategoryVO;
import com.xsy.scm.product.service.ProductCategoryService;
import com.xsy.scm.product.permission.ProductPermission;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/scm/product/category")
@Tag(name = "SCM 商品分类")
@RequiredArgsConstructor
public class ProductCategoryController {
    private final ProductCategoryService productCategoryService;

    @PostMapping("/tree")
    @SaCheckPermission(ProductPermission.CATEGORY_QUERY)
    public ResponseDTO<List<ProductCategoryTreeVO>> tree() {
        return ResponseDTO.ok(productCategoryService.tree());
    }

    @GetMapping("/{categoryId}")
    @SaCheckPermission(ProductPermission.CATEGORY_QUERY)
    public ResponseDTO<ProductCategoryVO> detail(@PathVariable Long categoryId) {
        return ResponseDTO.ok(productCategoryService.detail(categoryId));
    }

    @PostMapping("/add")
    @SaCheckPermission(ProductPermission.CATEGORY_ADD)
    @OperateLog
    public ResponseDTO<Long> add(@Valid @RequestBody ProductCategoryAddForm form) {
        return ResponseDTO.ok(productCategoryService.add(form));
    }

    @PostMapping("/update")
    @SaCheckPermission(ProductPermission.CATEGORY_UPDATE)
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody ProductCategoryUpdateForm form) {
        productCategoryService.update(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/delete")
    @SaCheckPermission(ProductPermission.CATEGORY_DELETE)
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody ProductCategoryDeleteForm form) {
        productCategoryService.delete(form);
        return ResponseDTO.ok();
    }
}
