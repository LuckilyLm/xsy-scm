package com.xianshuyuan.scm.product.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.product.dto.ProductCategorySaveRequest;
import com.xianshuyuan.scm.product.service.ProductCategoryService;
import com.xianshuyuan.scm.product.vo.ProductCategoryTreeNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "商品分类")
@RestController
@RequestMapping("/api/product-categories")
public class ProductCategoryController {

    private final ProductCategoryService service;

    public ProductCategoryController(ProductCategoryService service) {
        this.service = service;
    }

    @Operation(summary = "查询商品分类树")
    @GetMapping("/tree")
    public ApiResponse<List<ProductCategoryTreeNode>> tree() {
        return ApiResponse.success(service.getTree());
    }

    @Operation(summary = "新增商品分类")
    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody ProductCategorySaveRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @Operation(summary = "编辑商品分类")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
        @PathVariable long id,
        @Valid @RequestBody ProductCategorySaveRequest request
    ) {
        service.update(id, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除商品分类")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
